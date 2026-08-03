package com.hengji.app

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.hengji.app.application.PickedImportDocument
import com.hengji.app.application.CapturedDocumentCsvEncoder
import com.hengji.app.application.UserDocumentPolicy
import com.hengji.app.application.UserDocumentPurpose
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.application.UserLocalCapturePicker
import com.hengji.app.importflow.ImportDocumentFormat
import com.hengji.app.importflow.LocalCaptureMode
import com.hengji.connectors.LocalDocumentKind
import com.hengji.connectors.ReviewedDocumentBatchParseResult
import com.hengji.connectors.ReviewedDocumentBatchParser
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidImportDocumentPicker(
    private val activity: ComponentActivity,
) : UserImportDocumentPicker, UserLocalCapturePicker {
    private var pending: PendingPick? = null
    private var pendingCapture: PendingCapture? = null
    private var sharedDocument: SharedDocument? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pending ?: return@registerForActivityResult
        pending = null
        if (uri == null) {
            request.continuation.resume(null)
            return@registerForActivityResult
        }
        activity.lifecycleScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    readDocument(uri, request.format, request.purpose)
                }
                request.continuation.resume(document)
            } catch (error: CancellationException) {
                request.continuation.cancel(error)
                throw error
            } catch (error: Exception) {
                request.continuation.resumeWith(Result.failure(error))
            }
        }
    }
    private val captureLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingCapture ?: return@registerForActivityResult
        pendingCapture = null
        if (uri == null) {
            request.continuation.resume(null)
            return@registerForActivityResult
        }
        activity.lifecycleScope.launch {
            try {
                request.continuation.resume(readCapture(uri, activity.contentResolver.getType(uri), request.mode))
            } catch (error: CancellationException) {
                request.continuation.cancel(error)
                throw error
            } catch (error: Exception) {
                request.continuation.resumeWith(Result.failure(error))
            }
        }
    }

    override val isAvailable: Boolean = true

    override suspend fun pick(
        format: ImportDocumentFormat,
        purpose: UserDocumentPurpose,
    ): PickedImportDocument? =
        suspendCancellableCoroutine { continuation ->
            check(pending == null) { "已有文件选择请求正在进行" }
            pending = PendingPick(format, purpose, continuation)
            continuation.invokeOnCancellation {
                if (pending?.continuation === continuation) pending = null
            }
            launcher.launch(
                when (format) {
                    ImportDocumentFormat.Csv -> arrayOf("text/csv", "text/comma-separated-values", "application/csv")
                    ImportDocumentFormat.Json -> arrayOf("application/json", "text/json")
                },
            )
        }

    override suspend fun pick(mode: LocalCaptureMode): PickedImportDocument? {
        if (mode == LocalCaptureMode.SharedDocument) {
            val offered = sharedDocument ?: error("系统分享内容已过期，请重新分享")
            sharedDocument = null
            return readCapture(offered.uri, offered.mimeType, mode)
        }
        return suspendCancellableCoroutine { continuation ->
            check(pendingCapture == null) { "已有截图识别请求正在进行" }
            pendingCapture = PendingCapture(mode, continuation)
            continuation.invokeOnCancellation {
                if (pendingCapture?.continuation === continuation) pendingCapture = null
            }
            captureLauncher.launch(
                when (mode) {
                    LocalCaptureMode.LongScreenshot -> arrayOf("image/*")
                    LocalCaptureMode.ImageOrPdf -> arrayOf("image/*", "application/pdf")
                    LocalCaptureMode.SharedDocument -> error("Handled before launcher")
                },
            )
        }
    }

    fun offerSharedDocument(uri: Uri, mimeType: String): Boolean {
        if (mimeType != "application/pdf" && !mimeType.startsWith("image/")) return false
        sharedDocument = SharedDocument(uri, mimeType)
        return true
    }

    private suspend fun readCapture(
        uri: Uri,
        rawMimeType: String?,
        mode: LocalCaptureMode,
    ): PickedImportDocument = withContext(Dispatchers.Default) {
        val mimeType = resolveCaptureMimeType(uri, rawMimeType, mode)
        require(mimeType == "application/pdf" || mimeType.startsWith("image/")) {
            "只支持图片或 PDF"
        }
        val text = AndroidOnDeviceDocumentTextExtractor(activity.applicationContext).extract(uri, mimeType)
        val kind = if (mimeType == "application/pdf") LocalDocumentKind.PDF else LocalDocumentKind.IMAGE
        val parsed = ReviewedDocumentBatchParser().parse(
            text = text,
            sourceKind = kind,
            asOf = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            allowDefaultDate = mode != LocalCaptureMode.LongScreenshot,
        )
        val batch = when (parsed) {
            is ReviewedDocumentBatchParseResult.Batch -> parsed.value
            is ReviewedDocumentBatchParseResult.Rejected -> throw IllegalArgumentException(parsed.reason)
        }
        CapturedDocumentCsvEncoder.encode(batch, mode)
    }

    private fun resolveCaptureMimeType(
        uri: Uri,
        rawMimeType: String?,
        mode: LocalCaptureMode,
    ): String {
        if (rawMimeType == "application/pdf" || rawMimeType?.startsWith("image/") == true) {
            return rawMimeType
        }
        require(mode != LocalCaptureMode.SharedDocument) { "系统分享内容没有可验证的图片或 PDF 类型" }
        if (mode == LocalCaptureMode.LongScreenshot) return "image/*"
        val header = activity.contentResolver.openInputStream(uri)?.use { input ->
            ByteArray(PDF_MAGIC.size).also { bytes -> input.read(bytes) }
        } ?: error("无法读取所选文件")
        return if (header.contentEquals(PDF_MAGIC)) "application/pdf" else "image/*"
    }

    private fun readDocument(
        uri: Uri,
        format: ImportDocumentFormat,
        purpose: UserDocumentPurpose,
    ): PickedImportDocument {
        val resolver = activity.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "消费记录.${format.name.lowercase()}"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            readBounded(input, UserDocumentPolicy.maximumBytes(purpose))
        }
            ?: error("无法读取所选文件")
        return UserDocumentPolicy.decode(
            displayName = displayName,
            bytes = bytes,
            format = format,
            purpose = purpose,
        )
    }

    private fun readBounded(input: java.io.InputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes) { "文件超过安全上限" }
            output.write(buffer, 0, read)
        }
        require(total > 0) { "文件为空" }
        return output.toByteArray()
    }

    private data class PendingPick(
        val format: ImportDocumentFormat,
        val purpose: UserDocumentPurpose,
        val continuation: CancellableContinuation<PickedImportDocument?>,
    )

    private data class PendingCapture(
        val mode: LocalCaptureMode,
        val continuation: CancellableContinuation<PickedImportDocument?>,
    )

    private data class SharedDocument(
        val uri: Uri,
        val mimeType: String,
    )

    private companion object {
        val PDF_MAGIC = "%PDF-".encodeToByteArray()
    }
}

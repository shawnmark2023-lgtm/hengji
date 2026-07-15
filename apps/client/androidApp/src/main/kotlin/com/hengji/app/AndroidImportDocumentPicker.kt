package com.hengji.app

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.hengji.app.application.PickedImportDocument
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.importflow.ImportDocumentFormat
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidImportDocumentPicker(
    private val activity: ComponentActivity,
) : UserImportDocumentPicker {
    private var pending: PendingPick? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pending ?: return@registerForActivityResult
        pending = null
        if (uri == null) {
            request.continuation.resume(null)
            return@registerForActivityResult
        }
        activity.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { readDocument(uri, request.format) }
            }
            result.fold(
                onSuccess = { request.continuation.resume(it) },
                onFailure = { request.continuation.resumeWith(Result.failure(it)) },
            )
        }
    }

    override suspend fun pick(format: ImportDocumentFormat): PickedImportDocument? =
        suspendCancellableCoroutine { continuation ->
            check(pending == null) { "已有文件选择请求正在进行" }
            pending = PendingPick(format, continuation)
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

    private fun readDocument(uri: Uri, format: ImportDocumentFormat): PickedImportDocument {
        val resolver = activity.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "消费记录.${format.name.lowercase()}"
        val bytes = resolver.openInputStream(uri)?.use(::readBounded)
            ?: error("无法读取所选文件")
        return PickedImportDocument(
            displayName = displayName,
            content = bytes.decodeToString(throwOnInvalidSequence = true),
            format = format,
        )
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_IMPORT_BYTES) { "文件超过 5 MiB 安全上限" }
            output.write(buffer, 0, read)
        }
        require(total > 0) { "文件为空" }
        return output.toByteArray()
    }

    private data class PendingPick(
        val format: ImportDocumentFormat,
        val continuation: CancellableContinuation<PickedImportDocument?>,
    )

    private companion object {
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    }
}

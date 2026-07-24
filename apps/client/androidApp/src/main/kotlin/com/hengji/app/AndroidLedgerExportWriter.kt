package com.hengji.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.hengji.app.application.LedgerExportPolicy
import com.hengji.app.application.LedgerExportWriter
import com.hengji.app.application.PreparedLedgerExport
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidLedgerExportWriter(
    private val activity: ComponentActivity,
) : LedgerExportWriter {
    private var pending: PendingExport? = null
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val request = pending ?: return@registerForActivityResult
        pending = null
        if (uri == null) {
            request.continuation.resume(null)
            return@registerForActivityResult
        }
        activity.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { write(uri, request.export.bytes) }
                uri.toString()
            }
            result.fold(
                onSuccess = { request.continuation.resume(it) },
                onFailure = { request.continuation.resumeWith(Result.failure(it)) },
            )
        }
    }

    override suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String? {
        val export = LedgerExportPolicy.prepare(suggestedFileName, utf8Content, mediaType)
        return suspendCancellableCoroutine { continuation ->
            check(pending == null) { "已有导出请求正在进行" }
            pending = PendingExport(export, continuation)
            continuation.invokeOnCancellation {
                if (pending?.continuation === continuation) pending = null
            }
            launcher.launch(export.fileName)
        }
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        activity.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
        } ?: error("无法写入所选位置")
    }

    private data class PendingExport(
        val export: PreparedLedgerExport,
        val continuation: CancellableContinuation<String?>,
    )
}

package com.hengji.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.hengji.app.application.LedgerExportWriter
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
                withContext(Dispatchers.IO) { write(uri, request.content) }
                uri.toString()
            }
            result.fold(
                onSuccess = { request.continuation.resume(it) },
                onFailure = { request.continuation.resumeWith(Result.failure(it)) },
            )
        }
    }

    override suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String? =
        suspendCancellableCoroutine { continuation ->
            check(pending == null) { "已有导出请求正在进行" }
            pending = PendingExport(utf8Content, continuation)
            continuation.invokeOnCancellation {
                if (pending?.continuation === continuation) pending = null
            }
            launcher.launch(suggestedFileName)
        }

    private fun write(uri: Uri, content: String) {
        activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(content)
        } ?: error("无法写入所选位置")
    }

    private data class PendingExport(
        val content: String,
        val continuation: CancellableContinuation<String?>,
    )
}

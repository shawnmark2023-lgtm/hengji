package com.hengji.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.hengji.connectors.MAX_EXTRACTED_TEXT_CHARS
import com.hengji.connectors.MAX_LOCAL_DOCUMENT_BYTES
import com.hengji.connectors.MAX_LOCAL_DOCUMENT_PAGES
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidOnDeviceDocumentTextExtractor(
    private val context: Context,
) {
    suspend fun extract(uri: Uri, mimeType: String): String {
        validateSize(uri)
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build(),
        )
        return try {
            val text = if (mimeType == "application/pdf") {
                extractPdf(uri) { image -> recognizer.process(image).await().text }
            } else {
                recognizer.process(InputImage.fromFilePath(context, uri)).await().text
            }
            require(text.length <= MAX_EXTRACTED_TEXT_CHARS) {
                "OCR text exceeds the local review limit"
            }
            text
        } finally {
            recognizer.close()
        }
    }

    private fun validateSize(uri: Uri) {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length >= 0) {
                require(descriptor.length <= MAX_LOCAL_DOCUMENT_BYTES) {
                    "Document exceeds the 20 MiB local OCR limit"
                }
            }
        } ?: error("Cannot open the shared document")
    }

    private suspend fun extractPdf(
        uri: Uri,
        recognize: suspend (InputImage) -> String,
    ): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open the shared PDF")
        return descriptor.use {
            PdfRenderer(it).use { renderer ->
                require(renderer.pageCount in 1..MAX_LOCAL_DOCUMENT_PAGES) {
                    "PDF must contain between 1 and $MAX_LOCAL_DOCUMENT_PAGES pages"
                }
                buildString {
                    repeat(renderer.pageCount) { pageIndex ->
                        renderer.openPage(pageIndex).use { page ->
                            val scale = (MAX_RENDER_WIDTH.toFloat() / page.width).coerceAtMost(1f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            require(width.toLong() * height <= MAX_RENDER_PIXELS)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                if (isNotEmpty()) append('\n')
                                append(recognize(InputImage.fromBitmap(bitmap, 0)))
                                require(length <= MAX_EXTRACTED_TEXT_CHARS) {
                                    "OCR text exceeds the local review limit"
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_RENDER_WIDTH = 1_600
        const val MAX_RENDER_PIXELS = 4_000_000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}

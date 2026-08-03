package com.hengji.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
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
                extractImage(uri) { image -> recognizer.process(image).await().text }
            }
            require(text.length <= MAX_EXTRACTED_TEXT_CHARS) {
                "识别文字超过 100,000 字符，请拆分文件后重试"
            }
            text
        } finally {
            recognizer.close()
        }
    }

    private suspend fun extractImage(
        uri: Uri,
        recognize: suspend (InputImage) -> String,
    ): String {
        val bounds = imageBounds(uri)
        require(bounds.width.toLong() * bounds.height <= MAX_SOURCE_IMAGE_PIXELS) {
            "图片总像素超过本机识别上限，请缩小后重试"
        }
        require(bounds.width <= MAX_SOURCE_DIMENSION && bounds.height <= MAX_SOURCE_DIMENSION) {
            "图片单边尺寸超过 100,000 像素，请拆分后重试"
        }
        if (bounds.width.toLong() * bounds.height <= MAX_DIRECT_IMAGE_PIXELS) {
            return recognize(InputImage.fromFilePath(context, uri))
        }

        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法打开所选图片")
        return descriptor.use {
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(it.fileDescriptor, false)
            try {
                var sampleSize = 1
                while (bounds.width / sampleSize > MAX_RENDER_WIDTH) sampleSize *= 2
                val sourceTileHeight = TILE_OUTPUT_HEIGHT * sampleSize
                val tileCount = (bounds.height + sourceTileHeight - 1) / sourceTileHeight
                require(tileCount in 1..MAX_IMAGE_TILES) { "长截图超过 50 个识别分片，请拆成两张后重试" }
                buildString {
                    repeat(tileCount) { tileIndex ->
                        val top = tileIndex * sourceTileHeight
                        val bottom = (top + sourceTileHeight).coerceAtMost(bounds.height)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        @Suppress("DEPRECATION")
                        val bitmap = decoder.decodeRegion(Rect(0, top, bounds.width, bottom), options)
                            ?: error("长截图有一个分片无法解码，请换一张图片后重试")
                        try {
                            if (isNotEmpty()) append('\n')
                            append(recognize(InputImage.fromBitmap(bitmap, 0)))
                            require(length <= MAX_EXTRACTED_TEXT_CHARS) {
                                "识别文字超过 100,000 字符，请拆分文件后重试"
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            } finally {
                @Suppress("DEPRECATION")
                decoder.recycle()
            }
        }
    }

    private fun imageBounds(uri: Uri): ImageBounds {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法打开所选图片")
        return descriptor.use {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "图片格式不受支持或文件已经损坏" }
            ImageBounds(options.outWidth, options.outHeight)
        }
    }

    private fun validateSize(uri: Uri) {
        val resolver = context.contentResolver
        val descriptorLength = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: error("无法打开所选文件")
        val reportedLength = if (descriptorLength >= 0) {
            descriptorLength
        } else {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }
        if (reportedLength != null && reportedLength >= 0) {
            require(reportedLength in 1..MAX_LOCAL_DOCUMENT_BYTES) {
                "文件不能为空且不能超过 20 MiB"
            }
            return
        }

        val input = resolver.openInputStream(uri) ?: error("无法读取所选文件")
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_LOCAL_DOCUMENT_BYTES) {
                    "文件超过 20 MiB 本机识别上限"
                }
            }
            require(total > 0) { "文件为空" }
        }
    }

    private suspend fun extractPdf(
        uri: Uri,
        recognize: suspend (InputImage) -> String,
    ): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法打开所选 PDF")
        return descriptor.use {
            PdfRenderer(it).use { renderer ->
                require(renderer.pageCount in 1..MAX_LOCAL_DOCUMENT_PAGES) {
                    "PDF 页数必须在 1 到 $MAX_LOCAL_DOCUMENT_PAGES 页之间"
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
                                    "识别文字超过 100,000 字符，请拆分文件后重试"
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
        const val MAX_DIRECT_IMAGE_PIXELS = 8_000_000L
        const val MAX_SOURCE_IMAGE_PIXELS = 100_000_000L
        const val MAX_SOURCE_DIMENSION = 100_000
        const val TILE_OUTPUT_HEIGHT = 2_000
        const val MAX_IMAGE_TILES = 50
    }

    private data class ImageBounds(val width: Int, val height: Int)
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

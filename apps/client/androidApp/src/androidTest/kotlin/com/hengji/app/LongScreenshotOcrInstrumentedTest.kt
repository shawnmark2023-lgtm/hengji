package com.hengji.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongScreenshotOcrInstrumentedTest {
    @Test
    fun tallImageIsDecodedInSectionsAndRecognizedOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(context.cacheDir, "long-screenshot-ocr-test.png")
        val bitmap = Bitmap.createBitmap(1_000, 9_000, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 72f
                typeface = Typeface.DEFAULT_BOLD
            }
            listOf(
                180f to "2026-08-03",
                330f to "Coffee Shop",
                480f to "CNY 35.50",
                7_350f to "2026-08-03",
                7_500f to "Taxi Ride",
                7_650f to "CNY 28.00",
            ).forEach { (y, line) -> canvas.drawText(line, 80f, y, paint) }
            FileOutputStream(screenshot).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        try {
            val text = AndroidOnDeviceDocumentTextExtractor(context).extract(
                Uri.fromFile(screenshot),
                "image/png",
            )
            assertTrue(text.contains("Coffee", ignoreCase = true))
            assertTrue(text.contains("Taxi", ignoreCase = true))
            assertTrue(text.contains("35.50"))
            assertTrue(text.contains("28.00"))
        } finally {
            screenshot.delete()
        }
    }
}

package com.hengji.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P1PlatformSurfaceInstrumentedTest {
    @Test
    fun manifestExposesReviewedCaptureSurfacesWithoutSmsReadPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS,
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertFalse(Manifest.permission.READ_SMS in permissions)
        assertFalse(Manifest.permission.RECEIVE_SMS in permissions)

        val quickEntry = Intent(MainActivity.ACTION_QUICK_ENTRY).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(quickEntry, 0).isNotEmpty())

        listOf("text/plain", "image/png", "application/pdf").forEach { mimeType ->
            val share = Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .setPackage(context.packageName)
            assertTrue(context.packageManager.queryIntentActivities(share, 0).isNotEmpty())
        }

        val widget = ComponentName(context, HengjiQuickEntryWidget::class.java)
        assertTrue(
            AppWidgetManager.getInstance(context)
                .installedProviders
                .any { it.provider == widget },
        )
    }
}

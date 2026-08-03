package com.hengji.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hengji.data.openAndroidProtectedLedger
import com.hengji.insights.PriceTargetAnalyzer
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class PriceTargetNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = try {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val snapshot = openAndroidProtectedLedger(applicationContext).repository.snapshot()
        val liveQuotes = snapshot.marketQuotes.filter { it.isLiveSource }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val insights = PriceTargetAnalyzer.analyze(snapshot.assets, liveQuotes, today)
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val newInsight = insights.firstOrNull {
            !preferences.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().contains(it.deduplicationKey)
        } ?: return Result.success()

        createChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hengji)
            .setContentTitle("恒迹出售目标提醒")
            .setContentText("有一项资产的授权行情已达到你设置的出售目标。")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("有一项资产的授权行情已达到你设置的出售目标。打开恒迹后再查看本机明细。"),
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        preferences.edit()
            .putStringSet(
                KEY_NOTIFIED,
                preferences.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty() + newInsight.deduplicationKey,
            )
            .apply()
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK = "hengji-price-target-local-evaluation"
        private const val PREFERENCES = "hengji-price-notifications"
        private const val KEY_NOTIFIED = "notified-deduplication-keys"
        private const val CHANNEL_ID = "price-targets"
        private const val NOTIFICATION_ID = 0x484A

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceTargetNotificationWorker>(
                6,
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "出售目标提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "仅提醒授权实时行情达到本机出售目标，不展示账单原文。"
                },
            )
        }
    }
}

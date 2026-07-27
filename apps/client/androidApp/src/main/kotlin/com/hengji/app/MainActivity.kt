package com.hengji.app

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hengji.data.ProtectedLedgerOpenOutcome
import com.hengji.data.ProtectedLedgerOpenResult
import com.hengji.data.openAndroidProtectedLedger
import com.hengji.app.application.PriceNotificationControl
import com.hengji.app.application.ModelExplanationControl
import com.hengji.app.application.QuickEntryRequest
import com.hengji.connectors.LocalDocumentKind
import com.hengji.connectors.ReviewedDocumentParseResult
import com.hengji.connectors.ReviewedDocumentTextParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var storageState by mutableStateOf<AndroidStorageState>(AndroidStorageState.Loading)
    private var quickEntryRequest by mutableStateOf<QuickEntryRequest?>(null)
    private var quickEntrySequence = 0L
    private var notificationStatus by mutableStateOf("系统通知默认关闭；仅在你主动允许后安排本地评估。")
    private var notificationCanRequest by mutableStateOf(true)
    private var modelExplanationEnabled by mutableStateOf(false)
    private var modelExplanationStatus by mutableStateOf("默认关闭；当前未配置经过隐私评审的模型提供方，网络外发为 0。")
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enablePriceNotifications()
        } else {
            notificationStatus = "系统通知未获授权；后台提醒保持关闭，可稍后在系统设置中更改。"
            notificationCanRequest = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val importPicker = AndroidImportDocumentPicker(this)
        val exportWriter = AndroidLedgerExportWriter(this)
        handleLaunchIntent(intent)
        refreshNotificationStatus()
        refreshModelExplanationStatus()
        enableEdgeToEdge()
        setContent {
            when (val state = storageState) {
                AndroidStorageState.Loading -> AndroidStorageStartupStatus(
                    loading = true,
                    message = "正在安全打开本机账本…",
                )

                is AndroidStorageState.Opened -> HengjiApp(
                    repository = state.result.repository,
                    userImportDocumentPicker = importPicker,
                    ledgerExportWriter = exportWriter,
                    seedDemoData = state.result.outcome == ProtectedLedgerOpenOutcome.CREATED_EMPTY,
                    quickEntryRequest = quickEntryRequest,
                    priceNotificationControl = PriceNotificationControl(
                        status = notificationStatus,
                        canRequest = notificationCanRequest,
                        request = ::requestPriceNotificationPermission,
                        disable = ::disablePriceNotifications,
                    ),
                    modelExplanationControl = ModelExplanationControl(
                        enabled = modelExplanationEnabled,
                        status = modelExplanationStatus,
                        onEnabledChange = ::updateModelExplanationConsent,
                    ),
                )

                AndroidStorageState.Failed -> AndroidStorageStartupStatus(
                    loading = false,
                    message = "密钥、密文或旧账本迁移不可用。恒迹没有创建明文替代账本，原有文件保持不变。",
                    onRetry = ::openProtectedLedger,
                    onExit = ::finish,
                )
            }
        }
        openProtectedLedger()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun openProtectedLedger() {
        storageState = AndroidStorageState.Loading
        lifecycleScope.launch {
            storageState = try {
                val opened = withContext(Dispatchers.Default) {
                    openAndroidProtectedLedger(applicationContext)
                }
                AndroidStorageState.Opened(opened)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.e("HengjiStorage", "Protected ledger open failed", error)
                }
                AndroidStorageState.Failed
            }
        }
    }

    private fun handleLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_ENTRY -> publishQuickEntry()
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    publishSharedText(sharedText)
                } else if (intent.type == "application/pdf" || intent.type?.startsWith("image/") == true) {
                    val documentKind = if (intent.type == "application/pdf") {
                        LocalDocumentKind.PDF
                    } else {
                        LocalDocumentKind.IMAGE
                    }
                    val uri = @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                    if (uri == null) {
                        publishRejectedDocument("分享内容没有可读取的文件")
                    } else {
                        lifecycleScope.launch {
                            val text = runCatching {
                                withContext(Dispatchers.IO) {
                                    AndroidOnDeviceDocumentTextExtractor(applicationContext)
                                        .extract(uri, requireNotNull(intent.type))
                                }
                            }.getOrElse { error ->
                                publishRejectedDocument(error.message ?: "本机 OCR/PDF 解析失败")
                                return@launch
                            }
                            publishDocumentText(text, documentKind)
                        }
                    }
                }
            }
        }
    }

    private fun publishQuickEntry() {
        quickEntrySequence += 1
        quickEntryRequest = QuickEntryRequest(
            sequence = quickEntrySequence,
            sourceDisclosure = "快捷入口只打开确认页；保存前可修改或取消。",
        )
    }

    private fun publishSharedText(text: String) {
        quickEntrySequence += 1
        val parsed = runCatching {
            ReviewedDocumentTextParser().parse(
                text = text,
                sourceKind = LocalDocumentKind.USER_SHARED_FINANCIAL_SMS,
            )
        }.getOrElse {
            ReviewedDocumentParseResult.Rejected("分享内容超过本地解析上限或格式无效")
        }
        quickEntryRequest = when (parsed) {
            is ReviewedDocumentParseResult.Candidate -> QuickEntryRequest(
                sequence = quickEntrySequence,
                merchant = parsed.value.merchant.value.orEmpty(),
                amountMinor = parsed.value.amountMinor.value,
                categoryLabel = parsed.value.categoryHint.value ?: "其他",
                sourceDisclosure = "来自系统分享的本机解析候选；请逐项核对，保存前不会写入账本。",
            )

            is ReviewedDocumentParseResult.Rejected -> QuickEntryRequest(
                sequence = quickEntrySequence,
                sourceDisclosure = "${parsed.reason}。未保留分享原文，你仍可手动记账。",
            )
        }
    }

    private fun publishDocumentText(
        text: String,
        documentKind: LocalDocumentKind,
    ) {
        quickEntrySequence += 1
        val parsed = runCatching {
            ReviewedDocumentTextParser().parse(
                text = text,
                sourceKind = documentKind,
            )
        }.getOrElse {
            ReviewedDocumentParseResult.Rejected("OCR 文本超过本地解析上限或格式无效")
        }
        quickEntryRequest = when (parsed) {
            is ReviewedDocumentParseResult.Candidate -> QuickEntryRequest(
                sequence = quickEntrySequence,
                merchant = parsed.value.merchant.value.orEmpty(),
                amountMinor = parsed.value.amountMinor.value,
                categoryLabel = parsed.value.categoryHint.value ?: "其他",
                sourceDisclosure = "图片/PDF 已在设备上离线识别；请核对所有候选字段，原文件不会写入账本。",
            )

            is ReviewedDocumentParseResult.Rejected -> QuickEntryRequest(
                sequence = quickEntrySequence,
                sourceDisclosure = "${parsed.reason}。原文件未写入账本，你仍可手动记账。",
            )
        }
    }

    private fun publishRejectedDocument(reason: String) {
        quickEntrySequence += 1
        quickEntryRequest = QuickEntryRequest(
            sequence = quickEntrySequence,
            sourceDisclosure = "$reason。原文件未写入账本，你仍可手动记账。",
        )
    }

    private fun requestPriceNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enablePriceNotifications()
        }
    }

    private fun enablePriceNotifications() {
        getSharedPreferences(NOTIFICATION_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_OPT_IN, true)
            .apply()
        PriceTargetNotificationWorker.schedule(applicationContext)
        notificationStatus = "系统通知已允许；每 6 小时以内由系统择机进行一次本地评估。"
        notificationCanRequest = false
    }

    private fun disablePriceNotifications() {
        getSharedPreferences(NOTIFICATION_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_OPT_IN, false)
            .apply()
        PriceTargetNotificationWorker.cancel(applicationContext)
        notificationStatus = "目标提醒已关闭；后台评估已取消。系统通知权限可在设备设置中单独撤回。"
        notificationCanRequest = true
    }

    private fun refreshNotificationStatus() {
        val optedIn = getSharedPreferences(NOTIFICATION_PREFERENCES, MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATION_OPT_IN, false)
        val permitted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (optedIn && permitted) {
            PriceTargetNotificationWorker.schedule(applicationContext)
            notificationStatus = "系统通知已允许；每 6 小时以内由系统择机进行一次本地评估。"
            notificationCanRequest = false
        }
    }

    private fun updateModelExplanationConsent(enabled: Boolean) {
        modelExplanationEnabled = enabled
        getSharedPreferences(MODEL_EXPLANATION_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MODEL_EXPLANATION_CONSENT, enabled)
            .apply()
        modelExplanationStatus = if (enabled) {
            "已记录本机同意；因未配置隐私评审提供方，仍使用离线规则解释且网络外发为 0。"
        } else {
            "同意已撤回并立即停用；离线规则解释保持可用。"
        }
    }

    private fun refreshModelExplanationStatus() {
        modelExplanationEnabled = getSharedPreferences(MODEL_EXPLANATION_PREFERENCES, MODE_PRIVATE)
            .getBoolean(KEY_MODEL_EXPLANATION_CONSENT, false)
        modelExplanationStatus = if (modelExplanationEnabled) {
            "已记录本机同意；因未配置隐私评审提供方，仍使用离线规则解释且网络外发为 0。"
        } else {
            "默认关闭；当前未配置经过隐私评审的模型提供方，网络外发为 0。"
        }
    }

    companion object {
        const val ACTION_QUICK_ENTRY = "com.hengji.app.action.QUICK_ENTRY"
        private const val NOTIFICATION_PREFERENCES = "hengji-price-notification-consent"
        private const val KEY_NOTIFICATION_OPT_IN = "enabled"
        private const val MODEL_EXPLANATION_PREFERENCES = "hengji-model-explanation-consent"
        private const val KEY_MODEL_EXPLANATION_CONSENT = "enabled"
    }
}

private sealed interface AndroidStorageState {
    data object Loading : AndroidStorageState

    data class Opened(val result: ProtectedLedgerOpenResult) : AndroidStorageState

    data object Failed : AndroidStorageState
}

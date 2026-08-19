package com.hengji.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hengji.app.application.AppLedgerGateway
import com.hengji.app.application.AppAppearanceMode
import com.hengji.app.application.AssetSaleTargetEditor
import com.hengji.app.application.DemoDataSeedPolicy
import com.hengji.app.application.InsightFeedbackReducer
import com.hengji.app.application.LocalImportFlowPort
import com.hengji.app.application.LocalCaptureLaunchRequest
import com.hengji.app.application.ManualMarketQuoteFactory
import com.hengji.app.application.PersistentAppLedgerGateway
import com.hengji.app.application.PendingTransactionUndo
import com.hengji.app.application.PreviewLedgerGateway
import com.hengji.app.application.TransactionDeletionCoordinator
import com.hengji.app.application.TransactionDeletionResult
import com.hengji.app.application.rememberImportFlowHost
import com.hengji.app.application.UnavailableUserImportDocumentPicker
import com.hengji.app.application.UnavailableUserLocalCapturePicker
import com.hengji.app.application.UserDocumentPurpose
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.application.UserLocalCapturePicker
import com.hengji.app.application.LedgerExportWriter
import com.hengji.app.application.PreviewOnlyLedgerExportWriter
import com.hengji.app.application.QuickEntryRequest
import com.hengji.app.application.PriceNotificationControl
import com.hengji.app.application.shouldReduceMotion
import com.hengji.app.application.shouldDisplay
import com.hengji.app.importflow.ImportWizard
import com.hengji.app.importflow.ImportDocumentFormat
import com.hengji.app.importflow.ImportFlowEvent
import com.hengji.app.importflow.ImportSource
import com.hengji.app.importflow.LocalCaptureMode
import com.hengji.app.model.DomainDemoData
import com.hengji.app.model.currencyDisplayPrefix
import com.hengji.app.model.formatMoney
import com.hengji.app.model.parseMoneyToMinor
import com.hengji.app.model.withModelResult
import com.hengji.app.model.toGeneratedModelResult
import com.hengji.app.model.toPersonalAnalysisRecord
import com.hengji.app.navigation.AppDestination
import com.hengji.app.theme.HengjiTheme
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.AdaptiveAppShell
import com.hengji.app.ui.screens.AssetsScreen
import com.hengji.app.ui.screens.InsightsScreen
import com.hengji.app.ui.screens.LedgerScreen
import com.hengji.app.ui.screens.OverviewScreen
import com.hengji.app.ui.screens.SettingsScreen
import com.hengji.app.ui.screens.FirstRunGuide
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerJsonExporter
import com.hengji.data.LedgerCsvExporter
import com.hengji.data.LedgerRepository
import com.hengji.data.LedgerSnapshot
import com.hengji.data.MAX_MONTHLY_BUDGET_MINOR
import com.hengji.data.PersistentLedgerRepository
import com.hengji.domain.AssetId
import com.hengji.domain.Asset
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.ItemCondition
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.domain.UsageEvent
import com.hengji.domain.UsageEventId
import com.hengji.insights.InsightFeedback
import com.hengji.insights.InsightExplanationConsent
import com.hengji.insights.PersonalInsightGenerationResult
import com.hengji.insights.PersonalInsightModelOrchestrator
import com.hengji.insights.PersonalInsightModelProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Clock

private const val PERSONAL_ANALYSIS_INTERVAL_MILLIS: Long = 30L * 24L * 60L * 60L * 1_000L

@Composable
fun HengjiApp() {
    val repository = remember { InMemoryLedgerRepository(DomainDemoData.initialSnapshot) }
    HengjiApp(repository)
}

/**
 * Application composition root. Platform entry points inject the durable repository here;
 * the no-argument overload remains an explicitly in-memory preview fallback.
 */
@Composable
fun HengjiApp(repository: LedgerRepository) {
    val gateway = remember(repository) { PreviewLedgerGateway(repository) }
    HengjiApp(
        gateway = gateway,
        userImportDocumentPicker = UnavailableUserImportDocumentPicker,
        ledgerExportWriter = PreviewOnlyLedgerExportWriter,
    )
}

/** Durable platform entry point. All Room access remains coroutine-first and off the UI blocking path. */
@Composable
fun HengjiApp(
    repository: PersistentLedgerRepository,
    userImportDocumentPicker: UserImportDocumentPicker = UnavailableUserImportDocumentPicker,
    userLocalCapturePicker: UserLocalCapturePicker = UnavailableUserLocalCapturePicker,
    ledgerExportWriter: LedgerExportWriter = PreviewOnlyLedgerExportWriter,
    seedDemoData: Boolean = false,
    quickEntryRequest: QuickEntryRequest? = null,
    localCaptureLaunchRequest: LocalCaptureLaunchRequest? = null,
    quickEntryShortcutStatus: String? = null,
    priceNotificationControl: PriceNotificationControl? = null,
    systemReduceMotion: Boolean = false,
    personalInsightModelProvider: PersonalInsightModelProvider? = null,
) {
    val gateway = remember(repository) { PersistentAppLedgerGateway(repository) }
    HengjiApp(
        gateway,
        userImportDocumentPicker,
        userLocalCapturePicker,
        ledgerExportWriter,
        seedDemoData,
        quickEntryRequest,
        localCaptureLaunchRequest,
        quickEntryShortcutStatus,
        priceNotificationControl,
        systemReduceMotion = systemReduceMotion,
        personalInsightModelProvider = personalInsightModelProvider,
    )
}

@Composable
fun HengjiApp(
    gateway: AppLedgerGateway,
    userImportDocumentPicker: UserImportDocumentPicker = UnavailableUserImportDocumentPicker,
    userLocalCapturePicker: UserLocalCapturePicker = UnavailableUserLocalCapturePicker,
    ledgerExportWriter: LedgerExportWriter = PreviewOnlyLedgerExportWriter,
    seedDemoData: Boolean = false,
    quickEntryRequest: QuickEntryRequest? = null,
    localCaptureLaunchRequest: LocalCaptureLaunchRequest? = null,
    quickEntryShortcutStatus: String? = null,
    priceNotificationControl: PriceNotificationControl? = null,
    systemReduceMotion: Boolean = false,
    personalInsightModelProvider: PersonalInsightModelProvider? = null,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Overview) }
    var appearanceMode by rememberSaveable { mutableStateOf(AppAppearanceMode.SYSTEM) }
    var reduceMotionOverride by rememberSaveable { mutableStateOf(false) }
    var showAddTransaction by rememberSaveable { mutableStateOf(false) }
    var showMonthlyBudgetDialog by rememberSaveable { mutableStateOf(false) }
    var showAddAsset by rememberSaveable { mutableStateOf(false) }
    var manualQuoteAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showImportWizard by rememberSaveable { mutableStateOf(false) }
    var showFirstRunGuideAgain by rememberSaveable { mutableStateOf(false) }
    var firstRunGuideDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionPendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTransactionUndo by remember { mutableStateOf<PendingTransactionUndo?>(null) }
    var exportPreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var dataActionStatus by remember { mutableStateOf<String?>(null) }
    var snapshot by remember(gateway) { mutableStateOf<LedgerSnapshot?>(null) }
    var storageBusy by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var insightFeedbackBusyKey by remember { mutableStateOf<String?>(null) }
    var insightFeedbackResetting by remember { mutableStateOf(false) }
    var insightFeedbackStatus by remember { mutableStateOf<String?>(null) }
    var insightModelResult by remember { mutableStateOf<PersonalInsightGenerationResult.Generated?>(null) }
    var insightModelBusy by remember { mutableStateOf(false) }
    var insightModelStatus by remember { mutableStateOf<String?>(null) }
    var quickEntryMerchant by rememberSaveable { mutableStateOf("") }
    var quickEntryAmountMinor by rememberSaveable { mutableStateOf<Long?>(null) }
    var quickEntryCategory by rememberSaveable { mutableStateOf("其他") }
    var quickEntryDisclosure by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val transactionDeletionCoordinator = remember(gateway) { TransactionDeletionCoordinator(gateway) }
    val importPort = remember(gateway, userImportDocumentPicker, userLocalCapturePicker) {
        LocalImportFlowPort(gateway, userImportDocumentPicker, userLocalCapturePicker)
    }
    val importHost = rememberImportFlowHost(importPort) {
        scope.launch {
            snapshot = gateway.snapshot()
        }
    }

    LaunchedEffect(localCaptureLaunchRequest?.sequence) {
        val request = localCaptureLaunchRequest ?: return@LaunchedEffect
        showAddTransaction = false
        showImportWizard = true
        importHost.dispatch(
            ImportFlowEvent.SourceChosen(ImportSource.LocalCapture(request.mode)),
        )
    }

    LaunchedEffect(gateway) {
        storageBusy = true
        try {
            var loaded = gateway.snapshot()
            if (DemoDataSeedPolicy.shouldSeed(seedDemoData, loaded)) {
                gateway.replaceWith(DomainDemoData.initialSnapshot)
                loaded = gateway.snapshot()
            }
            snapshot = loaded
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            storageError = "无法打开本机受保护账本；原有文件保持不变，请重试。"
        } finally {
            storageBusy = false
        }
    }

    LaunchedEffect(quickEntryRequest?.sequence) {
        val request = quickEntryRequest ?: return@LaunchedEffect
        if (request.sequence == 0L) return@LaunchedEffect
        quickEntryMerchant = request.merchant
        quickEntryAmountMinor = request.amountMinor
        quickEntryCategory = request.categoryLabel
        quickEntryDisclosure = request.sourceDisclosure
        editingTransactionId = null
        showAddTransaction = true
    }

    LaunchedEffect(pendingTransactionUndo) {
        val pending = pendingTransactionUndo ?: return@LaunchedEffect
        val remainingMillis =
            (pending.expiresAtEpochMillis - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0)
        delay(remainingMillis)
        if (pendingTransactionUndo == pending) {
            pendingTransactionUndo = null
        }
    }

    fun mutate(block: suspend () -> Unit) {
        if (storageBusy) return
        scope.launch {
            storageBusy = true
            storageError = null
            try {
                block()
                snapshot = gateway.snapshot()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                storageError = "本机账本操作未完成；本次更改没有确认写入，请重试。"
            } finally {
                storageBusy = false
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        if (storageBusy) return
        scope.launch {
            storageBusy = true
            storageError = null
            val deletedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
            try {
                when (
                    val result = transactionDeletionCoordinator.delete(
                        transactionId = TransactionId(transactionId),
                        nowEpochMillis = deletedAtEpochMillis,
                    )
                ) {
                    is TransactionDeletionResult.Deleted -> {
                        snapshot = gateway.snapshot()
                        transactionPendingDeletionId = null
                        pendingTransactionUndo = result.pendingUndo
                    }
                    TransactionDeletionResult.Rejected -> {
                        transactionPendingDeletionId = null
                        storageError = "删除未完成：记录已变化或存在关联退款。请刷新后再试。"
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                transactionPendingDeletionId = null
                storageError = "删除未完成：记录已变化或存在关联退款。"
            } finally {
                storageBusy = false
            }
        }
    }

    fun undoTransactionDeletion(pending: PendingTransactionUndo) {
        if (storageBusy) return
        scope.launch {
            storageBusy = true
            storageError = null
            try {
                val restored = transactionDeletionCoordinator.undo(
                    pending = pending,
                    nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
                )
                pendingTransactionUndo = null
                if (restored) {
                    snapshot = gateway.snapshot()
                } else {
                    storageError = "撤销失败：账单已再次变化或撤销窗口已过期。"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                pendingTransactionUndo = null
                storageError = "撤销失败：账单已再次变化或撤销窗口已过期。"
            } finally {
                storageBusy = false
            }
        }
    }

    fun persistInsightPreferences(
        preferences: InsightPreferenceRecord,
        busyKey: String?,
        resetting: Boolean,
        successMessage: String,
    ) {
        if (storageBusy) return
        scope.launch {
            storageBusy = true
            storageError = null
            insightFeedbackStatus = null
            insightFeedbackBusyKey = busyKey
            insightFeedbackResetting = resetting
            try {
                gateway.saveInsightPreferences(preferences)
                snapshot = gateway.snapshot()
                insightFeedbackStatus = successMessage
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                storageError = "建议反馈未能保存到本机；原账本保持不变。"
            } finally {
                insightFeedbackBusyKey = null
                insightFeedbackResetting = false
                storageBusy = false
            }
        }
    }

    val currentSnapshot = snapshot
    val today = remember(currentSnapshot) { currentLocalDate() }
    val nowEpochMillis = remember(currentSnapshot) { Clock.System.now().toEpochMilliseconds() }
    val darkTheme = appearanceMode.resolve(isSystemInDarkTheme())
    val reduceMotion = shouldReduceMotion(
        systemRequestsReduction = systemReduceMotion,
        userRequestsAdditionalReduction = reduceMotionOverride,
    )

    if (currentSnapshot == null) {
        HengjiTheme(darkTheme = darkTheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            storageError?.let { message ->
                AlertDialog(
                    onDismissRequest = { storageError = null },
                    title = { Text("无法打开本机账本") },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = { storageError = null }) { Text("知道了") } },
                )
            }
        }
        return
    }

    val transactions = remember(currentSnapshot, today) { DomainDemoData.transactions(currentSnapshot, today) }
    val assets = remember(currentSnapshot, today) { DomainDemoData.assets(currentSnapshot, today) }
    val insightComputation = remember(currentSnapshot, today, nowEpochMillis) {
        DomainDemoData.insightComputation(currentSnapshot, today, nowEpochMillis)
    }
    val localInsightFeed = insightComputation.feed
    val insightModelRequest = insightComputation.modelRequest
    val personalAiEnabled = currentSnapshot.insightPreferences.personalAiEnabled
    val recentEntryPresets = remember(currentSnapshot.transactions) {
        currentSnapshot.transactions
            .asSequence()
            .filter { !it.isDeleted && it.kind != TransactionKind.REFUND && it.merchant != null }
            .sortedByDescending { it.bookedOn }
            .distinctBy { "${it.kind}:${it.merchant?.normalizedName}" }
            .take(3)
            .map { transaction ->
                QuickEntryPreset(
                    merchant = requireNotNull(transaction.merchant).displayName,
                    category = categoryLabelForId(transaction.categoryId.value),
                    amountMinor = transaction.amount.minorUnits,
                    kind = transaction.kind,
                )
            }
            .toList()
    }
    val savedAnalysis = currentSnapshot.insightPreferences.personalAnalysisHistory.lastOrNull()
    val insightModelConsent = InsightExplanationConsent(
        enabled = personalAiEnabled,
        consentedAtEpochMillis = if (personalAiEnabled) savedAnalysis?.createdAtEpochMillis ?: 0L else null,
    )
    LaunchedEffect(
        insightModelRequest,
        personalAiEnabled,
        personalInsightModelProvider,
        savedAnalysis?.createdAtEpochMillis,
    ) {
        insightModelResult = null
        if (!insightModelConsent.enabled) {
            insightModelStatus = "智能分析已关闭；不会加载模型，记账和账单仍可正常使用。"
            insightModelBusy = false
            return@LaunchedEffect
        }
        val request = insightModelRequest
        if (request == null) {
            insightModelStatus = if (localInsightFeed.firstAnalysisEligible) {
                "这期没有足够可靠的重点，恒迹不会为了显得聪明而编造结论。"
            } else {
                "再积累 ${localInsightFeed.daysUntilFirstAnalysis} 天左右的消费记录，就会生成第一次专属分析。"
            }
            insightModelBusy = false
            return@LaunchedEffect
        }
        val analysisDue = savedAnalysis == null ||
            nowEpochMillis - savedAnalysis.createdAtEpochMillis >= PERSONAL_ANALYSIS_INTERVAL_MILLIS
        if (!analysisDue) {
            insightModelStatus = "已显示最近一次专属分析；满一个月后会结合新记录和反馈再次分析。"
            return@LaunchedEffect
        }
        insightModelBusy = true
        val result = PersonalInsightModelOrchestrator(personalInsightModelProvider).generate(
            consent = insightModelConsent,
            request = request,
        )
        when (result) {
            is PersonalInsightGenerationResult.Generated -> {
                insightModelResult = result
                val createdAt = Clock.System.now().toEpochMilliseconds()
                val currentPreferences = currentSnapshot.insightPreferences
                val history = (
                    currentPreferences.personalAnalysisHistory +
                        result.toPersonalAnalysisRecord(createdAt)
                    ).takeLast(12)
                try {
                    gateway.saveInsightPreferences(
                        currentPreferences.copy(
                            updatedAtEpochMillis = createdAt,
                            personalAnalysisHistory = history,
                        ),
                    )
                    snapshot = gateway.snapshot()
                    insightModelStatus = "专属分析已保存在本机；以后会结合新记录和你的反馈继续学习。"
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    insightModelStatus = "分析已生成，但没有成功保存；账本数据没有改变。"
                }
            }
            is PersonalInsightGenerationResult.LocalFallback -> {
                insightModelStatus = when (result.reason) {
                    "model-provider-unavailable" -> "内置模型不可用，已安全回退；请重新安装完整应用包。"
                    "model-provider-not-privacy-reviewed" -> "模型未通过隐私评审，本次不会生成智能建议。"
                    else -> "模型本次未通过本机校验，已改为显示经过验证的普通建议。"
                }
            }
        }
        insightModelBusy = false
    }
    val displayedModelResult = insightModelResult ?: savedAnalysis?.toGeneratedModelResult()
    val insightFeed = remember(localInsightFeed, displayedModelResult) {
        localInsightFeed.withModelResult(displayedModelResult)
    }
    val insights = insightFeed.items

    HengjiTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                AdaptiveAppShell(
                    destination = destination,
                    paneTitle = if (showImportWizard) "导入账单" else destination.label,
                    onDestinationChange = {
                        showImportWizard = false
                        destination = it
                    },
                    onAddTransaction = { showAddTransaction = true },
                    allowPageSwipe = !showImportWizard,
                ) { page ->
                    if (showImportWizard) {
                        ImportWizard(
                            state = importHost.state,
                            onEvent = importHost.dispatch,
                            localCaptureAvailable = userLocalCapturePicker.isAvailable,
                            reduceMotion = reduceMotion,
                        )
                    } else when (page) {
                    AppDestination.Overview -> OverviewScreen(
                        transactions = transactions,
                        assets = assets,
                        insights = insights,
                        asOf = today,
                        onAddTransaction = { showAddTransaction = true },
                        onOpenImport = { showImportWizard = true },
                        onOpenLedger = { destination = AppDestination.Ledger },
                        onOpenInsights = { destination = AppDestination.Insights },
                        monthlyBudgetMinor = currentSnapshot.insightPreferences.monthlyBudgetMinor,
                        onEditMonthlyBudget = { showMonthlyBudgetDialog = true },
                    )
                    AppDestination.Ledger -> LedgerScreen(
                        transactions = transactions,
                        onAddTransaction = { showAddTransaction = true },
                        onEditTransaction = { editingTransactionId = it },
                        onDeleteTransaction = { transactionPendingDeletionId = it },
                        onOpenImport = { showImportWizard = true },
                    )
                    AppDestination.Assets -> AssetsScreen(
                        assets = assets,
                        onAddAsset = { showAddAsset = true },
                        onAddManualQuote = { manualQuoteAssetId = it },
                        onSaleTargetChange = { assetId, targetPriceMinor ->
                            mutate {
                                val asset = currentSnapshot.assets.firstOrNull { it.id.value == assetId }
                                    ?: return@mutate
                                gateway.upsertAsset(
                                    if (targetPriceMinor == null) {
                                        AssetSaleTargetEditor.clear(asset)
                                    } else {
                                        AssetSaleTargetEditor.set(asset, targetPriceMinor)
                                    },
                                )
                            }
                        },
                        onRecordUsage = { assetId ->
                            mutate {
                                gateway.addUsageEvent(
                                    UsageEvent(
                                        id = UsageEventId("local-usage-${currentSnapshot.revision + 1}"),
                                        assetId = AssetId(assetId),
                                        occurredOn = currentLocalDate(),
                                    ),
                                )
                            }
                        },
                    )
                    AppDestination.Insights -> InsightsScreen(
                        feed = insightFeed,
                        busyDeduplicationKey = insightFeedbackBusyKey,
                        isResetting = insightFeedbackResetting,
                        reduceMotion = reduceMotion,
                        statusMessage = insightFeedbackStatus,
                        modelAvailable = personalInsightModelProvider != null,
                        modelConsentEnabled = insightModelConsent.enabled,
                        modelBusy = insightModelBusy,
                        modelStatusMessage = insightModelStatus,
                        onModelConsentChange = { enabled ->
                            if (!enabled) {
                                insightModelResult = null
                            }
                            val updatedAt = Clock.System.now().toEpochMilliseconds()
                            persistInsightPreferences(
                                preferences = currentSnapshot.insightPreferences.copy(
                                    personalAiEnabled = enabled,
                                    updatedAtEpochMillis = updatedAt,
                                ),
                                busyKey = null,
                                resetting = false,
                                successMessage = if (enabled) {
                                    "智能分析已开启，所有推理只在本机进行"
                                } else {
                                    "智能分析已关闭，模型不会再运行"
                                },
                            )
                        },
                        onFeedback = { deduplicationKey, feedback ->
                            val updatedAt = Clock.System.now().toEpochMilliseconds()
                            val preferences = InsightFeedbackReducer.apply(
                                current = currentSnapshot.insightPreferences,
                                deduplicationKey = deduplicationKey,
                                insightType = insights.first { it.deduplicationKey == deduplicationKey }.type,
                                feedback = feedback,
                                nowEpochMillis = updatedAt,
                            )
                            val message = when (feedback) {
                                InsightFeedback.ADOPTED -> "已采纳建议，反馈已保存到本机"
                                InsightFeedback.SNOOZED -> "已稍后 7 天，届时建议会重新出现"
                                InsightFeedback.IGNORED -> "已忽略建议，可通过“恢复默认”找回"
                                InsightFeedback.NEW -> error("NEW is not a user feedback action")
                            }
                            persistInsightPreferences(
                                preferences = preferences,
                                busyKey = deduplicationKey,
                                resetting = false,
                                successMessage = message,
                            )
                        },
                        onResetFeedback = {
                            val updatedAt = Clock.System.now().toEpochMilliseconds()
                            persistInsightPreferences(
                                preferences = InsightFeedbackReducer.reset(updatedAt).copy(
                                    personalAiEnabled = currentSnapshot.insightPreferences.personalAiEnabled,
                                    onboardingCompletedAtEpochMillis =
                                        currentSnapshot.insightPreferences.onboardingCompletedAtEpochMillis,
                                    monthlyBudgetMinor = currentSnapshot.insightPreferences.monthlyBudgetMinor,
                                ),
                                busyKey = null,
                                resetting = true,
                                successMessage = "已恢复默认建议偏好",
                            )
                        },
                    )
                    AppDestination.Settings -> SettingsScreen(
                        appearanceMode = appearanceMode,
                        onAppearanceModeChange = { appearanceMode = it },
                        reduceMotion = reduceMotionOverride,
                        systemReduceMotion = systemReduceMotion,
                        onReduceMotionChange = { reduceMotionOverride = it },
                        dataActionStatus = dataActionStatus,
                        onExportData = {
                            mutate {
                                val json = LedgerJsonExporter.export(gateway.snapshot(includeDeleted = true))
                                val location = ledgerExportWriter.save(
                                    suggestedFileName = "hengji-ledger-${today}.json",
                                    utf8Content = json,
                                    mediaType = "application/json",
                                )
                                if (location == null) {
                                    exportPreview = "JSON 数据导出" to json
                                    dataActionStatus = "已生成当前账本的 JSON 导出内容"
                                } else {
                                    dataActionStatus = "账本已导出到 $location"
                                }
                            }
                        },
                        onExportCsv = {
                            mutate {
                                val csv = LedgerCsvExporter.export(gateway.snapshot(includeDeleted = true))
                                val location = ledgerExportWriter.save(
                                    suggestedFileName = "hengji-transactions-${today}.csv",
                                    utf8Content = csv,
                                    mediaType = "text/csv",
                                )
                                if (location == null) {
                                    exportPreview = "CSV 账单导出" to csv
                                    dataActionStatus = "已生成当前账单的 CSV 导出内容"
                                } else {
                                    dataActionStatus = "账单已导出到 $location"
                                }
                            }
                        },
                        onRestoreData = {
                            mutate {
                                val picked = userImportDocumentPicker.pick(
                                    format = ImportDocumentFormat.Json,
                                    purpose = UserDocumentPurpose.LedgerRestore,
                                )
                                if (picked != null) {
                                    val restored = LedgerJsonExporter.restore(picked.content)
                                    gateway.replaceWith(restored)
                                    pendingTransactionUndo = null
                                    transactionPendingDeletionId = null
                                    dataActionStatus = "已从 ${picked.displayName} 恢复本机账本"
                                }
                            }
                        },
                        onClearData = { confirmClear = true },
                        onOpenImport = { showImportWizard = true },
                        onOpenFirstRunGuide = { showFirstRunGuideAgain = true },
                        storageStatus = if (gateway is PersistentAppLedgerGateway) {
                            "认证加密账本已跨重启持久化 · 平台密钥保护"
                        } else {
                            "内存预览 · 关闭后不保留"
                        },
                        quickEntryShortcutStatus = quickEntryShortcutStatus,
                        localCaptureAvailable = userLocalCapturePicker.isAvailable,
                        priceNotificationControl = priceNotificationControl?.takeIf { control ->
                            control.shouldDisplay(
                                hasAuthorizedLiveQuotes =
                                    currentSnapshot.marketQuotes.any { quote -> quote.isLiveSource },
                            )
                        },
                    )
                    }
                }
                pendingTransactionUndo?.let { pending ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = HengjiSpacing.lg,
                                end = HengjiSpacing.lg,
                                bottom = 96.dp,
                            )
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                        action = {
                            TextButton(onClick = { undoTransactionDeletion(pending) }) {
                                Text("撤销")
                            }
                        },
                    ) {
                        Text("这笔账已删除；8 秒内可撤销")
                    }
                }
            }
        }

        val editingTransaction = editingTransactionId?.let { id ->
            currentSnapshot.transactions.firstOrNull { it.id.value == id }
        }
        if (showAddTransaction || editingTransaction != null) {
            AddTransactionDialog(
                title = if (editingTransaction == null) "记一笔" else "编辑这笔账",
                initialMerchant = editingTransaction?.merchant?.displayName ?: quickEntryMerchant,
                initialAmount = editingTransaction?.amount?.minorUnits?.let(::minorUnitsToInput)
                    ?: quickEntryAmountMinor?.let(::minorUnitsToInput).orEmpty(),
                initialCategory = editingTransaction?.categoryId?.value?.let(::categoryLabelForId)
                    ?: quickEntryCategory,
                initialKind = editingTransaction?.kind ?: TransactionKind.EXPENSE,
                initialBookedOn = editingTransaction?.bookedOn ?: today,
                asOf = today,
                recentPresets = if (editingTransaction == null) recentEntryPresets else emptyList(),
                sourceDisclosure = if (editingTransaction == null) quickEntryDisclosure else null,
                localCaptureAvailable = editingTransaction == null && userLocalCapturePicker.isAvailable,
                onLongScreenshot = {
                    showAddTransaction = false
                    showImportWizard = true
                    importHost.dispatch(
                        ImportFlowEvent.SourceChosen(
                            ImportSource.LocalCapture(LocalCaptureMode.LongScreenshot),
                        ),
                    )
                },
                onOneClickCapture = {
                    showAddTransaction = false
                    showImportWizard = true
                    importHost.dispatch(
                        ImportFlowEvent.SourceChosen(
                            ImportSource.LocalCapture(LocalCaptureMode.ImageOrPdf),
                        ),
                    )
                },
                onDismiss = {
                    showAddTransaction = false
                    editingTransactionId = null
                    quickEntryMerchant = ""
                    quickEntryAmountMinor = null
                    quickEntryCategory = "其他"
                    quickEntryDisclosure = null
                },
                onAdd = { merchant, category, amountMinor, kind, bookedOn ->
                    mutate {
                        val updated = editingTransaction?.copy(
                            merchant = Merchant(merchant),
                            categoryId = CategoryId(categoryIdForLabel(category)),
                            amount = Money(amountMinor, editingTransaction.amount.currency),
                            kind = if (editingTransaction.kind == TransactionKind.REFUND) {
                                TransactionKind.REFUND
                            } else {
                                kind
                            },
                            bookedOn = bookedOn,
                        ) ?: Transaction(
                            id = TransactionId("local-${currentSnapshot.revision + 1}"),
                            kind = kind,
                            amount = Money(amountMinor, CurrencyCode.CNY),
                            bookedOn = bookedOn,
                            categoryId = CategoryId(categoryIdForLabel(category)),
                            merchant = Merchant(merchant),
                            source = TransactionSource.MANUAL,
                        )
                        gateway.upsertTransaction(updated)
                        showAddTransaction = false
                        editingTransactionId = null
                        quickEntryMerchant = ""
                        quickEntryAmountMinor = null
                        quickEntryCategory = "其他"
                        quickEntryDisclosure = null
                        destination = AppDestination.Ledger
                    }
                },
            )
        }

        if (showMonthlyBudgetDialog) {
            MonthlyBudgetDialog(
                initialBudgetMinor = currentSnapshot.insightPreferences.monthlyBudgetMinor,
                onDismiss = { showMonthlyBudgetDialog = false },
                onSave = { budgetMinor ->
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    showMonthlyBudgetDialog = false
                    persistInsightPreferences(
                        preferences = currentSnapshot.insightPreferences.copy(
                            monthlyBudgetMinor = budgetMinor,
                            updatedAtEpochMillis = updatedAt,
                        ),
                        busyKey = null,
                        resetting = false,
                        successMessage = if (budgetMinor == null) "月预算已清除" else "月预算已保存到本机",
                    )
                },
            )
        }

        val transactionPendingDeletion = transactionPendingDeletionId?.let { id ->
            currentSnapshot.transactions.firstOrNull { it.id.value == id && !it.isDeleted }
        }
        transactionPendingDeletion?.let { transaction ->
            AlertDialog(
                onDismissRequest = { transactionPendingDeletionId = null },
                title = { Text("删除这笔账？") },
                text = {
                    Text(
                        "“${transaction.merchant?.displayName ?: "未命名交易"}”将从账单、首页和智能分析中移除。" +
                            "删除成功后可在 8 秒内撤销；如记录已变化或存在关联退款，系统会拒绝删除。",
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !storageBusy,
                        onClick = { deleteTransaction(transaction.id.value) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { transactionPendingDeletionId = null }) {
                        Text("取消")
                    }
                },
            )
        }

        if (showAddAsset) {
            AddAssetDialog(
                onDismiss = { showAddAsset = false },
                onAdd = { name, category, purchaseMinor, estimatedMinor, recordExpense ->
                    mutate {
                        val nextRevision = currentSnapshot.revision + 1
                        val assetId = AssetId("local-asset-$nextRevision")
                        val purchasedOn = currentLocalDate()
                        val asset = Asset(
                            id = assetId,
                            name = name,
                            categoryId = CategoryId(categoryIdForLabel(category)),
                            purchasePrice = Money(purchaseMinor, CurrencyCode.CNY),
                            purchasedOn = purchasedOn,
                            currentEstimatedValue = Money(estimatedMinor ?: purchaseMinor, CurrencyCode.CNY),
                        )
                        val transaction = if (recordExpense) {
                            Transaction(
                                id = TransactionId("local-purchase-$nextRevision"),
                                kind = TransactionKind.EXPENSE,
                                amount = Money(purchaseMinor, CurrencyCode.CNY),
                                bookedOn = purchasedOn,
                                categoryId = CategoryId(categoryIdForLabel(category)),
                                merchant = Merchant(name),
                                source = TransactionSource.MANUAL,
                                assetId = assetId,
                            )
                        } else {
                            null
                        }
                        gateway.replaceWith(
                            currentSnapshot.copy(
                                assets = currentSnapshot.assets + asset,
                                transactions = currentSnapshot.transactions + listOfNotNull(transaction),
                            ),
                        )
                        showAddAsset = false
                        destination = AppDestination.Assets
                    }
                },
            )
        }

        manualQuoteAssetId?.let { assetIdValue ->
            val asset = currentSnapshot.assets.firstOrNull { it.id.value == assetIdValue }
            if (asset != null) {
                AddManualQuoteDialog(
                    assetName = asset.name,
                    currency = asset.purchasePrice.currency,
                    onDismiss = { manualQuoteAssetId = null },
                    onAdd = { specification, condition, priceMinor, shippingMinor ->
                        mutate {
                            val nextRevision = currentSnapshot.revision + 1
                            gateway.addMarketQuote(
                                ManualMarketQuoteFactory.create(
                                    id = "manual-quote-$nextRevision",
                                    assetId = AssetId(assetIdValue),
                                    specification = specification,
                                    condition = condition,
                                    priceMinor = priceMinor,
                                    shippingMinor = shippingMinor,
                                    collectedOn = today,
                                    asOf = today,
                                    currency = asset.purchasePrice.currency,
                                ),
                            )
                            manualQuoteAssetId = null
                            destination = AppDestination.Assets
                        }
                    },
                )
            }
        }

        exportPreview?.let { (title, content) ->
            AlertDialog(
                onDismissRequest = { exportPreview = null },
                title = { Text(title) },
                text = {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            if (title.startsWith("CSV")) {
                                "这是账单表格内容；金额使用整数最小单位。为审计与恢复，软删除记录及删除时间也会导出，日常界面仍会隐藏这些记录。"
                            } else {
                                "这是完整的本机账本备份内容，可用于恢复当前数据；其中包含用于审计与恢复的软删除记录。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            Text(content, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { exportPreview = null }) { Text("完成") }
                },
            )
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("清除所有本机数据？") },
                text = { Text("此操作会删除账单、物品、使用记录和本机估值。建议先导出；清除后不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            mutate {
                                gateway.clear()
                                pendingTransactionUndo = null
                                transactionPendingDeletionId = null
                                confirmClear = false
                                dataActionStatus = "本机账本已清除"
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("确认清除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("取消") }
                },
            )
        }

        val shouldShowFirstRunGuide = showFirstRunGuideAgain ||
            (currentSnapshot.insightPreferences.onboardingCompletedAtEpochMillis == null &&
                !firstRunGuideDismissedThisSession)
        if (shouldShowFirstRunGuide) {
            fun finishFirstRunGuide() {
                showFirstRunGuideAgain = false
                firstRunGuideDismissedThisSession = true
                val completedAt = Clock.System.now().toEpochMilliseconds()
                persistInsightPreferences(
                    preferences = currentSnapshot.insightPreferences.copy(
                        onboardingCompletedAtEpochMillis = completedAt,
                        updatedAtEpochMillis = completedAt,
                    ),
                    busyKey = null,
                    resetting = false,
                    successMessage = "使用教程已完成，可在设置里重新打开",
                )
            }
            FirstRunGuide(
                onFinished = ::finishFirstRunGuide,
                onTryAddTransaction = { showAddTransaction = true },
                onTryImport = { showImportWizard = true },
            )
        }

        storageError?.let { message ->
            AlertDialog(
                onDismissRequest = { storageError = null },
                title = { Text("本机操作未完成") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { storageError = null }) { Text("知道了") } },
            )
        }
    }
}

private fun currentLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
private fun AddTransactionDialog(
    title: String,
    initialMerchant: String,
    initialAmount: String,
    initialCategory: String,
    initialKind: TransactionKind,
    initialBookedOn: LocalDate,
    asOf: LocalDate,
    recentPresets: List<QuickEntryPreset> = emptyList(),
    sourceDisclosure: String? = null,
    localCaptureAvailable: Boolean = false,
    onLongScreenshot: () -> Unit = {},
    onOneClickCapture: () -> Unit = {},
    onDismiss: () -> Unit,
    onAdd: (merchant: String, category: String, amountMinor: Long, kind: TransactionKind, bookedOn: LocalDate) -> Unit,
) {
    var merchant by remember(initialMerchant) { mutableStateOf(initialMerchant) }
    var amount by remember(initialAmount) { mutableStateOf(initialAmount) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    var bookedOnInput by remember(initialBookedOn) { mutableStateOf(initialBookedOn.toString()) }
    val amountMinor = parseMoneyToMinor(amount)
    val amountError = amount.isNotEmpty() && (amountMinor == null || amountMinor <= 0)
    val bookedOn = runCatching { LocalDate.parse(bookedOnInput) }.getOrNull()
    val dateError = bookedOnInput.isNotEmpty() && (bookedOn == null || bookedOn > asOf)
    val valid = merchant.isNotBlank() && amountMinor != null && amountMinor > 0 && bookedOn != null && bookedOn <= asOf
    val yesterday = LocalDate.fromEpochDays(asOf.toEpochDays() - 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "无需登录，仅写入本机账本，不会上传。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sourceDisclosure?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (initialKind == TransactionKind.REFUND) {
                    Text(
                        "退款记录 · 类型保持不变",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(TransactionKind.EXPENSE to "支出", TransactionKind.INCOME to "收入").forEach { item ->
                            FilterChip(
                                selected = kind == item.first,
                                onClick = { kind = item.first },
                                label = { Text(item.second) },
                            )
                        }
                    }
                }
                if (recentPresets.isNotEmpty()) {
                    Text("最近常用", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recentPresets.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    merchant = preset.merchant
                                    amount = minorUnitsToInput(preset.amountMinor)
                                    category = preset.category
                                    kind = preset.kind
                                },
                                label = { Text("${preset.merchant} · ${formatMoney(preset.amountMinor)}") },
                            )
                        }
                    }
                    Text(
                        "快捷项只会预填，仍需确认后保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (localCaptureAvailable) {
                    Text(
                        "不想手填？",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FilledTonalButton(
                        onClick = onLongScreenshot,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("识别长截图（可提取多笔）")
                    }
                    OutlinedButton(
                        onClick = onOneClickCapture,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("一键读取图片或 PDF")
                    }
                    Text(
                        "只读取你主动选择的文件，识别后先预览，不会直接入账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("商户或用途（必填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = bookedOn == asOf,
                        onClick = { bookedOnInput = asOf.toString() },
                        label = { Text("今天") },
                    )
                    FilterChip(
                        selected = bookedOn == yesterday,
                        onClick = { bookedOnInput = yesterday.toString() },
                        label = { Text("昨天") },
                    )
                }
                OutlinedTextField(
                    value = bookedOnInput,
                    onValueChange = { value ->
                        bookedOnInput = value.filter { it.isDigit() || it == '-' }.take(10)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("日期（YYYY-MM-DD）") },
                    singleLine = true,
                    isError = dateError,
                    supportingText = {
                        if (dateError) {
                            Text(if (bookedOn == null) "请输入有效日期" else "不能记录未来账单")
                        }
                    },
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额（必填）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        if (amountError) {
                            Text(if (amountMinor == null) "请输入最多两位小数" else "金额必须大于 0")
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("餐饮", "交通", "居家", "数码", "其他").forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(merchant.trim(), category, amountMinor ?: 0L, kind, requireNotNull(bookedOn)) },
                enabled = valid,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private data class QuickEntryPreset(
    val merchant: String,
    val category: String,
    val amountMinor: Long,
    val kind: TransactionKind,
)

@Composable
private fun MonthlyBudgetDialog(
    initialBudgetMinor: Long?,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    var amount by remember(initialBudgetMinor) {
        mutableStateOf(initialBudgetMinor?.let(::minorUnitsToInput).orEmpty())
    }
    val amountMinor = parseMoneyToMinor(amount)
    val amountError = amount.isNotEmpty() &&
        (amountMinor == null || amountMinor !in 1..MAX_MONTHLY_BUDGET_MINOR)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialBudgetMinor == null) "设置月预算" else "修改月预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                Text(
                    "预算只保存在本机，用于计算本月可用额度和消费节奏。退款会从支出中扣除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("每月预算") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        if (amountError) Text("请输入大于 0 的有效金额")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amountMinor) },
                enabled = amountMinor != null && amountMinor in 1..MAX_MONTHLY_BUDGET_MINOR,
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initialBudgetMinor != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("清除预算", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun AddAssetDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, category: String, purchaseMinor: Long, estimatedMinor: Long?, recordExpense: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("数码") }
    var purchaseAmount by remember { mutableStateOf("") }
    var estimatedAmount by remember { mutableStateOf("") }
    var recordExpense by remember { mutableStateOf(true) }
    val purchaseMinor = parseMoneyToMinor(purchaseAmount)
    val estimatedMinor = if (estimatedAmount.isBlank()) null else parseMoneyToMinor(estimatedAmount)
    val purchaseError = purchaseAmount.isNotEmpty() && (purchaseMinor == null || purchaseMinor <= 0)
    val estimatedError = estimatedAmount.isNotEmpty() && (estimatedMinor == null || estimatedMinor < 0)
    val valid = name.isNotBlank() && purchaseMinor != null && purchaseMinor > 0 &&
        (estimatedAmount.isBlank() || estimatedMinor != null && estimatedMinor >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增物品") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "购买价用于计算日均拥有成本；当前估值为空时先按购买价记录，后续可用手工或授权报价更新。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("产品名称（必填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("数码", "居家", "交通", "其他").forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) },
                        )
                    }
                }
                OutlinedTextField(
                    value = purchaseAmount,
                    onValueChange = { purchaseAmount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("购买价格（必填）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = purchaseError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    supportingText = {
                        if (purchaseError) {
                            Text(if (purchaseMinor == null) "请输入最多两位小数" else "购买价格必须大于 0")
                        }
                    },
                )
                OutlinedTextField(
                    value = estimatedAmount,
                    onValueChange = { estimatedAmount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("当前手工估值（可选）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = estimatedError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        if (estimatedError) {
                            Text(if (estimatedMinor == null) "请输入最多两位小数" else "估值不能小于 0")
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = recordExpense,
                            role = Role.Switch,
                            onValueChange = { recordExpense = it },
                        ),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("同时记入消费账单", style = MaterialTheme.typography.titleMedium)
                        Text("可在账单页继续编辑商户和分类", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = recordExpense, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), category, purchaseMinor ?: 0L, estimatedMinor, recordExpense) },
                enabled = valid,
            ) { Text("保存物品") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddManualQuoteDialog(
    assetName: String,
    currency: CurrencyCode,
    onDismiss: () -> Unit,
    onAdd: (specification: String, condition: ItemCondition, priceMinor: Long, shippingMinor: Long) -> Unit,
) {
    var specification by remember(assetName) { mutableStateOf(assetName) }
    var condition by remember { mutableStateOf(ItemCondition.GOOD) }
    var priceAmount by remember { mutableStateOf("") }
    var shippingAmount by remember { mutableStateOf("") }
    val priceMinor = parseMoneyToMinor(priceAmount)
    val shippingMinor = if (shippingAmount.isBlank()) 0L else parseMoneyToMinor(shippingAmount)
    val priceError = priceAmount.isNotEmpty() && (priceMinor == null || priceMinor <= 0)
    val shippingError = shippingAmount.isNotEmpty() && (shippingMinor == null || shippingMinor < 0)
    val valid = specification.isNotBlank() &&
        specification.length <= 120 &&
        priceMinor != null &&
        priceMinor > 0 &&
        shippingMinor != null &&
        shippingMinor >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加手工二手报价") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "报价仅写入本机，用于更新区间、残值和相关建议；不会访问二手平台，也不会标记为实时行情。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = specification,
                    onValueChange = { specification = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("规格说明（必填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    supportingText = { Text("${specification.length}/120") },
                )
                Text("成色", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ItemCondition.NEW to "全新",
                        ItemCondition.LIKE_NEW to "近新",
                        ItemCondition.GOOD to "良好",
                        ItemCondition.FAIR to "一般",
                        ItemCondition.POOR to "较差",
                    ).forEach { (item, label) ->
                        FilterChip(
                            selected = condition == item,
                            onClick = { condition = item },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = priceAmount,
                    onValueChange = { priceAmount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标价（${currency.value}，必填）") },
                    prefix = { Text(currencyDisplayPrefix(currency.value)) },
                    singleLine = true,
                    isError = priceError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    supportingText = {
                        if (priceError) {
                            Text(if (priceMinor == null) "请输入最多两位小数" else "标价必须大于 0")
                        }
                    },
                )
                OutlinedTextField(
                    value = shippingAmount,
                    onValueChange = { shippingAmount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("预计运费（${currency.value}，可选）") },
                    prefix = { Text(currencyDisplayPrefix(currency.value)) },
                    singleLine = true,
                    isError = shippingError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        if (shippingError) {
                            Text(if (shippingMinor == null) "请输入最多两位小数" else "运费不能小于 0")
                        } else {
                            Text("估值使用标价与运费之和")
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        specification.trim(),
                        condition,
                        priceMinor ?: 0L,
                        shippingMinor ?: 0L,
                    )
                },
                enabled = valid,
            ) { Text("保存报价") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun categoryIdForLabel(label: String): String = when (label) {
    "餐饮" -> "dining"
    "交通" -> "transport"
    "居家" -> "home"
    "数码" -> "digital"
    else -> "other"
}

private fun categoryLabelForId(id: String): String = when (id) {
    "dining" -> "餐饮"
    "transport" -> "交通"
    "home" -> "居家"
    "digital" -> "数码"
    else -> "其他"
}

private fun minorUnitsToInput(minorUnits: Long): String =
    "${minorUnits / 100}.${(minorUnits % 100).toString().padStart(2, '0')}"

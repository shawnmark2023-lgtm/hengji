package com.hengji.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.hengji.app.application.AppLedgerGateway
import com.hengji.app.application.LocalImportFlowPort
import com.hengji.app.application.PersistentAppLedgerGateway
import com.hengji.app.application.PreviewLedgerGateway
import com.hengji.app.application.rememberImportFlowHost
import com.hengji.app.application.UnavailableUserImportDocumentPicker
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.application.LedgerExportWriter
import com.hengji.app.application.PreviewOnlyLedgerExportWriter
import com.hengji.app.importflow.ImportWizard
import com.hengji.app.importflow.ImportDocumentFormat
import com.hengji.app.model.DomainDemoData
import com.hengji.app.model.parseMoneyToMinor
import com.hengji.app.navigation.AppDestination
import com.hengji.app.theme.HengjiTheme
import com.hengji.app.ui.AdaptiveAppShell
import com.hengji.app.ui.screens.AssetsScreen
import com.hengji.app.ui.screens.InsightsScreen
import com.hengji.app.ui.screens.LedgerScreen
import com.hengji.app.ui.screens.OverviewScreen
import com.hengji.app.ui.screens.SettingsScreen
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.LedgerJsonExporter
import com.hengji.data.LedgerCsvExporter
import com.hengji.data.LedgerRepository
import com.hengji.data.LedgerSnapshot
import com.hengji.data.PersistentLedgerRepository
import com.hengji.domain.AssetId
import com.hengji.domain.Asset
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.domain.UsageEvent
import com.hengji.domain.UsageEventId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

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
    HengjiApp(gateway, UnavailableUserImportDocumentPicker, PreviewOnlyLedgerExportWriter)
}

/** Durable platform entry point. All Room access remains coroutine-first and off the UI blocking path. */
@Composable
fun HengjiApp(
    repository: PersistentLedgerRepository,
    userImportDocumentPicker: UserImportDocumentPicker = UnavailableUserImportDocumentPicker,
    ledgerExportWriter: LedgerExportWriter = PreviewOnlyLedgerExportWriter,
) {
    val gateway = remember(repository) { PersistentAppLedgerGateway(repository) }
    HengjiApp(gateway, userImportDocumentPicker, ledgerExportWriter)
}

@Composable
fun HengjiApp(
    gateway: AppLedgerGateway,
    userImportDocumentPicker: UserImportDocumentPicker = UnavailableUserImportDocumentPicker,
    ledgerExportWriter: LedgerExportWriter = PreviewOnlyLedgerExportWriter,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Overview) }
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showAddTransaction by rememberSaveable { mutableStateOf(false) }
    var showAddAsset by rememberSaveable { mutableStateOf(false) }
    var showImportWizard by rememberSaveable { mutableStateOf(false) }
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportPreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var dataActionStatus by remember { mutableStateOf<String?>(null) }
    var snapshot by remember(gateway) { mutableStateOf<LedgerSnapshot?>(null) }
    var storageBusy by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val importPort = remember(gateway, userImportDocumentPicker) {
        LocalImportFlowPort(gateway, userImportDocumentPicker)
    }
    val importHost = rememberImportFlowHost(importPort) {
        scope.launch {
            snapshot = gateway.snapshot()
        }
    }

    LaunchedEffect(gateway) {
        storageBusy = true
        try {
            var loaded = gateway.snapshot()
            val pristine = loaded.revision == 0L && loaded.transactions.isEmpty() && loaded.assets.isEmpty()
            if (pristine) {
                gateway.replaceWith(DomainDemoData.initialSnapshot)
                loaded = gateway.snapshot()
            }
            snapshot = loaded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            storageError = error.message ?: "无法打开本机账本"
        } finally {
            storageBusy = false
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
            } catch (error: Throwable) {
                storageError = error.message ?: "本机账本操作未完成"
            } finally {
                storageBusy = false
            }
        }
    }

    val currentSnapshot = snapshot
    val today = remember { currentLocalDate() }
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()

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
    val insights = remember(currentSnapshot, today) { DomainDemoData.insights(currentSnapshot, today) }

    HengjiTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AdaptiveAppShell(
                destination = destination,
                onDestinationChange = {
                    showImportWizard = false
                    destination = it
                },
                onAddTransaction = { showAddTransaction = true },
            ) {
                if (showImportWizard) {
                    ImportWizard(state = importHost.state, onEvent = importHost.dispatch)
                } else when (destination) {
                    AppDestination.Overview -> OverviewScreen(
                        transactions = transactions,
                        assets = assets,
                        insights = insights,
                        asOf = today,
                        onOpenLedger = { destination = AppDestination.Ledger },
                        onOpenInsights = { destination = AppDestination.Insights },
                    )
                    AppDestination.Ledger -> LedgerScreen(
                        transactions = transactions,
                        onAddTransaction = { showAddTransaction = true },
                        onEditTransaction = { editingTransactionId = it },
                    )
                    AppDestination.Assets -> AssetsScreen(
                        assets = assets,
                        onAddAsset = { showAddAsset = true },
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
                    AppDestination.Insights -> InsightsScreen(insights = insights)
                    AppDestination.Settings -> SettingsScreen(
                        darkTheme = darkTheme,
                        onDarkThemeChange = { darkThemeOverride = it },
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
                                    exportPreview = "CSV 流水导出" to csv
                                    dataActionStatus = "已生成当前流水的 CSV 导出内容"
                                } else {
                                    dataActionStatus = "流水已导出到 $location"
                                }
                            }
                        },
                        onRestoreData = {
                            mutate {
                                val picked = userImportDocumentPicker.pick(ImportDocumentFormat.Json)
                                if (picked != null) {
                                    val restored = LedgerJsonExporter.restore(picked.content)
                                    gateway.replaceWith(restored)
                                    dataActionStatus = "已从 ${picked.displayName} 恢复本机账本"
                                }
                            }
                        },
                        onClearData = { confirmClear = true },
                        onOpenImport = { showImportWizard = true },
                        storageStatus = if (gateway is PersistentAppLedgerGateway) {
                            "SQLite 已跨重启持久化 · 当前开发构建未启用应用层加密"
                        } else {
                            "内存预览 · 关闭后不保留"
                        },
                    )
                }
            }
        }

        val editingTransaction = editingTransactionId?.let { id ->
            currentSnapshot.transactions.firstOrNull { it.id.value == id }
        }
        if (showAddTransaction || editingTransaction != null) {
            AddTransactionDialog(
                title = if (editingTransaction == null) "记一笔消费" else "编辑流水",
                initialMerchant = editingTransaction?.merchant?.displayName.orEmpty(),
                initialAmount = editingTransaction?.amount?.minorUnits?.let(::minorUnitsToInput).orEmpty(),
                initialCategory = editingTransaction?.categoryId?.value?.let(::categoryLabelForId) ?: "餐饮",
                onDismiss = {
                    showAddTransaction = false
                    editingTransactionId = null
                },
                onAdd = { merchant, category, amountMinor ->
                    mutate {
                        val updated = editingTransaction?.copy(
                            merchant = Merchant(merchant),
                            categoryId = CategoryId(categoryIdForLabel(category)),
                            amount = Money(amountMinor, editingTransaction.amount.currency),
                        ) ?: Transaction(
                            id = TransactionId("local-${currentSnapshot.revision + 1}"),
                            kind = TransactionKind.EXPENSE,
                            amount = Money(amountMinor, CurrencyCode.CNY),
                            bookedOn = currentLocalDate(),
                            categoryId = CategoryId(categoryIdForLabel(category)),
                            merchant = Merchant(merchant),
                            source = TransactionSource.MANUAL,
                        )
                        gateway.upsertTransaction(updated)
                        showAddTransaction = false
                        editingTransactionId = null
                        destination = AppDestination.Ledger
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
                                "这是交易流水表格内容；金额使用整数最小单位，避免小数精度丢失。"
                            } else {
                                "这是完整的本机账本备份内容，可用于恢复当前数据。"
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
                text = { Text("此操作会删除流水、物品、使用记录和本机估值。建议先导出；清除后不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            mutate {
                                gateway.clear()
                                confirmClear = false
                                dataActionStatus = "本机账本已清除"
                            }
                        },
                    ) { Text("确认清除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("取消") }
                },
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
    onDismiss: () -> Unit,
    onAdd: (merchant: String, category: String, amountMinor: Long) -> Unit,
) {
    var merchant by remember(initialMerchant) { mutableStateOf(initialMerchant) }
    var amount by remember(initialAmount) { mutableStateOf(initialAmount) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    val amountMinor = parseMoneyToMinor(amount)
    val valid = merchant.isNotBlank() && amountMinor != null && amountMinor > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "无需登录，仅写入本机账本，不会上传。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("商户或用途") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    supportingText = {
                        if (amount.isNotEmpty() && amountMinor == null) Text("请输入最多两位小数")
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
                onClick = { onAdd(merchant.trim(), category, amountMinor ?: 0L) },
                enabled = valid,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    val valid = name.isNotBlank() && purchaseMinor != null && purchaseMinor > 0 &&
        (estimatedAmount.isBlank() || estimatedMinor != null && estimatedMinor >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增物品") },
        text = {
            androidx.compose.foundation.layout.Column(
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
                    label = { Text("产品名称") },
                    singleLine = true,
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
                    label = { Text("购买价格") },
                    prefix = { Text("¥") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = estimatedAmount,
                    onValueChange = { estimatedAmount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("当前手工估值（可选）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("同时记入消费流水", style = MaterialTheme.typography.titleMedium)
                        Text("可在流水页继续编辑商户和分类", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = recordExpense, onCheckedChange = { recordExpense = it })
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

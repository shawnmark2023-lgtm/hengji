package com.hengji.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hengji.app.model.DemoTransaction
import com.hengji.app.model.DomainDemoData
import com.hengji.app.model.EntryKind
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
import com.hengji.domain.AssetId
import com.hengji.domain.CategoryId
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.domain.UsageEvent
import com.hengji.domain.UsageEventId
import kotlinx.datetime.LocalDate

@Composable
fun HengjiApp() {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Overview) }
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showAddTransaction by rememberSaveable { mutableStateOf(false) }
    val repository = remember { InMemoryLedgerRepository(DomainDemoData.initialSnapshot) }
    var ledgerRevision by remember { mutableStateOf(repository.snapshot().revision) }
    val snapshot = remember(ledgerRevision) { repository.snapshot() }
    val transactions = remember(ledgerRevision) { DomainDemoData.transactions(snapshot) }
    val assets = remember(ledgerRevision) { DomainDemoData.assets(snapshot) }
    val insights = remember(ledgerRevision) { DomainDemoData.insights(snapshot) }
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()

    HengjiTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AdaptiveAppShell(
                destination = destination,
                onDestinationChange = { destination = it },
                onAddTransaction = { showAddTransaction = true },
            ) {
                when (destination) {
                    AppDestination.Overview -> OverviewScreen(
                        transactions = transactions,
                        assets = assets,
                        insights = insights,
                        onOpenLedger = { destination = AppDestination.Ledger },
                        onOpenInsights = { destination = AppDestination.Insights },
                    )
                    AppDestination.Ledger -> LedgerScreen(
                        transactions = transactions,
                        onAddTransaction = { showAddTransaction = true },
                    )
                    AppDestination.Assets -> AssetsScreen(
                        assets = assets,
                        onRecordUsage = { assetId ->
                            repository.addUsageEvent(
                                UsageEvent(
                                    id = UsageEventId("local-usage-${repository.snapshot().revision + 1}"),
                                    assetId = AssetId(assetId),
                                    occurredOn = LocalDate(2026, 7, 15),
                                ),
                            )
                            ledgerRevision = repository.snapshot().revision
                        },
                    )
                    AppDestination.Insights -> InsightsScreen(insights = insights)
                    AppDestination.Settings -> SettingsScreen(
                        darkTheme = darkTheme,
                        onDarkThemeChange = { darkThemeOverride = it },
                    )
                }
            }
        }

        if (showAddTransaction) {
            AddTransactionDialog(
                onDismiss = { showAddTransaction = false },
                onAdd = { merchant, category, amountMinor ->
                    repository.upsertTransaction(
                        Transaction(
                            id = TransactionId("local-${repository.snapshot().revision + 1}"),
                            kind = TransactionKind.EXPENSE,
                            amount = Money(amountMinor, DomainDemoData.initialSnapshot.transactions.first().amount.currency),
                            bookedOn = LocalDate(2026, 7, 15),
                            categoryId = CategoryId(
                                when (category) {
                                    "餐饮" -> "dining"
                                    "交通" -> "transport"
                                    "居家" -> "home"
                                    "数码" -> "digital"
                                    else -> "other"
                                },
                            ),
                            merchant = Merchant(merchant),
                            source = TransactionSource.MANUAL,
                        ),
                    )
                    ledgerRevision = repository.snapshot().revision
                    showAddTransaction = false
                    destination = AppDestination.Ledger
                },
            )
        }
    }
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onAdd: (merchant: String, category: String, amountMinor: Long) -> Unit,
) {
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("餐饮") }
    val amountMinor = parseMoneyToMinor(amount)
    val valid = merchant.isNotBlank() && amountMinor != null && amountMinor > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔消费") },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "无需登录，只加入当前本机会话，不会上传。",
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

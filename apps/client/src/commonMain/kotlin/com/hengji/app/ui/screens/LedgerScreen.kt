package com.hengji.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hengji.app.model.DemoTransaction
import com.hengji.app.model.EntryKind
import com.hengji.app.model.formatMoney
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.SectionCard
import com.hengji.app.ui.components.StatusPill
import com.hengji.domain.ExactMath

@Composable
fun LedgerScreen(
    transactions: List<DemoTransaction>,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {},
    onOpenImport: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var selectedKind by remember { mutableStateOf<EntryKind?>(null) }
    val categories = listOf("全部") + transactions.map { it.category }.distinct().sorted()
    val filtered = transactions.filter { transaction ->
        (selectedCategory == "全部" || transaction.category == selectedCategory) &&
            (selectedKind == null || transaction.kind == selectedKind) &&
            (query.isBlank() || transaction.merchant.contains(query, ignoreCase = true) ||
                transaction.category.contains(query, ignoreCase = true) ||
                transaction.sourceLabel.contains(query, ignoreCase = true))
    }
    val netSpend = filtered
        .filter { it.kind == EntryKind.Expense || it.kind == EntryKind.Refund }
        .fold(0L) { total, item -> ExactMath.add(total, item.amountMinor) }
    val income = filtered
        .filter { it.kind == EntryKind.Income }
        .fold(0L) { total, item -> ExactMath.add(total, item.amountMinor) }
    val balance = ExactMath.subtract(income, netSpend)
    val grouped = filtered.sortedByDescending { it.bookedOn }.groupBy { it.bookedOn }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.lg),
    ) {
        item {
            ScreenHeader(
                eyebrow = "本机账本 · ${transactions.size} 笔记录",
                title = "账单",
                supporting = "搜索、核对和修正每笔收支；导入记录始终保留来源。",
                action = {
                    FilledTonalButton(onClick = onAddTransaction) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(HengjiSpacing.xs))
                        Text("新增")
                    }
                },
            )
        }

        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索商户、用途或分类") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.xs),
                    ) {
                        listOf(
                            null to "全部收支",
                            EntryKind.Expense to "支出",
                            EntryKind.Income to "收入",
                            EntryKind.Refund to "退款",
                        ).forEach { item ->
                            FilterChip(
                                selected = selectedKind == item.first,
                                onClick = { selectedKind = item.first },
                                label = { Text(item.second) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.xs),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs),
                    ) {
                        Text(
                            "筛选结果 ${filtered.size} 笔",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LedgerSummary(netSpend, income, balance)
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                SectionCard(Modifier.fillMaxWidth()) {
                    val isEmptyLedger = transactions.isEmpty() && query.isBlank() &&
                        selectedCategory == "全部" && selectedKind == null
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (isEmptyLedger) "还没有账单" else "没有找到这笔账",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(HengjiSpacing.xs))
                        Text(
                            if (isEmptyLedger) "手动记一笔，或导入已经有的账单。" else "调整收支类型、分类或搜索词后重试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isEmptyLedger) {
                            Spacer(Modifier.height(HengjiSpacing.md))
                            FilledTonalButton(onClick = onAddTransaction) { Text("记第一笔") }
                            TextButton(onClick = onOpenImport) { Text("导入旧账单") }
                        }
                    }
                }
            }
        } else {
            grouped.forEach { (_, dateTransactions) ->
                item {
                    Text(
                        dateTransactions.first().dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column {
                            dateTransactions.forEachIndexed { index, transaction ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LedgerRow(
                                    transaction = transaction,
                                    onEdit = { onEditTransaction(transaction.id) },
                                    onDelete = { onDeleteTransaction(transaction.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerSummary(netSpend: Long, income: Long, balance: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("净支出 ${formatMoney(netSpend)}", style = MaterialTheme.typography.titleMedium)
                Text("收入 ${formatMoney(income)} · 结余 ${formatMoney(balance, showSign = true)}")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("净支出 ${formatMoney(netSpend)}", style = MaterialTheme.typography.titleMedium)
                Text("收入 ${formatMoney(income)} · 结余 ${formatMoney(balance, showSign = true)}")
            }
        }
    }
}

@Composable
private fun LedgerRow(
    transaction: DemoTransaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "编辑这笔账",
                onClick = onEdit,
            )
            .padding(vertical = HengjiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (transaction.kind != EntryKind.Expense) {
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    StatusPill(
                        text = if (transaction.kind == EntryKind.Refund) "退款" else "收入",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                transaction.category,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                transaction.sourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(HengjiSpacing.md))
        Text(
            text = formatMoney(transaction.amountMinor, showSign = transaction.kind == EntryKind.Income),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.kind == EntryKind.Expense) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Spacer(Modifier.width(HengjiSpacing.xs))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除 ${transaction.merchant} 账单",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

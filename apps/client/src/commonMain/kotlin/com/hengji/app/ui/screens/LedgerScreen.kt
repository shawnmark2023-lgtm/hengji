package com.hengji.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
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

@Composable
fun LedgerScreen(
    transactions: List<DemoTransaction>,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    val filtered = transactions.filter { transaction ->
        (selectedCategory == "全部" || transaction.category == selectedCategory) &&
            (query.isBlank() || transaction.merchant.contains(query, ignoreCase = true) ||
                transaction.category.contains(query, ignoreCase = true))
    }
    val netSpend = filtered.sumOf {
        when (it.kind) {
            EntryKind.Expense, EntryKind.Refund -> it.amountMinor
            EntryKind.Income -> 0L
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.lg),
    ) {
        item {
            ScreenHeader(
                eyebrow = "本机账本 · ${transactions.size} 笔记录",
                title = "流水",
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
                        placeholder = { Text("搜索商户、用途或分类") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.xs),
                    ) {
                        listOf("全部", "餐饮", "交通", "居家", "数码", "数码服务").forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "筛选结果 ${filtered.size} 笔",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "净额 ${formatMoney(netSpend)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    if (filtered.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("没有匹配的流水", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(HengjiSpacing.xs))
                            Text(
                                "调整分类或搜索词后重试。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        filtered.forEachIndexed { index, transaction ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LedgerRow(transaction, onClick = { onEditTransaction(transaction.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(transaction: DemoTransaction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = HengjiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (transaction.kind == EntryKind.Refund) {
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    StatusPill(
                        text = "退款",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${transaction.category} · ${transaction.dateLabel}",
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
            text = formatMoney(transaction.amountMinor),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.amountMinor < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

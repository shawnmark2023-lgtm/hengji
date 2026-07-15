package com.hengji.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hengji.app.model.DemoAsset
import com.hengji.app.model.formatMoney
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.MetricCard
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.StatusPill

@Composable
fun AssetsScreen(
    assets: List<DemoAsset>,
    onRecordUsage: (String) -> Unit,
    onAddAsset: () -> Unit = {},
) {
    var selectedAsset by remember { mutableStateOf<DemoAsset?>(null) }
    val totalCost = assets.sumOf { it.totalCostMinor }
    val currentValue = assets.sumOf { it.currentValueMinor }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(HengjiSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader(
                eyebrow = "${assets.size} 件正在使用",
                title = "物品",
                supporting = "从购买价格走到真实使用成本，再看清当下残值。",
                action = {
                    FilledTonalButton(onClick = onAddAsset) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(HengjiSpacing.xs))
                        Text("新增物品")
                    }
                },
            )
        }
        item {
            MetricCard(
                label = "累计投入",
                value = formatMoney(totalCost),
                supporting = "购买价 + 维护费用",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            MetricCard(
                label = "当前估值",
                value = formatMoney(currentValue),
                supporting = "手工与示例报价中位数",
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.secondary,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = HengjiSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text("我的物品", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "点开物品查看四种成本与二手区间",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill("行情均为示例 / 手工")
            }
        }
        items(assets, key = { it.id }) { asset ->
            AssetCard(asset = asset, onClick = { selectedAsset = asset })
        }
    }

    selectedAsset?.let { asset ->
        AssetDetailDialog(
            asset = asset,
            onRecordUsage = { onRecordUsage(asset.id) },
            onDismiss = { selectedAsset = null },
        )
    }
}

@Composable
private fun AssetCard(
    asset: DemoAsset,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(HengjiSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        asset.variant,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(HengjiSpacing.sm))
                StatusPill("${asset.marketConfidence}%")
            }
            Spacer(Modifier.height(HengjiSpacing.lg))
            Text("净日均成本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(asset.netDailyCostMinor), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(HengjiSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(HengjiSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("每次使用", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(asset.costPerUseMinor), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("当前残值", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(asset.currentValueMinor), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(HengjiSpacing.md))
            Text(
                "示例区间 ${formatMoney(asset.marketLowMinor)}–${formatMoney(asset.marketHighMinor).removePrefix("¥")}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                asset.quoteUpdatedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssetDetailDialog(
    asset: DemoAsset,
    onRecordUsage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var usageRecorded by remember(asset.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(asset.name)
                Text(
                    asset.variant,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                DetailMetric("总拥有成本", formatMoney(asset.totalCostMinor), "购买与维护累计")
                DetailMetric("日均拥有成本", formatMoney(asset.dailyCostMinor), "已拥有 ${asset.ownedDays} 天")
                DetailMetric("净日均成本", formatMoney(asset.netDailyCostMinor), "扣除当前残值")
                DetailMetric("单次使用成本", formatMoney(asset.costPerUseMinor), "记录 ${asset.usageCount} 次使用")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("二手市场比价", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatMoney(asset.marketLowMinor)} – ${formatMoney(asset.marketHighMinor)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                LinearProgressIndicator(
                    progress = { asset.marketConfidence / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "置信度 ${asset.marketConfidence}% · ${asset.quoteUpdatedLabel}。此价格不是实时行情。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        onRecordUsage()
                        usageRecorded = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !usageRecorded,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text(if (usageRecorded) "已记录本次使用" else "记录一次使用")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun DetailMetric(label: String, value: String, supporting: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

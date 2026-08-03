package com.hengji.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hengji.app.application.SaleTargetStatus
import com.hengji.app.model.DemoAsset
import com.hengji.app.model.currencyDisplayPrefix
import com.hengji.app.model.formatMoney
import com.hengji.app.model.parseMoneyToMinor
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.MetricCard
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.StatusPill

@Composable
fun AssetsScreen(
    assets: List<DemoAsset>,
    onRecordUsage: (String) -> Unit,
    onAddManualQuote: (String) -> Unit,
    onSaleTargetChange: (assetId: String, targetPriceMinor: Long?) -> Unit,
    onAddAsset: () -> Unit = {},
) {
    var selectedAsset by remember { mutableStateOf<DemoAsset?>(null) }
    var editingSaleTargetAsset by remember { mutableStateOf<DemoAsset?>(null) }
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
            onAddManualQuote = {
                selectedAsset = null
                onAddManualQuote(asset.id)
            },
            onEditSaleTarget = {
                selectedAsset = null
                editingSaleTargetAsset = asset
            },
            onDismiss = { selectedAsset = null },
        )
    }
    editingSaleTargetAsset?.let { asset ->
        SaleTargetDialog(
            asset = asset,
            onDismiss = { editingSaleTargetAsset = null },
            onSave = { targetPriceMinor ->
                onSaleTargetChange(asset.id, targetPriceMinor)
                editingSaleTargetAsset = null
            },
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
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        asset.variant,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(HengjiSpacing.sm))
                StatusPill("${asset.marketConfidence}%")
            }
            Spacer(Modifier.height(HengjiSpacing.lg))
            Text("净日均成本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(asset.formatMoney(asset.netDailyCostMinor), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(HengjiSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(HengjiSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("每次使用", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(asset.formatMoney(asset.costPerUseMinor), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("当前残值", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(asset.formatMoney(asset.currentValueMinor), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(HengjiSpacing.md))
            Text(
                "估值区间 ${asset.formatMoney(asset.marketLowMinor)} – ${asset.formatMoney(asset.marketHighMinor)}",
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
    onAddManualQuote: () -> Unit,
    onEditSaleTarget: () -> Unit,
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
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
            ) {
                DetailMetric("总拥有成本", asset.formatMoney(asset.totalCostMinor), "购买与维护累计")
                DetailMetric("日均拥有成本", asset.formatMoney(asset.dailyCostMinor), "已拥有 ${asset.ownedDays} 天")
                DetailMetric("净日均成本", asset.formatMoney(asset.netDailyCostMinor), "扣除当前残值")
                DetailMetric("单次使用成本", asset.formatMoney(asset.costPerUseMinor), "记录 ${asset.usageCount} 次使用")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("二手市场比价", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${asset.formatMoney(asset.marketLowMinor)} – ${asset.formatMoney(asset.marketHighMinor)}",
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
                Text(
                    if (asset.marketMedianMinor == null) {
                        "已有 ${asset.quoteCount} 条报价；样本或置信度不足，仅展示区间，不把单点当作市场价。"
                    } else {
                        "已有 ${asset.quoteCount} 条报价；可呈现中位数 ${asset.formatMoney(asset.marketMedianMinor)}。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SaleTargetSection(asset = asset, onEdit = onEditSaleTarget)
                FilledTonalButton(
                    onClick = onAddManualQuote,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text("添加手工二手报价")
                }
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
private fun SaleTargetSection(
    asset: DemoAsset,
    onEdit: () -> Unit,
) {
    val projection = asset.saleTarget
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("出售目标价", style = MaterialTheme.typography.titleMedium)
        StatusPill(saleTargetStatusLabel(projection.status))
    }
    projection.targetPriceMinor?.let { target ->
        Text(
            "目标 ${asset.formatMoney(target)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
    Text(
        saleTargetSupportingText(asset),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "仅在你打开恒迹时根据本机报价更新；不会发送系统通知，也不会在后台联网。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (projection.status == SaleTargetStatus.NOT_SET) "设置出售目标价" else "修改出售目标价")
    }
}

@Composable
private fun SaleTargetDialog(
    asset: DemoAsset,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    var amount by remember(asset.id) {
        mutableStateOf(asset.saleTarget.targetPriceMinor?.let(::minorUnitsToInput).orEmpty())
    }
    val targetMinor = parseMoneyToMinor(amount)
    val invalid = amount.isNotEmpty() && (targetMinor == null || targetMinor <= 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (asset.saleTarget.targetPriceMinor == null) "设置出售目标价" else "修改出售目标价") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                Text(
                    "目标价使用物品的购买币种 ${asset.currencyCode}，金额会按最小货币单位精确保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("出售目标价") },
                    prefix = { Text(currencyDisplayPrefix(asset.currencyCode)) },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        Text(
                            when {
                                invalid && targetMinor == null -> "请输入最多两位小数"
                                invalid -> "目标价必须大于 0"
                                else -> "示例报价不会触发“已达到”状态"
                            },
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(targetMinor) },
                enabled = targetMinor != null && targetMinor > 0,
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (asset.saleTarget.targetPriceMinor != null) {
                    TextButton(onClick = { onSave(null) }) { Text("清除目标价") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun saleTargetStatusLabel(status: SaleTargetStatus): String = when (status) {
    SaleTargetStatus.NOT_SET -> "未设置"
    SaleTargetStatus.WAITING -> "等待达到"
    SaleTargetStatus.REACHED -> "已达到"
    SaleTargetStatus.INSUFFICIENT_SAMPLE -> "样本不足"
    SaleTargetStatus.STALE_QUOTES -> "报价过期"
    SaleTargetStatus.DEMO_ONLY -> "仅示例不可触发"
}

private fun saleTargetSupportingText(asset: DemoAsset): String {
    val target = asset.saleTarget
    return when (target.status) {
        SaleTargetStatus.NOT_SET -> "设置后可在应用内查看可信二手报价是否达到预期。"
        SaleTargetStatus.WAITING -> "可信非示例报价中位数 ${
            target.observedMedianMinor?.let(asset::formatMoney) ?: "暂不可用"
        }，尚未达到目标。${staleQuoteSuffix(target.rejectedStaleQuoteCount)}"
        SaleTargetStatus.REACHED -> "可信非示例报价中位数 ${
            target.observedMedianMinor?.let(asset::formatMoney) ?: "暂不可用"
        }，已经达到目标。${staleQuoteSuffix(target.rejectedStaleQuoteCount)}"
        SaleTargetStatus.INSUFFICIENT_SAMPLE -> "非示例报价样本或置信度不足，暂不判断是否达到目标。"
        SaleTargetStatus.STALE_QUOTES -> "现有非示例报价均已超过 90 天，需要补充较新的报价。"
        SaleTargetStatus.DEMO_ONLY -> "当前只有明确标注的示例报价；示例数据不会触发目标价。"
    }
}

private fun staleQuoteSuffix(count: Int): String =
    if (count > 0) " 已忽略 $count 条超过 90 天的报价。" else ""

private fun DemoAsset.formatMoney(minorUnits: Long): String =
    formatMoney(minorUnits = minorUnits, currencyCode = currencyCode)

private fun minorUnitsToInput(minorUnits: Long): String =
    "${minorUnits / 100}.${(minorUnits % 100).toString().padStart(2, '0')}"

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

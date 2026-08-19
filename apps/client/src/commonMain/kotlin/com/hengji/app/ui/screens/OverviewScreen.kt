package com.hengji.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hengji.app.model.DemoInsight
import com.hengji.app.model.DemoAsset
import com.hengji.app.model.DemoTransaction
import com.hengji.app.model.EntryKind
import com.hengji.app.model.formatMoney
import com.hengji.app.theme.HengjiGreenLight
import com.hengji.app.theme.HengjiApricot
import com.hengji.app.theme.HengjiSpacing
import com.hengji.domain.ExactMath
import com.hengji.app.ui.components.CategoryProgress
import com.hengji.app.ui.components.LocalOnlyBadge
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.SectionCard
import com.hengji.app.ui.components.StatusPill
import kotlinx.datetime.LocalDate

@Composable
fun OverviewScreen(
    transactions: List<DemoTransaction>,
    assets: List<DemoAsset>,
    insights: List<DemoInsight>,
    asOf: LocalDate,
    onAddTransaction: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenInsights: () -> Unit,
    monthlyBudgetMinor: Long? = null,
    onEditMonthlyBudget: () -> Unit = {},
) {
    val currentTransactions = transactions.filter { it.inCurrentPeriod }
    val spend = currentTransactions
        .filter { it.kind == EntryKind.Expense || it.kind == EntryKind.Refund }
        .fold(0L) { total, item -> ExactMath.add(total, item.amountMinor) }
        .coerceAtLeast(0)
    val income = currentTransactions
        .filter { it.kind == EntryKind.Income }
        .fold(0L) { total, item -> ExactMath.add(total, item.amountMinor) }
    val balance = ExactMath.subtract(income, spend)
    val residualValue = assets.fold(0L) { total, item -> ExactMath.add(total, item.currentValueMinor) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.lg),
    ) {
        item {
            ScreenHeader(
                eyebrow = "${asOf.year} 年 ${asOf.month.ordinal + 1} 月 · 第 ${(asOf.day - 1) / 7 + 1} 周",
                title = "本月收支",
                supporting = "支出、收入和预算进度，都按已确认账单计算。",
                action = { LocalOnlyBadge() },
            )
        }

        if (transactions.isEmpty() && assets.isEmpty()) {
            item {
                FirstRunGuide(
                    onAddTransaction = onAddTransaction,
                    onOpenImport = onOpenImport,
                )
            }
        }

        if (transactions.isNotEmpty() || assets.isNotEmpty()) {
            item {
                MonthlyOverviewCard(
                    spendMinor = spend,
                    incomeMinor = income,
                    balanceMinor = balance,
                    monthlyBudgetMinor = monthlyBudgetMinor,
                    residualValueMinor = residualValue.takeIf { assets.isNotEmpty() },
                    onEditMonthlyBudget = onEditMonthlyBudget,
                )
            }

            item {
                BoxWithConstraints {
                    val wide = maxWidth >= 820.dp
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            SpendingComposition(currentTransactions, modifier = Modifier.weight(1.08f))
                            InsightPreview(insights.firstOrNull(), onOpenInsights, modifier = Modifier.weight(0.92f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                            SpendingComposition(currentTransactions, Modifier.fillMaxWidth())
                            InsightPreview(insights.firstOrNull(), onOpenInsights, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("最近记的账", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "最近发生的收支与来源",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = onOpenLedger) { Text("查看全部") }
                        }
                        Spacer(Modifier.height(HengjiSpacing.sm))
                        currentTransactions.take(4).forEachIndexed { index, transaction ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            OverviewTransactionRow(transaction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyOverviewCard(
    spendMinor: Long,
    incomeMinor: Long,
    balanceMinor: Long,
    monthlyBudgetMinor: Long?,
    residualValueMinor: Long?,
    onEditMonthlyBudget: () -> Unit,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("本月概览", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "退款已从支出中扣除",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEditMonthlyBudget) {
                    Text(if (monthlyBudgetMinor == null) "设置预算" else "修改预算")
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 620.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
                        SummaryValue("支出", formatMoney(spendMinor), Modifier.fillMaxWidth())
                        SummaryValue("收入", formatMoney(incomeMinor), Modifier.fillMaxWidth())
                        SummaryValue("结余", formatMoney(balanceMinor, showSign = true), Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.lg),
                    ) {
                        SummaryValue("支出", formatMoney(spendMinor), Modifier.weight(1f))
                        SummaryValue("收入", formatMoney(incomeMinor), Modifier.weight(1f))
                        SummaryValue("结余", formatMoney(balanceMinor, showSign = true), Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (monthlyBudgetMinor == null) {
                Text("设置月预算后，这里会显示真实可用额度和消费进度。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onEditMonthlyBudget) { Text("设置月预算") }
            } else {
                val remaining = ExactMath.subtract(monthlyBudgetMinor, spendMinor)
                val usedPercent = ((spendMinor.toDouble() / monthlyBudgetMinor) * 100).toInt().coerceAtLeast(0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("月预算 ${formatMoney(monthlyBudgetMinor)}", style = MaterialTheme.typography.labelLarge)
                    Text("已用 $usedPercent%", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { (spendMinor.toFloat() / monthlyBudgetMinor).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (remaining >= 0) {
                        "还可安排 ${formatMoney(remaining)}"
                    } else {
                        "已超出 ${formatMoney(ExactMath.negate(remaining))}，可以先复核非必要支出。"
                    },
                    color = if (remaining >= 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            residualValueMinor?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("物品当前残值", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "基于明确标注的手工与示例报价",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatMoney(it), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun FirstRunGuide(
    onAddTransaction: () -> Unit,
    onOpenImport: () -> Unit,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs)) {
                Text("先记第一笔，就能开始", style = MaterialTheme.typography.titleLarge)
                Text(
                    "不用注册，也不用先设置。手动记一笔，或把旧账单导进来，恒迹就会开始整理。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BoxWithConstraints {
                if (maxWidth >= 720.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.xl),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1.25f),
                            verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
                        ) {
                            FirstRunSteps()
                        }
                        Column(
                            modifier = Modifier.weight(0.75f),
                            verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
                        ) {
                            FirstRunActions(onAddTransaction, onOpenImport)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                        FirstRunSteps()
                        FirstRunActions(onAddTransaction, onOpenImport)
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRunSteps() {
    GuideStep("1", "记下或导入", "填金额和用途，也可以读取图片、PDF 或长截图。")
    GuideStep("2", "核对后保存", "所有识别结果都先预览，确认后才进入账本。")
    GuideStep("3", "用得越久越懂你", "满三个月后给出首次个人分析，随时可以关闭。")
}

@Composable
private fun FirstRunActions(
    onAddTransaction: () -> Unit,
    onOpenImport: () -> Unit,
) {
    Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth()) { Text("记第一笔") }
    FilledTonalButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) { Text("导入旧账单") }
    Text(
        "文件只在本机读取，确认后才入账。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GuideStep(number: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(HengjiSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpendingComposition(transactions: List<DemoTransaction>, modifier: Modifier) {
    val expenses = transactions.filter { it.kind == EntryKind.Expense }
    val total = expenses.sumOf { it.amountMinor }.coerceAtLeast(1)
    val breakdown = expenses.groupBy { it.category }
        .mapValues { (_, items) -> items.sumOf { it.amountMinor } }
        .entries
        .sortedByDescending { it.value }
        .take(5)
    val colors = listOf(HengjiGreenLight, HengjiApricot, Color(0xFF6D8AA2), Color(0xFF8A78A2))
    SectionCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("支出占比", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "按已确认分类计算",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill("${breakdown.size} 类已确认")
            }
            breakdown.forEachIndexed { index, item ->
                val share = item.value * 100 / total
                CategoryProgress(
                    name = item.key,
                    amount = "${formatMoney(item.value)} · $share%",
                    fraction = item.value.toFloat() / total.toFloat(),
                    color = colors[index % colors.size],
                )
            }
            if (breakdown.isEmpty()) {
                Text("本月还没有支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InsightPreview(
    insight: DemoInsight?,
    onOpenInsights: () -> Unit,
    modifier: Modifier,
) {
    SectionCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(HengjiSpacing.xs))
                Text("值得留意", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (insight == null) {
                Text("暂无需要处理的建议", style = MaterialTheme.typography.titleLarge)
                Text(
                    "继续记账或记录物品使用，三个月后这里会出现你的专属分析。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(insight.title, style = MaterialTheme.typography.titleLarge)
            Text(
                insight.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("依据", style = MaterialTheme.typography.labelLarge)
            Text(
                insight.evidence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { insight.confidence / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "置信度 ${insight.confidence}% · 预计可优化 ${formatMoney(insight.impactMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text("查看依据与行动")
            }
        }
    }
}

@Composable
private fun OverviewTransactionRow(transaction: DemoTransaction) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HengjiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                transaction.merchant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${transaction.category} · ${transaction.dateLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(HengjiSpacing.md))
        Text(
            formatMoney(transaction.amountMinor, showSign = transaction.kind == EntryKind.Income),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.kind == EntryKind.Expense) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
            textAlign = TextAlign.End,
        )
    }
}

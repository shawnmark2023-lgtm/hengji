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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hengji.app.model.DemoInsight
import com.hengji.app.model.InsightPriority
import com.hengji.app.model.formatMoney
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.SectionCard
import com.hengji.app.ui.components.StatusPill
import com.hengji.insights.InsightFeedback

@Composable
fun InsightsScreen(
    insights: List<DemoInsight>,
    busyDeduplicationKey: String?,
    isResetting: Boolean,
    reduceMotion: Boolean = false,
    statusMessage: String?,
    onFeedback: (deduplicationKey: String, feedback: InsightFeedback) -> Unit,
    onResetFeedback: () -> Unit,
) {
    val totalImpact = insights.sumOf { it.impactMinor }
    val interactionLocked = busyDeduplicationKey != null || isResetting
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item {
            ScreenHeader(
                eyebrow = "确定性规则 · 本机计算",
                title = "洞察",
                supporting = "每条建议都说明依据、影响和置信度；你始终拥有最终决定权。",
                action = {
                    TextButton(
                        onClick = { showResetConfirmation = true },
                        enabled = !interactionLocked,
                    ) {
                        if (isResetting && !reduceMotion) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(HengjiSpacing.xs))
                        }
                        Text(if (isResetting) "正在恢复…" else "恢复默认")
                    }
                },
            )
        }
        statusMessage?.let { message ->
            item {
                SectionCard(
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("本月可优化空间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(totalImpact), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "估算值，不等同于保证节省",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill("${insights.size} 条建议")
                }
            }
        }
        itemsIndexed(
            items = insights,
            key = { _, insight -> insight.deduplicationKey },
        ) { index, insight ->
            InsightCard(
                index = index,
                insight = insight,
                busy = busyDeduplicationKey == insight.deduplicationKey,
                interactionEnabled = !interactionLocked,
                reduceMotion = reduceMotion,
                onFeedback = { feedback ->
                    onFeedback(insight.deduplicationKey, feedback)
                },
            )
        }
        item {
            Text(
                "衡记不提供投资、借贷或税务建议。生成式模型默认关闭，当前说明均由本地模板生成。",
                modifier = Modifier.fillMaxWidth().padding(vertical = HengjiSpacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("恢复默认建议？") },
            text = { Text("这会清除已采纳、稍后和忽略状态，但不会修改任何账本数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onResetFeedback()
                    },
                ) {
                    Text("恢复默认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun InsightCard(
    index: Int,
    insight: DemoInsight,
    busy: Boolean,
    interactionEnabled: Boolean,
    reduceMotion: Boolean,
    onFeedback: (InsightFeedback) -> Unit,
) {
    val accent = when (insight.priority) {
        InsightPriority.High -> MaterialTheme.colorScheme.primary
        InsightPriority.Medium -> MaterialTheme.colorScheme.secondary
        InsightPriority.Low -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(HengjiSpacing.lg), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = when (insight.priority) {
                        InsightPriority.High -> "优先处理"
                        InsightPriority.Medium -> "建议复核"
                        InsightPriority.Low -> "可以关注"
                    },
                    containerColor = accent.copy(alpha = 0.16f),
                    contentColor = accent,
                )
                Text("#${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(insight.title, style = MaterialTheme.typography.titleLarge)
            Text(insight.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("为什么出现", style = MaterialTheme.typography.labelLarge)
            Text(insight.evidence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("置信度", style = MaterialTheme.typography.bodyMedium)
                Text("${insight.confidence}%", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { insight.confidence / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
            )
            Text("建议行动", style = MaterialTheme.typography.labelLarge)
            Text(insight.action, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "预计影响 ${formatMoney(insight.impactMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (insight.feedback != InsightFeedback.NEW) {
                StatusPill(
                    text = when (insight.feedback) {
                        InsightFeedback.ADOPTED -> "已采纳"
                        InsightFeedback.SNOOZED -> "已稍后提醒"
                        InsightFeedback.IGNORED -> "已忽略"
                        InsightFeedback.NEW -> error("Handled by condition")
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (busy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!reduceMotion) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(HengjiSpacing.sm))
                    }
                    Text("正在保存到本机…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
            ) {
                Button(
                    onClick = { onFeedback(InsightFeedback.ADOPTED) },
                    modifier = Modifier.weight(1f),
                    enabled = interactionEnabled && insight.feedback != InsightFeedback.ADOPTED,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text("采纳")
                }
                FilledTonalButton(
                    onClick = { onFeedback(InsightFeedback.SNOOZED) },
                    modifier = Modifier.weight(1f),
                    enabled = interactionEnabled && insight.feedback != InsightFeedback.SNOOZED,
                ) {
                    Text("稍后 7 天")
                }
            }
            TextButton(
                onClick = { onFeedback(InsightFeedback.IGNORED) },
                modifier = Modifier.fillMaxWidth(),
                enabled = interactionEnabled && insight.feedback != InsightFeedback.IGNORED,
            ) {
                Text("忽略这条建议")
            }
        }
    }
}

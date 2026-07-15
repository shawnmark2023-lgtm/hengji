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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hengji.app.model.DemoInsight
import com.hengji.app.model.InsightPriority
import com.hengji.app.model.formatMoney
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.SectionCard
import com.hengji.app.ui.components.StatusPill

@Composable
fun InsightsScreen(insights: List<DemoInsight>) {
    val feedback = remember { mutableStateMapOf<Int, String>() }
    val totalImpact = insights.sumOf { it.impactMinor }

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
            )
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    StatusPill("3 条建议")
                }
            }
        }
        itemsIndexed(insights) { index, insight ->
            InsightCard(
                index = index,
                insight = insight,
                feedback = feedback[index],
                onFeedback = { feedback[index] = it },
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
}

@Composable
private fun InsightCard(
    index: Int,
    insight: DemoInsight,
    feedback: String?,
    onFeedback: (String) -> Unit,
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
            if (feedback == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
                ) {
                    Button(onClick = { onFeedback("已采纳") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(HengjiSpacing.xs))
                        Text("采纳")
                    }
                    FilledTonalButton(onClick = { onFeedback("稍后提醒") }, modifier = Modifier.weight(1f)) {
                        Text("稍后")
                    }
                }
            } else {
                StatusPill(
                    text = feedback,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

package com.hengji.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.hengji.app.model.PersonalInsightFeed
import com.hengji.app.model.formatMoney
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.insights.InsightFeedback
import com.hengji.insights.InsightLearningStage

@Composable
fun InsightsScreen(
    feed: PersonalInsightFeed,
    busyDeduplicationKey: String?,
    isResetting: Boolean,
    reduceMotion: Boolean = false,
    statusMessage: String?,
    modelAvailable: Boolean = false,
    modelConsentEnabled: Boolean = false,
    modelBusy: Boolean = false,
    modelStatusMessage: String? = null,
    onModelConsentChange: (Boolean) -> Unit = {},
    onFeedback: (deduplicationKey: String, feedback: InsightFeedback) -> Unit,
    onResetFeedback: () -> Unit,
) {
    val totalImpact = feed.items.sumOf { it.impactMinor }
    val interactionLocked = busyDeduplicationKey != null || isResetting
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = HengjiSpacing.lg,
            top = HengjiSpacing.lg,
            end = HengjiSpacing.lg,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.lg),
    ) {
        item {
            ScreenHeader(
                eyebrow = "数据只留在你的设备",
                title = "智能分析",
                supporting = "消费记录覆盖三个月后，内置模型会做第一次专属分析；以后结合新记录和你的反馈继续学习。",
                action = {
                    TextButton(
                        onClick = { showResetConfirmation = true },
                        enabled = !interactionLocked,
                    ) {
                        if (isResetting && !reduceMotion) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(HengjiSpacing.xs))
                        }
                        Text(if (isResetting) "正在清除…" else "清除学习记录")
                    }
                },
            )
        }

        item { LearningProfilePanel(feed) }

        item {
            ModelEnhancementPanel(
                available = modelAvailable,
                consentEnabled = modelConsentEnabled,
                busy = modelBusy,
                statusMessage = modelStatusMessage,
                reduceMotion = reduceMotion,
                firstAnalysisEligible = feed.firstAnalysisEligible,
                daysUntilFirstAnalysis = feed.daysUntilFirstAnalysis,
                onConsentChange = onModelConsentChange,
            )
        }

        statusMessage?.let { message ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = HengjiSpacing.md, vertical = HengjiSpacing.sm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "这次最值得注意",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${feed.items.size} 条 · 潜在影响 ${formatMoney(totalImpact)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "按和你的关系排序",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (feed.items.isEmpty()) {
            item { EmptyInsightState(feed) }
        } else {
            itemsIndexed(
                items = feed.items,
                key = { _, insight -> insight.deduplicationKey },
            ) { index, insight ->
                InsightPanel(
                    index = index,
                    insight = insight,
                    busy = busyDeduplicationKey == insight.deduplicationKey,
                    interactionEnabled = !interactionLocked,
                    reduceMotion = reduceMotion,
                    onFeedback = { feedback -> onFeedback(insight.deduplicationKey, feedback) },
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(HengjiSpacing.xs))
                Text(
                    "智能分析只在本机运行，仅供你整理消费，不构成投资、借贷或税务建议。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("清除智能学习记录？") },
            text = { Text("这会清除过去的专属分析和你的反馈，重新从账单学习；收入支出记录不会改变。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onResetFeedback()
                    },
                ) {
                    Text("清除并恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ModelEnhancementPanel(
    available: Boolean,
    consentEnabled: Boolean,
    busy: Boolean,
    statusMessage: String?,
    reduceMotion: Boolean,
    firstAnalysisEligible: Boolean,
    daysUntilFirstAnalysis: Int,
    onConsentChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(HengjiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (available) Icons.Outlined.AutoAwesome else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(HengjiSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("本机智能分析", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !available -> "完整模型没有安装，请重新安装应用"
                            consentEnabled -> "已开启 · 不联网 · 不上传账单"
                            else -> "已关闭 · 模型不会运行"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (firstAnalysisEligible) {
                    "已达到第一次分析条件。模型会读取本机汇总和过去反馈，原始账单不会离开设备。"
                } else {
                    "还差约 $daysUntilFirstAnalysis 天记录。三个月前只积累数据，不会提前给出不可靠结论。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (available) {
                FilledTonalButton(
                    onClick = { onConsentChange(!consentEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    if (busy && !reduceMotion) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(HengjiSpacing.xs))
                    }
                    Text(
                        when {
                            busy -> "正在本机分析…"
                            consentEnabled -> "关闭智能分析"
                            else -> "开启智能分析"
                        },
                    )
                }
            }
            statusMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LearningProfilePanel(feed: PersonalInsightFeed) {
    val stageLabel = when (feed.learningStage) {
        InsightLearningStage.STARTING -> "正在积累记录"
        InsightLearningStage.LEARNING -> "开始了解你"
        InsightLearningStage.PERSONALIZED -> "越来越懂你"
        InsightLearningStage.ESTABLISHED -> "已形成长期习惯"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(HengjiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(HengjiSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("专属分析准备进度", style = MaterialTheme.typography.labelLarge)
                    Text(stageLabel, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "${feed.learningPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { feed.learningPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LearningStat("消费记录", "${feed.observedTransactionCount} 笔")
                LearningStat("已记录", "${feed.observedDays} 天")
                LearningStat("月份", "${feed.observedExpenseMonthCount} 个")
            }
            Text(
                if (feed.firstAnalysisEligible) {
                    "已经可以生成专属分析。你点“有帮助”或“不适合我”，下次分析会记住。"
                } else {
                    "继续正常记账即可。覆盖至少三个月后，恒迹会自动做第一次专属分析。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LearningStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyInsightState(feed: PersonalInsightFeed) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(HengjiSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(30.dp))
            Text(
                if (feed.firstAnalysisEligible) "这次没有需要特别提醒" else "先安心记满三个月",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (!feed.firstAnalysisEligible) {
                    "还差约 ${feed.daysUntilFirstAnalysis} 天。记录得越完整，第一次分析越像你。"
                } else {
                    "本期数据没有足够可靠的变化；恒迹不会为了显得聪明而编造建议。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightPanel(
    index: Int,
    insight: DemoInsight,
    busy: Boolean,
    interactionEnabled: Boolean,
    reduceMotion: Boolean,
    onFeedback: (InsightFeedback) -> Unit,
) {
    val accent = when (insight.priority) {
        InsightPriority.High -> MaterialTheme.colorScheme.error
        InsightPriority.Medium -> MaterialTheme.colorScheme.primary
        InsightPriority.Low -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(HengjiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "建议 ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
                Text(
                    text = "置信度 ${insight.confidence}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(insight.title, style = MaterialTheme.typography.titleLarge)
            Text(insight.summary, style = MaterialTheme.typography.bodyLarge)
            insight.modelDisclosure?.let { disclosure ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text(
                        disclosure,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            insight.personalizationReason?.let { reason ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                ) {
                    Text(
                        text = reason,
                        modifier = Modifier.padding(horizontal = HengjiSpacing.sm, vertical = HengjiSpacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("数据依据", style = MaterialTheme.typography.labelLarge)
            Text(
                insight.evidence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { insight.confidence / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("建议下一步", style = MaterialTheme.typography.labelLarge)
                    Text(insight.action, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                if (insight.impactMinor > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("潜在影响", style = MaterialTheme.typography.labelLarge)
                        Text(formatMoney(insight.impactMinor), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!reduceMotion) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                    Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text("有帮助")
                }
                OutlinedButton(
                    onClick = { onFeedback(InsightFeedback.IGNORED) },
                    modifier = Modifier.weight(1f),
                    enabled = interactionEnabled && insight.feedback != InsightFeedback.IGNORED,
                ) {
                    Icon(Icons.Outlined.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text("不适合我")
                }
            }
            FilledTonalButton(
                onClick = { onFeedback(InsightFeedback.SNOOZED) },
                modifier = Modifier.fillMaxWidth(),
                enabled = interactionEnabled && insight.feedback != InsightFeedback.SNOOZED,
            ) {
                Text("7 天后再提醒")
            }
        }
    }
}

package com.hengji.app.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hengji.app.theme.HengjiSpacing
import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportCandidate
import com.hengji.connectors.ImportErrorCode
import com.hengji.connectors.ImportIssue
import com.hengji.connectors.TransactionDirection

@Composable
fun ImportWizard(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
    localCaptureAvailable: Boolean = false,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            WizardHeader(state = state, onEvent = onEvent)
            if (state.isBusy && !reduceMotion) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "正在处理导入请求"
                        },
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 760.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        WizardStepRail(state, Modifier.width(238.dp).fillMaxHeight())
                        HorizontalDivider(
                            modifier = Modifier.width(1.dp).fillMaxHeight(),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        WizardContent(
                            state = state,
                            onEvent = onEvent,
                            localCaptureAvailable = localCaptureAvailable,
                            reduceMotion = reduceMotion,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        CompactStepProgress(state)
                        WizardContent(
                            state = state,
                            onEvent = onEvent,
                            localCaptureAvailable = localCaptureAvailable,
                            reduceMotion = reduceMotion,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HengjiSpacing.lg, vertical = HengjiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step !in setOf(ImportWizardStep.Source, ImportWizardStep.Result)) {
            TextButton(
                onClick = { onEvent(ImportFlowEvent.BackRequested) },
                enabled = !state.isBusy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(HengjiSpacing.xs))
                Text("返回")
            }
            Spacer(Modifier.width(HengjiSpacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "导入账单",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "先预览、再确认，整批操作都可撤销",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.step == ImportWizardStep.Result) {
            TextButton(
                onClick = { onEvent(ImportFlowEvent.ResetRequested) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("导入另一份")
            }
        }
    }
}

@Composable
private fun WizardStepRail(
    state: ImportWizardState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
    ) {
        Text("导入步骤", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(HengjiSpacing.xs))
        ImportWizardStep.entries.forEachIndexed { index, step ->
            val currentIndex = state.step.ordinal
            val current = step == state.step
            val complete = index < currentIndex
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        selected = current
                        stateDescription = when {
                            current -> "当前步骤"
                            complete -> "已完成"
                            else -> "未开始"
                        }
                    },
                color = if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(HengjiSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepMarker(index + 1, complete, current)
                    Spacer(Modifier.width(HengjiSpacing.sm))
                    Text(step.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrivacyMiniCard()
    }
}

@Composable
private fun CompactStepProgress(state: ImportWizardState) {
    val index = state.step.ordinal
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HengjiSpacing.lg, vertical = HengjiSpacing.sm)
            .semantics {
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    current = (index + 1).toFloat(),
                    range = 1f..ImportWizardStep.entries.size.toFloat(),
                    steps = ImportWizardStep.entries.size - 2,
                )
                stateDescription = "第 ${index + 1} 步，共 ${ImportWizardStep.entries.size} 步：${state.step.label}"
            },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.step.label, style = MaterialTheme.typography.labelLarge)
            Text("${index + 1} / ${ImportWizardStep.entries.size}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(HengjiSpacing.xs))
        LinearProgressIndicator(
            progress = { (index + 1f) / ImportWizardStep.entries.size },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepMarker(number: Int, complete: Boolean, current: Boolean) {
    val color = when {
        complete || current -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = color,
        contentColor = if (complete || current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (complete) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            else Text(number.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WizardContent(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
    localCaptureAvailable: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier.semantics {
            paneTitle = "导入账单，第 ${state.step.ordinal + 1} 步：${state.step.label}"
        },
    ) {
        state.error?.let { error ->
            ErrorBanner(error = error, onDismiss = { onEvent(ImportFlowEvent.ErrorDismissed) })
        }
        when (state.step) {
            ImportWizardStep.Source -> SourceStep(state, onEvent, localCaptureAvailable, reduceMotion)
            ImportWizardStep.Mapping -> MappingStep(state, onEvent)
            ImportWizardStep.Preview -> PreviewStep(state, onEvent)
            ImportWizardStep.Confirm -> ConfirmStep(state, onEvent, reduceMotion)
            ImportWizardStep.Result -> ResultStep(state, onEvent)
        }
    }
}

@Composable
private fun SourceStep(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
    localCaptureAvailable: Boolean,
    reduceMotion: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item {
            StepTitle(
                title = "选择消费记录来源",
                supporting = "长截图、图片、PDF 和用户文件都只在本机解析；先预览，确认后才入账。",
            )
        }
        item { SensitiveDataNotice() }
        if (localCaptureAvailable) {
            item {
                SourceCard(
                    title = "识别长截图",
                    supporting = "选择支付平台的长截图，本机拆成多笔账单；缺日期或商户的模糊项目会自动跳过",
                    badge = "本机 OCR · 多笔识别",
                    enabled = !state.isBusy,
                    selected = state.source == ImportSource.LocalCapture(LocalCaptureMode.LongScreenshot),
                    onClick = {
                        onEvent(
                            ImportFlowEvent.SourceChosen(
                                ImportSource.LocalCapture(LocalCaptureMode.LongScreenshot),
                            ),
                        )
                    },
                )
            }
            item {
                SourceCard(
                    title = "一键读取图片或 PDF",
                    supporting = "选择一张账单图片或 PDF，自动提取日期、商户、金额和分类；也可以从其他应用直接分享给恒迹",
                    badge = "本机读取 · 不上传",
                    enabled = !state.isBusy,
                    selected = state.source == ImportSource.LocalCapture(LocalCaptureMode.ImageOrPdf),
                    onClick = {
                        onEvent(
                            ImportFlowEvent.SourceChosen(
                                ImportSource.LocalCapture(LocalCaptureMode.ImageOrPdf),
                            ),
                        )
                    },
                )
            }
        }
        item {
            SourceCard(
                title = "CSV 沙箱样例",
                supporting = "内置 8 条虚构记录，适合体验字段映射与去重",
                badge = "沙箱 · 非生产数据",
                enabled = !state.isBusy,
                selected = state.source == ImportSource.CsvSandboxSample,
                onClick = { onEvent(ImportFlowEvent.SourceChosen(ImportSource.CsvSandboxSample)) },
            )
        }
        item {
            SourceCard(
                title = "JSON 沙箱样例",
                supporting = "内置结构化虚构记录，不连接任何真实消费平台",
                badge = "沙箱 · 非 OAuth 授权",
                enabled = !state.isBusy,
                selected = state.source == ImportSource.JsonSandboxSample,
                onClick = { onEvent(ImportFlowEvent.SourceChosen(ImportSource.JsonSandboxSample)) },
            )
        }
        item {
            SourceCard(
                title = "选择本机 CSV 文件",
                supporting = "由系统文件选择器授权；不扫描其他目录，不上传原始账单",
                badge = "用户主动选择",
                enabled = !state.isBusy,
                selected = state.source == ImportSource.UserFile(ImportDocumentFormat.Csv),
                onClick = {
                    onEvent(ImportFlowEvent.SourceChosen(ImportSource.UserFile(ImportDocumentFormat.Csv)))
                },
            )
        }
        item {
            SourceCard(
                title = "选择本机 JSON 文件",
                supporting = "仅接受受支持的交易数组，异常结构会在写入前拦截",
                badge = "用户主动选择",
                enabled = !state.isBusy,
                selected = state.source == ImportSource.UserFile(ImportDocumentFormat.Json),
                onClick = {
                    onEvent(ImportFlowEvent.SourceChosen(ImportSource.UserFile(ImportDocumentFormat.Json)))
                },
            )
        }
        if (state.isBusy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = HengjiSpacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!reduceMotion) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(HengjiSpacing.sm))
                    }
                    Text("正在安全读取来源…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    supporting: String,
    badge: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "已选择" else "未选择"
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(HengjiSpacing.lg), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            StatusLabel(badge, warning = badge.contains("沙箱"))
            Text(supporting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MappingStep(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
) {
    val document = state.document ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item {
            StepTitle(
                title = "确认字段对应关系",
                supporting = "日期与金额必须映射；其他字段可以跳过，稍后仍可手工补充。",
            )
        }
        item { DocumentSummaryCard(document) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                ImportTargetField.entries.forEach { target ->
                    FieldMappingSelector(
                        target = target,
                        selectedField = state.mapping.sourceFor(target),
                        availableFields = document.fields,
                        enabled = !state.isBusy,
                        onSelected = { onEvent(ImportFlowEvent.MappingChanged(target, it)) },
                    )
                }
            }
        }
        item { SampleRowsPreview(document) }
        item {
            Button(
                onClick = { onEvent(ImportFlowEvent.PreviewRequested) },
                enabled = state.canPreview,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(if (state.isBusy) "正在解析…" else "生成导入预览")
            }
        }
        if (!state.mapping.isComplete) {
            item {
                Text(
                    "请先映射交易时间和金额。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FieldMappingSelector(
    target: ImportTargetField,
    selectedField: String?,
    availableFields: List<String>,
    enabled: Boolean,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(target.label, style = MaterialTheme.typography.labelLarge)
            if (target.required) {
                Spacer(Modifier.width(HengjiSpacing.xs))
                Text("必填", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(HengjiSpacing.xs))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .semantics {
                        contentDescription = buildString {
                            append(target.label)
                            if (target.required) append("，必填")
                            append("，当前映射：")
                            append(selectedField ?: "不导入此字段")
                        }
                        stateDescription = selectedField ?: "未映射"
                    },
            ) {
                Text(
                    selectedField ?: "不导入此字段",
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("选择")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 240.dp, max = 520.dp),
            ) {
                if (!target.required) {
                    DropdownMenuItem(
                        text = { Text("不导入此字段") },
                        modifier = Modifier.semantics { selected = selectedField == null },
                        onClick = {
                            onSelected(null)
                            expanded = false
                        },
                    )
                }
                availableFields.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field) },
                        modifier = Modifier.semantics { selected = selectedField == field },
                        onClick = {
                            onSelected(field)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
) {
    val preview = state.preview ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item {
            StepTitle(
                title = "逐条预览并处理重复",
                supporting = "默认只选中可安全写入的记录；重复项自动跳过，错误项不会进入账本。",
            )
        }
        item {
            PreviewCounters(state)
        }
        if (preview.fileIssues.isNotEmpty()) {
            item { IssueList(preview.fileIssues, title = "文件级问题") }
        }
        items(preview.candidates.size.coerceAtMost(100)) { index ->
            val candidate = preview.candidates[index]
            PreviewCandidateCard(
                candidate = candidate,
                accepted = candidate.transaction?.fingerprint in state.acceptedFingerprints,
                onToggle = { accepted ->
                    candidate.transaction?.fingerprint?.let { fingerprint ->
                        onEvent(ImportFlowEvent.CandidateToggled(fingerprint, accepted))
                    }
                },
            )
        }
        if (preview.candidates.size > 100) {
            item {
                Text(
                    "为保证辅助技术和窄屏性能，此处显示前 100 条；提交统计仍覆盖全部记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Button(
                onClick = { onEvent(ImportFlowEvent.ConfirmationRequested) },
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text("继续确认 ${state.selectedCount} 笔")
            }
        }
    }
}

@Composable
private fun PreviewCounters(state: ImportWizardState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 520.dp
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs)) {
                CounterLine("可导入", state.readyCount, MaterialTheme.colorScheme.primary)
                CounterLine("重复跳过", state.duplicateCount, MaterialTheme.colorScheme.secondary)
                CounterLine("错误拦截", state.invalidCount, MaterialTheme.colorScheme.error)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
                CounterLine("可导入", state.readyCount, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                CounterLine("重复跳过", state.duplicateCount, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                CounterLine("错误拦截", state.invalidCount, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CounterLine(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.padding(horizontal = HengjiSpacing.md, vertical = HengjiSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(HengjiSpacing.sm))
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
private fun PreviewCandidateCard(
    candidate: ImportCandidate,
    accepted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val statusText = when (candidate.status) {
        CandidateStatus.READY -> if (accepted) "已选择导入" else "未选择"
        CandidateStatus.DUPLICATE -> "重复项，已跳过"
        CandidateStatus.INVALID -> "存在错误，不能导入"
    }
    val transactionSummary = candidate.transaction?.let { transaction ->
        listOfNotNull(
            transaction.merchant,
            formatImportedAmount(transaction.amountMinor, transaction.currency, transaction.direction),
            transaction.occurredAt,
            transaction.category,
        ).joinToString("，")
    } ?: "交易字段未能解析"
    val issueSummary = candidate.issues
        .joinToString(separator = "；", prefix = if (candidate.issues.isEmpty()) "" else "，问题：") {
            friendlyIssue(it)
        }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "第 ${candidate.sourceRowNumber} 行，$statusText，$transactionSummary$issueSummary"
                stateDescription = statusText
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(HengjiSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            if (candidate.status == CandidateStatus.READY) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = "选择第 ${candidate.sourceRowNumber} 行" },
                )
            } else {
                Icon(
                    imageVector = if (candidate.status == CandidateStatus.DUPLICATE) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (candidate.status == CandidateStatus.DUPLICATE) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Spacer(Modifier.width(HengjiSpacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs)) {
                val transaction = candidate.transaction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        transaction?.merchant ?: "第 ${candidate.sourceRowNumber} 行",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (transaction != null) {
                        Spacer(Modifier.width(HengjiSpacing.sm))
                        Text(
                            formatImportedAmount(transaction.amountMinor, transaction.currency, transaction.direction),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (transaction != null) {
                    Text(
                        listOfNotNull(transaction.occurredAt, transaction.category).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusLabel(statusText, warning = candidate.status != CandidateStatus.READY)
                if (candidate.issues.isNotEmpty()) {
                    candidate.issues.forEach { issue ->
                        Text(
                            "• ${friendlyIssue(issue)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
    reduceMotion: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item {
            StepTitle(
                title = "最后确认",
                supporting = "提交会作为一个原子批次执行：全部成功，或一笔都不写入。",
            )
        }
        item { SensitiveDataNotice() }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(HengjiSpacing.lg), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                    ConfirmLine("即将写入", "${state.selectedCount} 笔")
                    ConfirmLine("重复跳过", "${state.duplicateCount} 笔")
                    ConfirmLine("错误拦截", "${state.invalidCount} 笔")
                    ConfirmLine("数据位置", "仅本机")
                    ConfirmLine("写入方式", "原子批次，可撤销")
                    state.document?.let { document ->
                        ConfirmLine("来源", if (document.isSandbox) "沙箱样例 · 非生产" else document.displayName)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onEvent(ImportFlowEvent.CommitRequested) },
                enabled = state.canCommit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                if (state.isBusy && !reduceMotion) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(HengjiSpacing.sm))
                }
                Text(if (state.isBusy) "正在原子提交…" else "确认导入 ${state.selectedCount} 笔")
            }
        }
        item {
            Text(
                "导入不会创建登录账户，也不会把原始账单发送给模型或远程服务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultStep(
    state: ImportWizardState,
    onEvent: (ImportFlowEvent) -> Unit,
) {
    val commit = state.commitResult ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                Text(
                    if (state.rollbackResult == null) "导入完成" else "批次已撤销",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(HengjiSpacing.xs))
                Text(
                    if (state.rollbackResult == null) {
                        "已将 ${commit.insertedFingerprints.size} 笔记录写入本机账本。"
                    } else {
                        "已从本机账本移除 ${state.rollbackResult.removedFingerprints.size} 笔记录。"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(HengjiSpacing.lg), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
                    ConfirmLine("批次编号", commit.batchId)
                    ConfirmLine("提交时间", commit.committedAt)
                    state.rollbackResult?.let { rollback ->
                        ConfirmLine("撤销时间", rollback.rolledBackAt)
                        ConfirmLine("撤销状态", if (rollback.alreadyRolledBack) "此前已撤销" else "已完整撤销")
                    }
                }
            }
        }
        if (state.rollbackResult == null) {
            item {
                OutlinedButton(
                    onClick = { onEvent(ImportFlowEvent.RollbackRequested) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp).heightIn(min = 52.dp),
                ) {
                    Text(if (state.isBusy) "正在撤销…" else "撤销整个导入批次")
                }
            }
        }
        item {
            Text(
                "撤销按批次执行，不影响导入前已有的账单。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, supporting: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(HengjiSpacing.xs))
        Text(supporting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SensitiveDataNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(HengjiSpacing.md), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(HengjiSpacing.sm))
            Column {
                Text("消费记录仍属于敏感数据", style = MaterialTheme.typography.titleMedium)
                Text(
                    "恒迹只解析完成记账所需的时间、金额、商户、分类等字段；不读取姓名、手机号、位置或通讯录。原始文件不上传。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PrivacyMiniCard() {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(HengjiSpacing.xs))
        Text(
            "原始文件仅在本机处理",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocumentSummaryCard(document: ImportDocumentSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(HengjiSpacing.md), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(document.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(HengjiSpacing.sm))
                StatusLabel(if (document.isSandbox) "沙箱 · 非生产" else "本机文件", warning = document.isSandbox)
            }
            Text(
                "${document.format.name.uppercase()} · ${formatBytes(document.byteCount)} · ${document.fields.size} 个字段",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SampleRowsPreview(document: ImportDocumentSummary) {
    Column(Modifier.fillMaxWidth()) {
        Text("脱敏样例预览", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(HengjiSpacing.xs))
        Text(
            "最多展示 5 行，仅用于核对映射。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(HengjiSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.xs),
        ) {
            document.fields.forEach { field ->
                Column(
                    modifier = Modifier.widthIn(min = 140.dp, max = 220.dp).background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp),
                    ).padding(HengjiSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs),
                ) {
                    Text(field, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                    document.sampleRows.forEach { row ->
                        Text(
                            row[field].orEmpty().ifBlank { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueList(issues: List<ImportIssue>, title: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(HengjiSpacing.md), verticalArrangement = Arrangement.spacedBy(HengjiSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            issues.forEach { issue ->
                Text(
                    "• ${friendlyIssue(issue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: ImportFlowError, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HengjiSpacing.lg, vertical = HengjiSpacing.sm)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "${error.title}。${error.message}"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(HengjiSpacing.md), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(HengjiSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(error.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(error.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭错误提示")
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String, warning: Boolean) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ConfirmLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(HengjiSpacing.md))
        Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 3)
    }
}

private fun friendlyIssue(issue: ImportIssue): String {
    val location = buildString {
        issue.rowNumber?.let { append("第 $it 行") }
        issue.field?.let { field ->
            if (isNotEmpty()) append(" · ")
            append(field)
        }
    }
    val description = when (issue.code) {
        ImportErrorCode.FILE_TOO_LARGE -> "文件超过允许大小"
        ImportErrorCode.TOO_MANY_ROWS -> "行数超过安全上限"
        ImportErrorCode.TOO_MANY_COLUMNS -> "字段数超过安全上限"
        ImportErrorCode.CELL_TOO_LARGE -> "单个字段内容过长"
        ImportErrorCode.MALFORMED_CSV -> "CSV 结构无法解析"
        ImportErrorCode.MALFORMED_JSON -> "JSON 格式无法解析"
        ImportErrorCode.UNSUPPORTED_JSON_SHAPE -> "JSON 结构不受支持"
        ImportErrorCode.DUPLICATE_HEADER -> "存在重复表头"
        ImportErrorCode.MISSING_REQUIRED_FIELD -> "必填值缺失"
        ImportErrorCode.INVALID_AMOUNT -> "金额格式不正确"
        ImportErrorCode.INVALID_CURRENCY -> "币种代码不正确"
        ImportErrorCode.INVALID_DIRECTION -> "收支方向不正确"
        ImportErrorCode.INVALID_DATE -> "日期格式不正确"
        ImportErrorCode.DANGEROUS_FORMULA -> "疑似表格公式注入，已拦截"
    }
    return if (location.isBlank()) description else "$location：$description"
}

private fun formatImportedAmount(amountMinor: Long, currency: String, direction: TransactionDirection): String {
    val whole = amountMinor / 100
    val cents = (amountMinor % 100).toString().padStart(2, '0')
    val sign = if (direction == TransactionDirection.EXPENSE) "−" else "+"
    return "$sign$currency $whole.$cents"
}

private fun formatBytes(byteCount: Long): String = when {
    byteCount < 1_024 -> "$byteCount B"
    byteCount < 1_048_576 -> "${byteCount / 1_024} KB"
    else -> "${byteCount / 1_048_576} MB"
}

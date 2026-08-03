package com.hengji.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hengji.app.theme.HengjiSpacing

private data class GuidePage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val checklist: List<String>,
)

private val guidePages = listOf(
    GuidePage(
        Icons.Outlined.Lock,
        "欢迎使用恒迹",
        "不用注册，不用连银行卡。你的账单和智能分析都保存在这台设备上。",
        listOf("打开就能记账", "数据不会上传", "随时可以导出或清除"),
    ),
    GuidePage(
        Icons.AutoMirrored.Outlined.ReceiptLong,
        "先记第一笔消费",
        "点底部中间的“＋”，填三样东西：在哪花、花多少、属于哪一类，然后点保存。",
        listOf("商家：例如早餐店", "金额：例如 18.50", "分类：例如餐饮"),
    ),
    GuidePage(
        Icons.Outlined.FileOpen,
        "有旧账单就直接导入",
        "到“设置”点“导入账单”，选择 CSV 或 JSON。恒迹会先给你检查，确认后才写入。",
        listOf("先预览", "重复账单自动跳过", "导错了可以按批次撤销"),
    ),
    GuidePage(
        Icons.Outlined.AutoAwesome,
        "三个月后看专属分析",
        "消费记录覆盖三个月后，内置模型会自动做第一次分析，以后根据新记录和你的反馈继续学习。",
        listOf("模型已随应用安装", "只在本机运行", "可在“智能分析”里随时关闭"),
    ),
)

@Composable
fun FirstRunGuide(
    onFinished: () -> Unit,
    onTryAddTransaction: () -> Unit,
    onTryImport: () -> Unit,
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = guidePages[pageIndex]
    // Avoid treating an accidental outside click or Escape/Back press as completed onboarding.
    // Users can still leave explicitly through the visible “跳过教程” action.
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(HengjiSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(HengjiSpacing.lg),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "第 ${pageIndex + 1} 步，共 ${guidePages.size} 步",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = onFinished) { Text("跳过教程") }
                }
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(page.body, style = MaterialTheme.typography.bodyLarge)
                Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
                    page.checklist.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(HengjiSpacing.sm))
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                when (pageIndex) {
                    1 -> FilledTonalButton(
                        onClick = {
                            onFinished()
                            onTryAddTransaction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("现在试着记一笔") }
                    2 -> FilledTonalButton(
                        onClick = {
                            onFinished()
                            onTryImport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("现在导入旧账单") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
                ) {
                    if (pageIndex > 0) {
                        TextButton(onClick = { pageIndex -= 1 }) { Text("上一步") }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (pageIndex == guidePages.lastIndex) onFinished() else pageIndex += 1
                        },
                    ) {
                        Text(if (pageIndex == guidePages.lastIndex) "我会用了" else "下一步")
                    }
                }
            }
        }
    }
}

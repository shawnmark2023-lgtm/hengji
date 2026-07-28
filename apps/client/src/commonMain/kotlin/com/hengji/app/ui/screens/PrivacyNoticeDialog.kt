package com.hengji.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PrivacyNoticeDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("隐私说明") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrivacySection(
                    title = "本地优先",
                    body = "衡记不要求账户，不采集姓名、手机号、位置、通讯录、广告标识或设备指纹。" +
                        "交易、物品和分析结果保存在设备上的受保护账本中。",
                )
                PrivacySection(
                    title = "文件与识别",
                    body = "CSV、JSON、图片、PDF 和分享文本只在你主动选择或分享后处理。" +
                        "图片与 PDF 的文字识别在设备内完成；只有你确认的结构化字段会写入账本，原文件和 OCR 原文不保存。",
                )
                PrivacySection(
                    title = "权限与第三方组件",
                    body = "系统文件选择器不授予整库访问。通知权限是可选的；拒绝不会影响记账功能。" +
                        "Android 文字识别使用 Google ML Kit，但当前发行包移除了网络权限，应用不包含广告或行为追踪 SDK。",
                )
                PrivacySection(
                    title = "保留、导出与删除",
                    body = "数据会保留到你在设置中清除、卸载应用或由操作系统移除应用数据为止。" +
                        "你可以随时导出完整 JSON 备份或 CSV 流水；“清除数据”会在确认后删除本机账本内容。",
                )
                PrivacySection(
                    title = "选择与撤回",
                    body = "你可以在应用内关闭本地提醒，也可以在系统设置中撤回通知权限。" +
                        "未来任何联网、账户或同步功能都必须另行说明用途并重新取得同意。",
                )
                PrivacySection(
                    title = "使用边界",
                    body = "衡记用于个人记录与本地分析，不提供银行、支付、信贷、证券交易、投资、税务或受托理财服务。",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun PrivacySection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

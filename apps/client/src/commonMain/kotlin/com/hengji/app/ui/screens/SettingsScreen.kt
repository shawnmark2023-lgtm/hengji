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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.LocalOnlyBadge
import com.hengji.app.ui.components.ScreenHeader
import com.hengji.app.ui.components.SectionCard
import com.hengji.app.ui.components.StatusPill

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    var reduceMotion by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(HengjiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md),
    ) {
        item {
            ScreenHeader(
                eyebrow = "隐私与控制",
                title = "设置",
                supporting = "首版无账户、无同步；你可以随时导出或彻底清除本机数据。",
                action = { LocalOnlyBadge() },
            )
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(HengjiSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text("本地模式已开启", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "姓名、手机号、位置、广告标识：均不采集",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill("网络 0 次")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
                    ) {
                        FilledTonalButton(
                            onClick = { statusMessage = "已准备本机 JSON 导出预览（示例状态）" },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出数据") }
                        OutlinedButton(
                            onClick = { statusMessage = "清除操作将在正式数据层接入后要求二次确认" },
                            modifier = Modifier.weight(1f),
                        ) { Text("清除数据") }
                    }
                    statusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("外观与辅助功能", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(HengjiSpacing.sm))
                    SettingSwitchRow(
                        title = "深色外观",
                        supporting = "在所有共享界面使用高对比深色主题",
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitchRow(
                        title = "减少动态效果",
                        supporting = "减少非必要过渡与位移动画",
                        checked = reduceMotion,
                        onCheckedChange = { reduceMotion = it },
                    )
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                    Text("导入与连接器", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "所有外部记录先进入预览区，经你确认后才会写入主账本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectorRow("CSV / JSON 文件", "可用 · 本机解析", true)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConnectorRow("支付宝 / 微信账单文件", "沙箱映射 · 非自动同步", true)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConnectorRow("平台官方 OAuth", "等待平台真实 scope 与审核", false)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConnectorRow("Apple FinanceKit", "Beta · 受地区与 entitlement 限制", false)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = HengjiSpacing.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(HengjiSpacing.xs))
                Text(
                    "衡记 0.1.0 · Local-first preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HengjiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(HengjiSpacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConnectorRow(
    name: String,
    status: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(HengjiSpacing.md))
        StatusPill(
            text = if (enabled) "可用" else "未启用",
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

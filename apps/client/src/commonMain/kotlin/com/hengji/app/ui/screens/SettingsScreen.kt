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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    reduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit = {},
    dataActionStatus: String? = null,
    onExportData: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onRestoreData: () -> Unit = {},
    onClearData: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    storageStatus: String = "内存预览 · 关闭后不保留",
) {
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
                            Text(
                                storageStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill("网络 0 次")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ResponsiveActionPair(
                        first = {
                            FilledTonalButton(onClick = onExportData, modifier = Modifier.fillMaxWidth()) {
                                Text("完整 JSON")
                            }
                        },
                        second = {
                            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                                Text("流水 CSV")
                            }
                        },
                    )
                    ResponsiveActionPair(
                        first = {
                            OutlinedButton(onClick = onRestoreData, modifier = Modifier.fillMaxWidth()) {
                                Text("恢复备份")
                            }
                        },
                        second = {
                            OutlinedButton(onClick = onClearData, modifier = Modifier.fillMaxWidth()) {
                                Text("清除数据")
                            }
                        },
                    )
                    dataActionStatus?.let {
                        Text(
                            it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "外观与辅助功能",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
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
                        supporting = "本次使用关闭衡记自定义加载动画；系统界面仍遵循设备设置",
                        checked = reduceMotion,
                        onCheckedChange = onReduceMotionChange,
                    )
                }
            }
        }
        item {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.md)) {
                    Text(
                        "导入与连接器",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "所有外部记录先进入预览区，经你确认后才会写入主账本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = onOpenImport,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("打开导入中心") }
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
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {}
            .padding(vertical = HengjiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(HengjiSpacing.md))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ResponsiveActionPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val effectiveWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (effectiveWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
                first()
                second()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HengjiSpacing.sm),
            ) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { first() }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { second() }
            }
        }
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

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.hengji.app.application.PriceNotificationControl
import com.hengji.app.application.AppAppearanceMode

@Composable
fun SettingsScreen(
    appearanceMode: AppAppearanceMode,
    onAppearanceModeChange: (AppAppearanceMode) -> Unit,
    reduceMotion: Boolean = false,
    systemReduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit = {},
    dataActionStatus: String? = null,
    onExportData: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onRestoreData: () -> Unit = {},
    onClearData: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    storageStatus: String = "内存预览 · 关闭后不保留",
    quickEntryShortcutStatus: String? = null,
    priceNotificationControl: PriceNotificationControl? = null,
) {
    var showPrivacyNotice by rememberSaveable { mutableStateOf(false) }

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
                            OutlinedButton(
                                onClick = onClearData,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("清除数据")
                            }
                        },
                    )
                    OutlinedButton(
                        onClick = { showPrivacyNotice = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("查看隐私说明")
                    }
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
        priceNotificationControl?.let { control ->
            item {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(HengjiSpacing.sm)) {
                        Text(
                            "出售目标提醒",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            control.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = if (control.canRequest) control.request else control.disable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (control.canRequest) "允许系统通知" else "关闭目标提醒")
                        }
                        Text(
                            "后台只读取本机受保护账本，并仅使用授权实时报价判断；不会上传流水，也不会在通知中显示流水原文。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(
                        "外观",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "默认跟随系统；手动选择后仍可随时恢复系统设置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.selectableGroup()) {
                        AppearanceChoiceRow(
                            title = "跟随系统",
                            selected = appearanceMode == AppAppearanceMode.SYSTEM,
                            onClick = { onAppearanceModeChange(AppAppearanceMode.SYSTEM) },
                        )
                        AppearanceChoiceRow(
                            title = "浅色",
                            selected = appearanceMode == AppAppearanceMode.LIGHT,
                            onClick = { onAppearanceModeChange(AppAppearanceMode.LIGHT) },
                        )
                        AppearanceChoiceRow(
                            title = "深色",
                            selected = appearanceMode == AppAppearanceMode.DARK,
                            onClick = { onAppearanceModeChange(AppAppearanceMode.DARK) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitchRow(
                        title = "进一步减少动态效果",
                        supporting = if (systemReduceMotion) {
                            "系统已要求减少动态效果；恒迹不会允许应用内设置覆盖该选择"
                        } else {
                            "额外关闭恒迹自定义加载动画；系统辅助功能设置始终优先"
                        },
                        checked = reduceMotion,
                        onCheckedChange = onReduceMotionChange,
                    )
                    quickEntryShortcutStatus?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            it,
                            modifier = Modifier.padding(vertical = HengjiSpacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        "所有外部记录先进入预览区，经你确认后才会写入主账本。" +
                            "当前版本只提供系统文件选择器，不包含自动同步或平台账户授权。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = onOpenImport,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("打开导入中心") }
                    ConnectorRow("CSV / JSON 文件", "系统选择器授权 · 本机解析")
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
                    "恒迹 0.1.0 · Local-first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showPrivacyNotice) {
        PrivacyNoticeDialog(onDismiss = { showPrivacyNotice = false })
    }
}

@Composable
private fun AppearanceChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = HengjiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(HengjiSpacing.sm))
        Text(title, style = MaterialTheme.typography.bodyLarge)
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
            text = "本机",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

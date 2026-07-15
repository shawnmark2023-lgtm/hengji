package com.hengji.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val supportingLabel: String,
    val icon: ImageVector,
) {
    Overview("概览", "本月消费与价值", Icons.Default.Home),
    Ledger("流水", "每一笔收支", Icons.AutoMirrored.Filled.List),
    Assets("物品", "拥有成本与残值", Icons.Default.ShoppingCart),
    Insights("洞察", "可解释的优化建议", Icons.Default.Star),
    Settings("设置", "隐私、导入与偏好", Icons.Default.Settings),
}

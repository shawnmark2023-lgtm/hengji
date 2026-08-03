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
    Overview("首页", "这个月花了多少", Icons.Default.Home),
    Ledger("账单", "每一笔收入和支出", Icons.AutoMirrored.Filled.List),
    Assets("我的物品", "买了多久、还值多少", Icons.Default.ShoppingCart),
    Insights("智能分析", "看懂钱花到哪里", Icons.Default.Star),
    Settings("设置", "数据、外观和隐私", Icons.Default.Settings),
}

package com.hengji.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hengji.app.navigation.AppDestination
import com.hengji.app.theme.HengjiSpacing
import com.hengji.app.ui.components.BrandBlock
import com.hengji.app.ui.components.LocalOnlyBadge

@Composable
fun AdaptiveAppShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String = destination.label,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val effectiveWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
        when {
            effectiveWidth < 700.dp -> CompactShell(
                destination = destination,
                onDestinationChange = onDestinationChange,
                onAddTransaction = onAddTransaction,
                paneTitle = paneTitle,
                content = content,
            )
            effectiveWidth < 1080.dp -> RailShell(
                destination = destination,
                onDestinationChange = onDestinationChange,
                onAddTransaction = onAddTransaction,
                paneTitle = paneTitle,
                content = content,
            )
            else -> ExpandedShell(
                destination = destination,
                onDestinationChange = onDestinationChange,
                onAddTransaction = onAddTransaction,
                paneTitle = paneTitle,
                content = content,
            )
        }
    }
}

@Composable
private fun CompactShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { onDestinationChange(item) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                modifier = Modifier
                    .size(56.dp)
                    .semantics { contentDescription = "新增流水" },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .semantics { this.paneTitle = paneTitle },
        ) {
            content()
        }
    }
}

@Composable
private fun RailShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
            header = {
                BrandBlock(compact = true, modifier = Modifier.padding(vertical = HengjiSpacing.md))
                FloatingActionButton(
                    onClick = onAddTransaction,
                    modifier = Modifier.padding(vertical = HengjiSpacing.lg),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增流水")
                }
            },
        ) {
            AppDestination.entries.forEach { item ->
                NavigationRailItem(
                    selected = item == destination,
                    onClick = { onDestinationChange(item) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .semantics { this.paneTitle = paneTitle },
        ) {
            content()
        }
    }
}

@Composable
private fun ExpandedShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.width(272.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = HengjiSpacing.md, vertical = HengjiSpacing.lg),
            ) {
                BrandBlock(modifier = Modifier.padding(horizontal = HengjiSpacing.xs))
                Spacer(Modifier.height(HengjiSpacing.xl))
                Button(
                    onClick = onAddTransaction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = HengjiSpacing.md),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(HengjiSpacing.xs))
                    Text("记一笔", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(HengjiSpacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppDestination.entries.forEach { item ->
                        val selected = item == destination
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onDestinationChange(item) },
                                ),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = HengjiSpacing.md, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(item.icon, contentDescription = null)
                                Spacer(Modifier.width(HengjiSpacing.md))
                                Column {
                                    Text(item.label, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        item.supportingLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                LocalOnlyBadge(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(HengjiSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "网络访问：0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .semantics { this.paneTitle = paneTitle },
        ) {
            content()
        }
    }
}

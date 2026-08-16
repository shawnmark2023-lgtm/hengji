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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.hengji.app.ui.components.GlassSurface
import com.hengji.app.ui.components.LocalOnlyBadge

@Composable
fun AdaptiveAppShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String = destination.label,
    allowPageSwipe: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (AppDestination) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val effectiveWidth = maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f)
        when {
            effectiveWidth < 700.dp -> CompactShell(
                destination = destination,
                onDestinationChange = onDestinationChange,
                onAddTransaction = onAddTransaction,
                paneTitle = paneTitle,
                allowPageSwipe = allowPageSwipe,
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
    allowPageSwipe: Boolean,
    content: @Composable (AppDestination) -> Unit,
) {
    val destinations = AppDestination.entries
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(destination).coerceAtLeast(0),
        pageCount = { destinations.size },
    )
    LaunchedEffect(destination, allowPageSwipe) {
        if (allowPageSwipe) {
            val target = destinations.indexOf(destination).coerceAtLeast(0)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage, allowPageSwipe) {
        if (allowPageSwipe) {
            val page = destinations[pagerState.currentPage]
            if (page != destination) onDestinationChange(page)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (allowPageSwipe) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { destinations[it].name },
                beyondViewportPageCount = 0,
            ) { pageIndex ->
                val page = destinations[pageIndex]
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp)
                        .semantics { this.paneTitle = if (page == destination) paneTitle else page.label },
                ) {
                    content(page)
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
                    .semantics { this.paneTitle = paneTitle },
            ) {
                content(destination)
            }
        }

        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = HengjiSpacing.sm, vertical = HengjiSpacing.sm),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                destinations.forEach { item ->
                    CompactDockItem(
                        item = item,
                        selected = item == destination,
                        onClick = { onDestinationChange(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = HengjiSpacing.lg, bottom = 92.dp),
        ) {
            FloatingActionButton(
                onClick = onAddTransaction,
                modifier = Modifier
                    .size(54.dp)
                    .semantics { contentDescription = "记一笔" },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun CompactDockItem(
    item: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f) else Color.Transparent,
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = contentColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun RailShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String,
    content: @Composable (AppDestination) -> Unit,
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
                    Icon(Icons.Default.Add, contentDescription = "记一笔")
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
            content(destination)
        }
    }
}

@Composable
private fun ExpandedShell(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    onAddTransaction: () -> Unit,
    paneTitle: String,
    content: @Composable (AppDestination) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.width(232.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = HengjiSpacing.sm, vertical = HengjiSpacing.lg),
            ) {
                BrandBlock(modifier = Modifier.padding(horizontal = HengjiSpacing.xs))
                Spacer(Modifier.height(HengjiSpacing.lg))
                Button(
                    onClick = onAddTransaction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
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
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                            } else {
                                Color.Transparent
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = HengjiSpacing.sm, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        ),
                                )
                                Spacer(Modifier.width(HengjiSpacing.sm))
                                Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(HengjiSpacing.sm))
                                Column {
                                    Text(item.label, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        item.supportingLabel,
                                        style = MaterialTheme.typography.bodySmall,
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
                Text(
                    "本机处理 · 默认不联网",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            content(destination)
        }
    }
}

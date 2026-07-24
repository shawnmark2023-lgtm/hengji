package com.hengji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hengji.app.generated.resources.Res
import com.hengji.app.generated.resources.app_name
import com.hengji.app.generated.resources.app_tagline
import com.hengji.app.generated.resources.ic_hengji_mark
import com.hengji.app.theme.HengjiSpacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Image

@Composable
fun BrandBlock(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_hengji_mark),
            contentDescription = if (compact) stringResource(Res.string.app_name) else null,
            modifier = Modifier.size(42.dp),
        )
        if (!compact) {
            Spacer(Modifier.width(HengjiSpacing.sm))
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth / fontScale < 620.dp) {
            Column {
                ScreenHeaderCopy(eyebrow, title, supporting)
                if (action != null) {
                    Spacer(Modifier.height(HengjiSpacing.md))
                    Box(Modifier.align(Alignment.End)) {
                        action()
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenHeaderCopy(
                    eyebrow = eyebrow,
                    title = title,
                    supporting = supporting,
                    modifier = Modifier.weight(1f),
                )
                if (action != null) {
                    Spacer(Modifier.width(HengjiSpacing.md))
                    action()
                }
            }
        }
    }
}

@Composable
private fun ScreenHeaderCopy(
    eyebrow: String,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(HengjiSpacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(HengjiSpacing.xs))
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LocalOnlyBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("仅保存在本机", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(HengjiSpacing.lg)) {
            Box(
                Modifier
                    .size(width = 28.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.height(HengjiSpacing.md))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(HengjiSpacing.xs))
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(HengjiSpacing.xs))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CategoryProgress(
    name: String,
    amount: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(amount, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(Modifier.padding(HengjiSpacing.lg)) {
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

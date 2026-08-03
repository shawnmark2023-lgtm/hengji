package com.hengji.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HengjiGreen = Color(0xFF087F71)
val HengjiGreenLight = Color(0xFFB8E3DB)
val HengjiApricot = Color(0xFFE5B86C)
val HengjiInk = Color(0xFF171A19)
val HengjiPaper = Color(0xFFF4F5F2)
val HengjiCream = Color(0xFFFCFCF9)
val HengjiSuccess = Color(0xFF087F71)
val HengjiWarning = Color(0xFF9A5A1F)
val HengjiGlassHighlight = Color(0xE6FFFFFF)
val HengjiGlassShadow = Color(0x24111F1C)

private val HengjiLightColors = lightColorScheme(
    primary = HengjiGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0EB),
    onPrimaryContainer = Color(0xFF043A34),
    secondary = Color(0xFF4E6460),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EAE7),
    onSecondaryContainer = Color(0xFF24322F),
    tertiary = Color(0xFF52708B),
    onTertiary = Color.White,
    background = HengjiPaper,
    onBackground = HengjiInk,
    surface = HengjiCream,
    onSurface = HengjiInk,
    surfaceVariant = Color(0xFFE9ECE8),
    onSurfaceVariant = Color(0xFF5A615E),
    outline = Color(0xFF7E8581),
    outlineVariant = Color(0xFFD8DCD8),
    error = Color(0xFFBA1A1A),
)

private val HengjiDarkColors = darkColorScheme(
    primary = Color(0xFF73D7C6),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF164F47),
    onPrimaryContainer = Color(0xFFC3F2E8),
    secondary = Color(0xFFB7C9C4),
    onSecondary = Color(0xFF23332F),
    secondaryContainer = Color(0xFF354640),
    onSecondaryContainer = Color(0xFFD3E5DF),
    tertiary = Color(0xFFADCBE5),
    onTertiary = Color(0xFF163248),
    background = Color(0xFF101311),
    onBackground = Color(0xFFE1E9E5),
    surface = Color(0xFF191D1B),
    onSurface = Color(0xFFE1E9E5),
    surfaceVariant = Color(0xFF2C322F),
    onSurfaceVariant = Color(0xFFC1CBC6),
    outline = Color(0xFF8B9691),
    outlineVariant = Color(0xFF3D4844),
    error = Color(0xFFFFB4AB),
)

object HengjiSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

val HengjiShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private val HengjiTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun HengjiTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) HengjiDarkColors else HengjiLightColors,
        typography = HengjiTypography,
        shapes = HengjiShapes,
        content = content,
    )
}

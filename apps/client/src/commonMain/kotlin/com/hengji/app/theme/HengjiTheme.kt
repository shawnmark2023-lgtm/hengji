package com.hengji.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HengjiGreen = Color(0xFF143F3A)
val HengjiGreenLight = Color(0xFFB8D9CF)
val HengjiApricot = Color(0xFFF4C982)
val HengjiInk = Color(0xFF18211F)
val HengjiPaper = Color(0xFFF7F6F1)
val HengjiCream = Color(0xFFFFFBF3)
val HengjiSuccess = Color(0xFF2E745F)
val HengjiWarning = Color(0xFF9A5A1F)

private val HengjiLightColors = lightColorScheme(
    primary = HengjiGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EBE4),
    onPrimaryContainer = Color(0xFF07312D),
    secondary = Color(0xFF6D4F22),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1AE),
    onSecondaryContainer = Color(0xFF2D1A00),
    tertiary = Color(0xFF466178),
    onTertiary = Color.White,
    background = HengjiPaper,
    onBackground = HengjiInk,
    surface = HengjiCream,
    onSurface = HengjiInk,
    surfaceVariant = Color(0xFFE7ECE8),
    onSurfaceVariant = Color(0xFF53605C),
    outline = Color(0xFF74817D),
    outlineVariant = Color(0xFFD3DBD7),
    error = Color(0xFFBA1A1A),
)

private val HengjiDarkColors = darkColorScheme(
    primary = Color(0xFF9ED3C4),
    onPrimary = Color(0xFF00372F),
    primaryContainer = Color(0xFF1A4F47),
    onPrimaryContainer = Color(0xFFC6F1E5),
    secondary = Color(0xFFF1C47C),
    onSecondary = Color(0xFF402D04),
    secondaryContainer = Color(0xFF594318),
    onSecondaryContainer = Color(0xFFFFDEA4),
    tertiary = Color(0xFFADCBE5),
    onTertiary = Color(0xFF163248),
    background = Color(0xFF101614),
    onBackground = Color(0xFFE1E9E5),
    surface = Color(0xFF171D1B),
    onSurface = Color(0xFFE1E9E5),
    surfaceVariant = Color(0xFF303936),
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

private val HengjiTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
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
        content = content,
    )
}

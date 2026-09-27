package com.aicarchecking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.aicarchecking.data.settings.ThemeMode
import com.aicarchecking.domain.model.Confidence
import com.aicarchecking.domain.model.EvidenceQuality
import com.aicarchecking.domain.model.PaintStatus
import com.aicarchecking.domain.model.Severity

object Brand {
    val Navy = Color(0xFF0B1220)
    val NavySurface = Color(0xFF131C2E)
    val NavySurfaceHigh = Color(0xFF1B2640)
    val Cyan = Color(0xFF00B4D8)
    val CyanDeep = Color(0xFF0077A8)
    val Amber = Color(0xFFFFB703)
    val AmberDeep = Color(0xFFC77800)
    val Green = Color(0xFF2EC4A6)
    val Orange = Color(0xFFFF7A1A)
    val Red = Color(0xFFE5484D)
    val Grey = Color(0xFF8A94A6)
}

private val DarkColors = darkColorScheme(
    primary = Brand.Cyan,
    onPrimary = Brand.Navy,
    primaryContainer = Color(0xFF003F55),
    onPrimaryContainer = Color(0xFFBDEBFF),
    secondary = Brand.Amber,
    onSecondary = Brand.Navy,
    secondaryContainer = Color(0xFF4A3500),
    onSecondaryContainer = Color(0xFFFFE08A),
    background = Brand.Navy,
    onBackground = Color(0xFFE6EAF2),
    surface = Brand.NavySurface,
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Brand.NavySurfaceHigh,
    onSurfaceVariant = Color(0xFFAEB8CC),
    surfaceContainer = Brand.NavySurface,
    surfaceContainerHigh = Brand.NavySurfaceHigh,
    outline = Color(0xFF3A4760),
    error = Brand.Red,
    errorContainer = Color(0xFF5C1518),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Brand.CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFFF),
    onPrimaryContainer = Color(0xFF002636),
    secondary = Brand.AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7B0),
    onSecondaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0E1726),
    surface = Color.White,
    onSurface = Color(0xFF0E1726),
    surfaceVariant = Color(0xFFE7ECF3),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainer = Color(0xFFF0F3F8),
    surfaceContainerHigh = Color(0xFFE7ECF3),
    outline = Color(0xFFC3CCD9),
    error = Color(0xFFC62828),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun AICarCheckingTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

fun severityColor(s: Severity): Color = when (s) {
    Severity.LOW -> Brand.Green
    Severity.MEDIUM -> Brand.Amber
    Severity.HIGH -> Brand.Orange
    Severity.CRITICAL -> Brand.Red
}

fun confidenceColor(c: Confidence): Color = when (c) {
    Confidence.LOW -> Brand.Grey
    Confidence.MEDIUM -> Brand.Cyan
    Confidence.HIGH -> Brand.Green
}

fun qualityColor(q: EvidenceQuality): Color = when (q) {
    EvidenceQuality.GOOD -> Brand.Green
    EvidenceQuality.ACCEPTABLE -> Brand.Cyan
    EvidenceQuality.POOR -> Brand.Amber
    EvidenceQuality.INSUFFICIENT -> Brand.Red
    EvidenceQuality.NOT_ASSESSED -> Brand.Grey
}

fun paintStatusColor(s: PaintStatus): Color = when (s) {
    PaintStatus.APPEARS_ORIGINAL -> Brand.Green
    PaintStatus.POSSIBLE_REPAINT, PaintStatus.POSSIBLE_REPAIR -> Brand.Amber
    PaintStatus.STRONG_REPAINT_INDICATORS -> Brand.Orange
    PaintStatus.POSSIBLE_PANEL_REPLACEMENT -> Brand.Red
    PaintStatus.INSUFFICIENT_EVIDENCE, PaintStatus.CANNOT_DETERMINE -> Brand.Grey
    PaintStatus.NOT_CHECKED -> Color(0x33808A99)
}

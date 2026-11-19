package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class BaddieExtendedColors(
    val border: Color,
    val borderSubtle: Color,
    val surfaceElevated: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val info: Color
)

@Immutable
data class BaddieSpacing(
    val cardPadding: Dp = 16.dp,
    val sectionGap: Dp = 14.dp,
    val itemGap: Dp = 10.dp
)

val LocalBaddieColors = staticCompositionLocalOf {
    BaddieExtendedColors(
        border = CharcoalBorder,
        borderSubtle = CharcoalBorderSubtle,
        surfaceElevated = CharcoalSurfaceElevated,
        textMuted = CharcoalTextMuted,
        success = StatusSuccess,
        warning = StatusWarning,
        info = StatusInfo
    )
}

val LocalBaddieSpacing = staticCompositionLocalOf { BaddieSpacing() }

val BaddieShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private fun accentTokens(preset: String): Triple<Color, Color, Color> = when (preset.uppercase()) {
    "SAGE" -> Triple(SagePrimary, SageSecondary, SageMuted)
    "SLATE" -> Triple(SlateBluePrimary, SlateBlueSecondary, SlateBlueMuted)
    "AMBER" -> Triple(WarmAmberPrimary, WarmAmberSecondary, WarmAmberMuted)
    else -> Triple(LavenderPrimary, LavenderSecondary, LavenderMuted)
}

private fun darkSchemeFor(accentPreset: String): ColorScheme {
    val (primary, secondary, container) = accentTokens(accentPreset)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF14111E),
        primaryContainer = container,
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color(0xFF14111E),
        secondaryContainer = CharcoalSurfaceHighest,
        onSecondaryContainer = CharcoalTextPrimary,
        tertiary = StatusInfo,
        onTertiary = CharcoalBg,
        background = CharcoalBg,
        onBackground = CharcoalTextPrimary,
        surface = CharcoalSurface,
        onSurface = CharcoalTextPrimary,
        surfaceVariant = CharcoalSurfaceElevated,
        onSurfaceVariant = CharcoalTextSecondary,
        surfaceContainerLowest = CharcoalBg,
        surfaceContainerLow = CharcoalSurface,
        surfaceContainer = CharcoalSurfaceElevated,
        surfaceContainerHigh = CharcoalSurfaceHighest,
        outline = CharcoalBorder,
        outlineVariant = CharcoalBorderSubtle,
        error = StatusError,
        onError = CharcoalBg
    )
}

private fun lightSchemeFor(accentPreset: String): ColorScheme {
    val (_, secondary, _) = accentTokens(accentPreset)
    val darkAccent = when (accentPreset.uppercase()) {
        "SAGE" -> Color(0xFF15803D)
        "SLATE" -> Color(0xFF2563EB)
        "AMBER" -> Color(0xFFD97706)
        else -> Color(0xFF6D28D9)
    }
    val softContainer = when (accentPreset.uppercase()) {
        "SAGE" -> Color(0xFFDCFCE7)
        "SLATE" -> Color(0xFFDBEAFE)
        "AMBER" -> Color(0xFFFEF3C7)
        else -> Color(0xFFEDE9FE)
    }
    return lightColorScheme(
        primary = darkAccent,
        onPrimary = Color.White,
        primaryContainer = softContainer,
        onPrimaryContainer = darkAccent,
        secondary = secondary,
        onSecondary = Color.White,
        background = LightBg,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceElevated,
        onSurfaceVariant = LightTextSecondary,
        surfaceContainerLowest = LightSurface,
        surfaceContainerLow = LightSurfaceElevated,
        surfaceContainer = LightSurfaceElevated,
        outline = LightBorder,
        outlineVariant = LightBorder,
        error = Color(0xFFDC2626),
        onError = Color.White
    )
}

@Composable
fun BaddieTheme(
    themeMode: String = "DARK",
    accentPreset: String = "LAVENDER",
    compactLayout: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }
    val colorScheme = if (useDark) darkSchemeFor(accentPreset) else lightSchemeFor(accentPreset)
    val extendedColors = if (useDark) {
        BaddieExtendedColors(
            border = CharcoalBorder,
            borderSubtle = CharcoalBorderSubtle,
            surfaceElevated = CharcoalSurfaceElevated,
            textMuted = CharcoalTextMuted,
            success = StatusSuccess,
            warning = StatusWarning,
            info = StatusInfo
        )
    } else {
        BaddieExtendedColors(
            border = LightBorder,
            borderSubtle = LightBorder,
            surfaceElevated = LightSurfaceElevated,
            textMuted = LightTextSecondary,
            success = Color(0xFF16A34A),
            warning = Color(0xFFD97706),
            info = Color(0xFF2563EB)
        )
    }
    val spacing = if (compactLayout) {
        BaddieSpacing(cardPadding = 12.dp, sectionGap = 10.dp, itemGap = 8.dp)
    } else {
        BaddieSpacing(cardPadding = 16.dp, sectionGap = 14.dp, itemGap = 10.dp)
    }

    CompositionLocalProvider(
        LocalBaddieColors provides extendedColors,
        LocalBaddieSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BaddieTypography,
            shapes = BaddieShapes,
            content = content
        )
    }
}

package com.example.suncareclinic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SuncareExtendedColors(
    val canvas: Color = ClinicalCanvas,
    val surface: Color = ClinicalSurface,
    val ink: Color = ClinicalInk,
    val muted: Color = ClinicalMuted,
    val border: Color = ClinicalBorder,
    val borderStrong: Color = ClinicalBorderStrong,
    val leaf: Color = ClinicalLeaf,
    val leafSoft: Color = ClinicalLeafSoft,
    val goldSoft: Color = ClinicalGoldSoft,
    val success: Color = ClinicalSuccess,
    val info: Color = ClinicalInfo,
    val warning: Color = ClinicalWarning,
    val warningSoft: Color = Suncare100,
    val warningBorder: Color = Suncare200,
    val danger: Color = ClinicalDanger,
    val dangerSoft: Color = ClinicalDangerSoft,
    val brandAccent: Color = Suncare400,
    val brandAccentPressed: Color = Suncare500,
    val brandText: Color = Suncare700,
)

private val LocalSuncareColors = staticCompositionLocalOf { SuncareExtendedColors() }

private val SunflowerLightColorScheme = lightColorScheme(
    primary = Suncare400,
    onPrimary = ClinicalInk,
    primaryContainer = Suncare100,
    onPrimaryContainer = Suncare900,
    secondary = ClinicalLeaf,
    onSecondary = Color.White,
    secondaryContainer = ClinicalLeafSoft,
    onSecondaryContainer = ClinicalInk,
    tertiary = Suncare700,
    onTertiary = Color.White,
    tertiaryContainer = ClinicalGoldSoft,
    onTertiaryContainer = Suncare900,
    background = ClinicalCanvas,
    onBackground = ClinicalInk,
    surface = ClinicalSurface,
    onSurface = ClinicalInk,
    surfaceVariant = Suncare50,
    onSurfaceVariant = ClinicalMuted,
    outline = ClinicalBorderStrong,
    outlineVariant = ClinicalBorder,
    error = ClinicalDanger,
    onError = Color.White,
    errorContainer = ClinicalDangerSoft,
    onErrorContainer = ClinicalDanger,
)

object SuncareTheme {
    val colors: SuncareExtendedColors
        @Composable
        get() = LocalSuncareColors.current
}

@Composable
fun SuncareClinicTheme(
    content: @Composable () -> Unit,
) {
    // Giữ nhận diện màu Sunflower Clinical đồng nhất trên mọi phiên bản Android (không bị dynamic color ghi đè).
    val extendedColors = SuncareExtendedColors()
    CompositionLocalProvider(LocalSuncareColors provides extendedColors) {
        MaterialTheme(
            colorScheme = SunflowerLightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

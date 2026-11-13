package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val titleAr: String) {
    SYSTEM("تلقائي حسب النظام"),
    LIGHT("الوضع الفاتح"),
    DARK("الوضع الداكن"),
}

enum class AppColorPalette(val titleAr: String) {
    OFFICIAL_NAVY_TEAL("الأزرق المالي الرسمي"),
    RED_WHITE("الأحمر والأبيض"),
}

@Immutable
data class FinancialColors(
    val success: Color,
    val onSuccess: Color,
    val danger: Color,
    val onDanger: Color,
    val warning: Color,
    val orange: Color,
    val turquoise: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

val LocalFinancialColors = staticCompositionLocalOf {
    FinancialColors(
        success = SuccessGreen,
        onSuccess = PureWhite,
        danger = DangerRed,
        onDanger = PureWhite,
        warning = WarningYellow,
        orange = AlertOrange,
        turquoise = TurquoiseAccent,
        heroStart = NavyDark,
        heroEnd = OceanBlue,
    )
}

private val OfficialLightColorScheme = lightColorScheme(
    primary = NavyDark,
    onPrimary = PureWhite,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = NavyDark,
    secondary = OceanBlue,
    onSecondary = PureWhite,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = NavyDark,
    tertiary = TurquoiseAccent,
    onTertiary = NavyDark,
    background = SoftIceBackground,
    onBackground = OnSurfaceLight,
    surface = PureWhite,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = DangerRed,
    onError = PureWhite,
)

private val OfficialDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = PureWhite,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = OnDarkSurface,
    tertiary = TurquoiseAccent,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = OnDarkSurface,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFF6B6B),
    onError = DarkBackground,
)

private val RedWhiteLightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = PureWhite,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = CrimsonDark,
    secondary = CrimsonDark,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFFEDE8),
    onSecondaryContainer = CrimsonDark,
    tertiary = CrimsonSecondary,
    onTertiary = PureWhite,
    background = CrimsonLightSurface,
    onBackground = Color(0xFF2B1212),
    surface = PureWhite,
    onSurface = Color(0xFF2B1212),
    surfaceVariant = Color(0xFFF3E2E2),
    onSurfaceVariant = Color(0xFF6B4848),
    outline = Color(0xFFDDC2C2),
    error = DangerRed,
    onError = PureWhite,
)

private val RedWhiteDarkColorScheme = darkColorScheme(
    primary = CrimsonDarkPrimary,
    onPrimary = CrimsonDarkBackground,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = PureWhite,
    secondary = CrimsonSecondary,
    onSecondary = CrimsonDarkBackground,
    secondaryContainer = Color(0xFF3A1D20),
    onSecondaryContainer = PureWhite,
    tertiary = WarningYellow,
    onTertiary = CrimsonDarkBackground,
    background = CrimsonDarkBackground,
    onBackground = Color(0xFFFCECEC),
    surface = CrimsonDarkSurface,
    onSurface = Color(0xFFFCECEC),
    surfaceVariant = Color(0xFF331C1F),
    onSurfaceVariant = Color(0xFFD9B8BA),
    outline = Color(0xFF5D3539),
    error = Color(0xFFFF6B6B),
    onError = CrimsonDarkBackground,
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SmartDebtTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.OFFICIAL_NAVY_TEAL,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when (colorPalette) {
        AppColorPalette.OFFICIAL_NAVY_TEAL -> if (isDark) OfficialDarkColorScheme else OfficialLightColorScheme
        AppColorPalette.RED_WHITE -> if (isDark) RedWhiteDarkColorScheme else RedWhiteLightColorScheme
    }

    val financialColors = when (colorPalette) {
        AppColorPalette.OFFICIAL_NAVY_TEAL -> FinancialColors(
            success = if (isDark) Color(0xFF38B87C) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = WarningYellow,
            orange = AlertOrange,
            turquoise = TurquoiseAccent,
            heroStart = NavyDark,
            heroEnd = OceanBlue,
        )
        AppColorPalette.RED_WHITE -> FinancialColors(
            success = if (isDark) Color(0xFF38B87C) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = WarningYellow,
            orange = AlertOrange,
            turquoise = if (isDark) Color(0xFFFFB088) else CrimsonSecondary,
            heroStart = CrimsonDark,
            heroEnd = CrimsonPrimary,
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalFinancialColors provides financialColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

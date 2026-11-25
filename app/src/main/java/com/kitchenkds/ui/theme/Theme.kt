package com.kitchenkds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kitchenkds.domain.model.ThemeMode

/**
 * 主题入口（V3）。
 *
 * themeMode 来自 DataStore：
 *   DARK   → 强制深色（覆盖系统设置）
 *   LIGHT  → 强制浅色（覆盖系统设置）
 *   SYSTEM → 跟随 isSystemInDarkTheme()
 *
 * 调色板走 LocalKdsColors，Material3 的 colorScheme 同步切换，
 * 保证背景、卡片、文字、分割线整套一起反转。
 */
@Composable
fun KitchenKdsTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val kdsColors = if (dark) DarkKdsColors else LightKdsColors

    val scheme = if (dark) {
        darkColorScheme(
            primary = kdsColors.accent,
            onPrimary = Color0,
            secondary = kdsColors.warning,
            onSecondary = Color0,
            error = kdsColors.danger,
            background = kdsColors.background,
            onBackground = kdsColors.textPrimary,
            surface = kdsColors.surface,
            onSurface = kdsColors.textPrimary,
            surfaceVariant = kdsColors.surfaceHigh,
            onSurfaceVariant = kdsColors.textSecondary,
            outline = kdsColors.divider
        )
    } else {
        lightColorScheme(
            primary = kdsColors.accent,
            onPrimary = Color0,
            secondary = kdsColors.warning,
            onSecondary = Color0,
            error = kdsColors.danger,
            background = kdsColors.background,
            onBackground = kdsColors.textPrimary,
            surface = kdsColors.surface,
            onSurface = kdsColors.textPrimary,
            surfaceVariant = kdsColors.surfaceHigh,
            onSurfaceVariant = kdsColors.textSecondary,
            outline = kdsColors.divider
        )
    }

    CompositionLocalProvider(LocalKdsColors provides kdsColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KdsTypography,
            content = content
        )
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0xFF000000)

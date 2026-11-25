package com.kitchenkds.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义化调色板（V3）。
 *
 * 组件里一律不许写死颜色，全部从 KdsTheme.colors 取，
 * 这样主题切换时背景、卡片底色、文字颜色才能整体反转。
 */
@Immutable
data class KdsColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val divider: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textDone: Color,

    val accent: Color,
    val warning: Color,
    val danger: Color,
    val success: Color,

    val foodAccent: Color,
    val drinkAccent: Color,

    /** 卡片底部状态栏：新单 / 旧单 / 文字 */
    val bottomNew: Color,
    val bottomOld: Color,
    val bottomText: Color,

    val scrim: Color
)

/** 暗黑（工业风，默认） */
val DarkKdsColors = KdsColors(
    background = Color(0xFF0E0F11),
    surface = Color(0xFF1A1C1F),
    surfaceHigh = Color(0xFF23262A),
    divider = Color(0xFF2C3035),

    textPrimary = Color(0xFFECEFF1),
    textSecondary = Color(0xFF90A4AE),
    textDone = Color(0xFF4A5257),

    accent = Color(0xFF29B6F6),
    warning = Color(0xFFF9A825),
    danger = Color(0xFFE53935),
    success = Color(0xFF43A047),

    foodAccent = Color(0xFFFFB74D),
    drinkAccent = Color(0xFF4DD0E1),

    bottomNew = Color(0xFF29B6F6),
    bottomOld = Color(0xFFF9A825),
    bottomText = Color.White,

    scrim = Color(0xCC000000)
)

/** 白色（强光环境用） */
val LightKdsColors = KdsColors(
    background = Color(0xFFF4F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFECEEF1),
    divider = Color(0xFFDCE0E5),

    textPrimary = Color(0xFF15181B),
    textSecondary = Color(0xFF5F6B73),
    textDone = Color(0xFFB0B8BF),

    accent = Color(0xFF0277BD),
    warning = Color(0xFFEF6C00),
    danger = Color(0xFFC62828),
    success = Color(0xFF2E7D32),

    foodAccent = Color(0xFFB26500),
    drinkAccent = Color(0xFF00695C),

    bottomNew = Color(0xFF29B6F6),
    bottomOld = Color(0xFFF9A825),
    bottomText = Color.White,

    scrim = Color(0x99FFFFFF)
)

val LocalKdsColors = staticCompositionLocalOf { DarkKdsColors }

object KdsTheme {
    val colors: KdsColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKdsColors.current
}

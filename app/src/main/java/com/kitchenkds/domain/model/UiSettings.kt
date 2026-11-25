package com.kitchenkds.domain.model

import androidx.compose.runtime.Immutable

/** 主题模式：深色 / 浅色 / 跟随系统 */
enum class ThemeMode(val label: String) {
    DARK("暗黑模式"),
    LIGHT("白色模式"),
    SYSTEM("跟随系统");

    companion object {
        fun fromName(raw: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

/**
 * 全局 UI 偏好（DataStore 持久化）。
 *
 * V4.3 尺寸调节范围全面扩大（14 寸桌面模式下原来的上限太小）：
 *   cardWidth        180 ~ 900 dp（步进 20 dp）        ← 原 200~400 / 10
 *   cardHeightRatio  30% ~ 100% 屏高（步进 5%）         ← 原写死 screenHeightDp/2
 *   fontScale        0.5 ~ 4.5 x（步进 0.1）            ← 原 0.8~1.5 / 0.05
 *   itemSpacing      0 ~ 48 dp（步进 2 dp）             ← 原 0~16 / 1
 *   headerHeight     32 ~ 180 dp（步进 4 dp）           ← 蓝条最小高度，内容更高时自动撑开
 *   headerScale      0.5 ~ 3.0 x（步进 0.1）            ← 蓝条字号附加倍率
 *   themeMode        主题模式
 *
 * 蓝条最终字号 = 基础字号 × fontScale × headerScale，
 * 桌号 / 时间 / 计时 全部走同一条通路，保证严格联动。
 */
@Immutable
data class UiSettings(
    val cardWidth: Float = 260f,
    val itemSpacing: Float = 4f,
    val fontScale: Float = 1.0f,
    /** 卡片最大高度占屏比：0.30 ~ 1.00（1.00 = 整屏高度，等同自动撑开） */
    val cardHeightRatio: Float = 0.85f,
    /** 卡片顶部蓝条的最小高度（dp） */
    val headerHeight: Float = 44f,
    /** 蓝条字号附加倍率 */
    val headerScale: Float = 1.0f,
    val themeMode: ThemeMode = ThemeMode.DARK,

    // ── V5 音效 ──
    val soundEnabled: Boolean = true,
    /** 内置音效 "0".."4"，或 "CUSTOM"（用户从手机导入的音频） */
    val soundMode: String = "0",
    /** 自定义音效的原始文件名，只用于设置页展示 */
    val customSoundName: String = "",
    /** true = USAGE_ALARM 闹钟通道（静音/震动也响）；false = USAGE_MEDIA */
    val soundUseAlarmChannel: Boolean = true,
    val soundTimes: Int = 2,
    val soundIntervalSec: Int = 1,

    // ── V4 定时重置 ──
    val autoResetEnabled: Boolean = false,
    val autoResetHour: Int = 4,
    val autoResetMinute: Int = 0
) {
    /** 卡片宽度可选档位（180 ~ 900，步进 20dp） */
    val cardWidthSteps: List<Float>
        get() {
            val out = ArrayList<Float>(((CARD_WIDTH_MAX - CARD_WIDTH_MIN) / CARD_WIDTH_STEP).toInt() + 1)
            var v = CARD_WIDTH_MIN
            while (v <= CARD_WIDTH_MAX) {
                out.add(v)
                v += CARD_WIDTH_STEP
            }
            return out
        }

    /** 已按倍率算好的蓝条字号倍率（桌号/时间/计时共用） */
    val headerTextScale: Float get() = fontScale * headerScale

    companion object {
        const val CARD_WIDTH_MIN = 180f
        const val CARD_WIDTH_MAX = 900f
        const val CARD_WIDTH_STEP = 20f

        const val ITEM_SPACING_MIN = 0f
        const val ITEM_SPACING_MAX = 48f
        const val ITEM_SPACING_STEP = 2f

        const val FONT_SCALE_MIN = 0.5f
        const val FONT_SCALE_MAX = 4.5f
        const val FONT_SCALE_STEP = 0.1f

        const val CARD_HEIGHT_RATIO_MIN = 0.30f
        const val CARD_HEIGHT_RATIO_MAX = 1.00f
        const val CARD_HEIGHT_RATIO_STEP = 0.05f

        const val HEADER_HEIGHT_MIN = 32f
        const val HEADER_HEIGHT_MAX = 180f
        const val HEADER_HEIGHT_STEP = 4f

        const val HEADER_SCALE_MIN = 0.5f
        const val HEADER_SCALE_MAX = 3.0f
        const val HEADER_SCALE_STEP = 0.1f

        const val SOUND_TIMES_MIN = 1
        const val SOUND_TIMES_MAX = 5
        const val SOUND_INTERVAL_MIN = 0
        const val SOUND_INTERVAL_MAX = 10
    }

    fun sanitized(): UiSettings = copy(
        cardWidth = cardWidth.coerceIn(CARD_WIDTH_MIN, CARD_WIDTH_MAX),
        itemSpacing = itemSpacing.coerceIn(ITEM_SPACING_MIN, ITEM_SPACING_MAX),
        fontScale = fontScale.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX),
        cardHeightRatio = cardHeightRatio.coerceIn(CARD_HEIGHT_RATIO_MIN, CARD_HEIGHT_RATIO_MAX),
        headerHeight = headerHeight.coerceIn(HEADER_HEIGHT_MIN, HEADER_HEIGHT_MAX),
        headerScale = headerScale.coerceIn(HEADER_SCALE_MIN, HEADER_SCALE_MAX),
        // 音效模式白名单：非法值一律回落第 1 个内置音，杜绝"存在但播不出"的脏配置
        soundMode = if (AlertSounds.isValidMode(soundMode)) soundMode else AlertSounds.fallback().mode,
        soundTimes = soundTimes.coerceIn(SOUND_TIMES_MIN, SOUND_TIMES_MAX),
        soundIntervalSec = soundIntervalSec.coerceIn(SOUND_INTERVAL_MIN, SOUND_INTERVAL_MAX),
        autoResetHour = autoResetHour.coerceIn(0, 23),
        autoResetMinute = autoResetMinute.coerceIn(0, 59)
    )
}

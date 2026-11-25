package com.kitchenkds.domain.model

/**
 * 内置提示音目录（V5）。
 *
 * 每一条都对应一个真实存在的 res/raw 资源（alert_01 ... alert_05，WAV 44.1kHz 单声道），
 * 不再依赖"运行时合成后塞进 cacheDir"的旧方案 —— 旧方案在部分机型上 SoundPool 解码失败，
 * 这正是"换手机后提示音失效"的根因之一。
 *
 * mode 就是 DataStore 里 soundMode 的取值："0".."4" 或 "CUSTOM"。
 */
object AlertSounds {

    const val MODE_CUSTOM = "CUSTOM"

    data class Builtin(
        val mode: String,
        /** res/raw 下的资源名（不含扩展名，MediaPlayer 通过它拿 resId） */
        val rawName: String,
        val label: String,
        /** 一句话说明，写给后厨看 */
        val note: String
    )

    val builtins: List<Builtin> = listOf(
        Builtin("0", "alert_01", "叮咚（双音）", "经典门铃，穿透力强"),
        Builtin("1", "alert_02", "清脆铃（单响）", "短促清脆，适合安静厨房"),
        Builtin("2", "alert_03", "低频双响", "低沉厚重，嘈杂环境不刺耳"),
        Builtin("3", "alert_04", "韩式点单（三连音）", "韩国 POS 常见的上行三音"),
        Builtin("4", "alert_05", "长警报（1.2 秒）", "最吵的厨房用，穿透力最强")
    )

    val modes: List<String> = builtins.map { it.mode }

    fun byMode(mode: String): Builtin? = builtins.firstOrNull { it.mode == mode }

    fun isCustom(mode: String): Boolean = mode == MODE_CUSTOM

    /** 兜底档位：任何非法值都回落到第 1 个内置音 */
    fun fallback(): Builtin = builtins[0]

    /** 设置页 / 摘要行通用文案 */
    fun labelOf(mode: String, customFileName: String = ""): String {
        if (isCustom(mode)) {
            return if (customFileName.isBlank()) "自定义提示音（未选择）" else "自定义：" + customFileName
        }
        return byMode(mode)?.label ?: fallback().label
    }

    fun isValidMode(mode: String): Boolean = isCustom(mode) || modes.contains(mode)
}

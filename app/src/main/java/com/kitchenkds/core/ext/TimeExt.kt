package com.kitchenkds.core.ext

import java.util.Locale
import kotlin.math.max

/** 接单经过时间 → "MM:SS"（超过 99 分钟不再回绕，直接继续累加） */
fun formatElapsed(elapsedMillis: Long): String {
    val totalSec = max(0L, elapsedMillis) / 1000L
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/** 超时等级：0 = 正常(<3min)，1 = 注意(3~8min)，2 = 催单(>8min) */
fun urgencyLevel(elapsedMillis: Long): Int = when {
    elapsedMillis < 3L * 60_000L -> 0
    elapsedMillis < 8L * 60_000L -> 1
    else -> 2
}

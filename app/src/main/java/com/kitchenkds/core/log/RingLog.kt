package com.kitchenkds.core.log

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 轻量环形日志：后厨现场没有 Logcat，出问题时长按顶栏即可把最近的日志吐出来。
 * 不写文件、不依赖任何库，避免额外权限与 IO 抖动。
 *
 * V4.3：容量 200 → 1000。FrameSplitter 现在会把每张单子的原始行逐行写入，
 * 一张单子约 20~40 行，200 条只够存 5 张单，遇到"折行/取消单分裂"根本回看不出原因。
 */
object RingLog {

    private const val CAPACITY = 1000

    private val buffer = ArrayDeque<String>(CAPACITY)
    private val lock = Any()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, msg: String) = push("D", tag, msg)
    fun w(tag: String, msg: String) = push("W", tag, msg)
    fun e(tag: String, msg: String) = push("E", tag, msg)

    private fun push(level: String, tag: String, msg: String) {
        val stamp = fmt.format(Date())
        val line = stamp + " " + level + "/" + tag + ": " + msg
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(line)
        }
    }

    fun dump(): String = synchronized(lock) { buffer.joinToString("\n") }

    fun clear() = synchronized(lock) { buffer.clear() }
}

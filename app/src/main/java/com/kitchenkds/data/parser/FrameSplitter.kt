package com.kitchenkds.data.parser

import com.kitchenkds.core.log.RingLog
import com.kitchenkds.domain.model.OrderKind

data class RawFrame(
    val kind: OrderKind,
    val lines: List<String>,
    val receivedAtMillis: Long
) {
    fun text(): String = lines.joinToString("\n")
}

/**
 * 订单封包器。打印机是连续字节流，没有明确结束符，所以用双保险：
 *   ① 标记切分：遇到 [주문서] / [재주문서] 收掉上一帧，开启新帧
 *   ② 空闲封包：串口静默超过 IDLE_FLUSH_MS 且当前帧有内容 → 强制封包
 *      （单张单子尾部没有下一张时只能靠②）
 */
class FrameSplitter {

    companion object {
        private const val TAG = "Frame"
        const val IDLE_FLUSH_MS = 2500L
        private const val MAX_LINES_PER_FRAME = 300
        private val markerRegex =
            Regex("\\[\\s*(재\\s*주\\s*문\\s*서|주\\s*문\\s*서)\\s*\\]|^\\s*(재\\s*주\\s*문\\s*서|주\\s*문\\s*서)\\s*$")
    }

    private val buffer = ArrayList<String>()
    private var kind = OrderKind.UNKNOWN
    private var lastActivity = System.currentTimeMillis()

    val pendingLines: Int get() = buffer.size
    val currentKind: OrderKind get() = kind

    fun reset() {
        buffer.clear()
        kind = OrderKind.UNKNOWN
        lastActivity = System.currentTimeMillis()
    }

    fun touch() {
        lastActivity = System.currentTimeMillis()
    }

    /** 喂入若干行，返回本轮被标记切分出来的所有完整帧 */
    fun feedLines(lines: List<String>, nowMillis: Long): List<RawFrame> {
        if (lines.isEmpty()) return emptyList()
        val frames = ArrayList<RawFrame>(2)
        for (line in lines) {
            lastActivity = nowMillis
            if (markerRegex.containsMatchIn(line)) {
                if (buffer.isNotEmpty()) {
                    frames.add(snapshot(kind, System.currentTimeMillis()))
                    buffer.clear()
                }
                kind = detectKind(line)
                continue
            }
            if (NoiseLineFilter.isNoise(line)) continue
            if (buffer.size >= MAX_LINES_PER_FRAME) continue
            buffer.add(line)
        }
        return frames
    }

    /** 空闲超时检查：由上层在串口静默时调用 */
    fun maybeFlushIdle(nowMillis: Long): RawFrame? {
        if (buffer.isEmpty()) return null
        if (nowMillis - lastActivity < IDLE_FLUSH_MS) return null
        val frame = snapshot(kind, lastActivity)
        buffer.clear()
        kind = OrderKind.UNKNOWN
        return frame
    }

    /**
     * 【V4.3】封包出口：把这一帧的原始行逐行打进 RingLog。
     *
     * 现场排查折行 / 取消单 / 乱码时，唯一可靠的事实来源就是"POS 到底发了哪几行"。
     * 因此每一行都单独记一条（带序号），导出运行日志时能原样还原整张单据的分行结构。
     */
    private fun snapshot(frameKind: OrderKind, atMillis: Long): RawFrame {
        val copy = ArrayList(buffer)
        RingLog.d(TAG, "frame flush kind=" + frameKind + " lines=" + copy.size)
        copy.forEachIndexed { index, raw ->
            RingLog.d(TAG, "  #" + (index + 1) + " | " + raw)
        }
        return RawFrame(frameKind, copy, atMillis)
    }

    private fun detectKind(line: String): OrderKind {
        val compact = line.replace(Regex("\\s+"), "")
        return when {
            compact.contains("재주문서") -> OrderKind.REPRINT
            compact.contains("주문서") -> OrderKind.NEW
            else -> OrderKind.UNKNOWN
        }
    }
}

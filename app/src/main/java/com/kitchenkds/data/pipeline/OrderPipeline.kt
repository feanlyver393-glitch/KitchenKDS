package com.kitchenkds.data.pipeline

import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.decode.EscPosByteFilter
import com.kitchenkds.data.decode.EucKrDecoder
import com.kitchenkds.data.decode.TextNormalizer
import com.kitchenkds.data.parser.FrameSplitter
import com.kitchenkds.data.parser.RawFrame

/**
 * 全链路流水线（必须单线程串行调用，内部无锁）：
 *   原始字节 → EscPosByteFilter(字节级) → EucKrDecoder(编码级)
 *            → TextNormalizer(字符级) → 行缓冲 → FrameSplitter(封包)
 *
 * 为什么不用"整体解码再正则"：见 EscPosByteFilter 的类注释。
 */
class OrderPipeline {

    private companion object {
        const val TAG = "Pipeline"
        /** 静默超过该时长，认为行缓冲里的残余就是完整一行 */
        const val RESIDUAL_FLUSH_MS = 600L
    }

    private val byteFilter = EscPosByteFilter()
    private val decoder = EucKrDecoder()
    private val splitter = FrameSplitter()

    private val lineBuffer = StringBuilder(1024)
    private var lastDataAt = 0L

    fun reset() {
        byteFilter.reset()
        decoder.reset()
        splitter.reset()
        lineBuffer.setLength(0)
        lastDataAt = 0L
    }

    fun onData(bytes: ByteArray, nowMillis: Long, out: (RawFrame) -> Unit) {
        lastDataAt = nowMillis

        val cleaned = byteFilter.feed(bytes)
        if (cleaned.isEmpty()) return
        val decoded = decoder.decode(cleaned)
        if (decoded.isEmpty()) return
        val text = TextNormalizer.normalize(decoded)
        if (text.isEmpty()) return

        lineBuffer.append(text)
        val complete = drainCompleteLines()
        if (complete.isNotEmpty()) {
            for (frame in splitter.feedLines(complete, nowMillis)) out(frame)
        }
    }

    fun onIdle(nowMillis: Long, out: (RawFrame) -> Unit) {
        // 先处理"没有换行符结尾但已经打印完"的残行
        if (lineBuffer.isNotEmpty() && lastDataAt > 0 && nowMillis - lastDataAt >= RESIDUAL_FLUSH_MS) {
            val residual = lineBuffer.toString().trim()
            lineBuffer.setLength(0)
            if (residual.isNotEmpty()) {
                for (frame in splitter.feedLines(listOf(residual), nowMillis)) out(frame)
            }
        }
        splitter.maybeFlushIdle(nowMillis)?.let(out)
    }

    private fun drainCompleteLines(): List<String> {
        val result = ArrayList<String>(8)
        var start = 0
        var idx = lineBuffer.indexOf("\n", 0)
        while (idx >= 0) {
            val line = lineBuffer.substring(start, idx)
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) result.add(trimmed)
            start = idx + 1
            idx = lineBuffer.indexOf("\n", start)
        }
        if (start > 0) lineBuffer.delete(0, start)
        // 防御：单行超过 4KB 说明流已乱，直接丢弃，避免内存膨胀
        if (lineBuffer.length > 4096) {
            RingLog.w(TAG, "line buffer overflow, dropped")
            lineBuffer.setLength(0)
        }
        return result
    }
}

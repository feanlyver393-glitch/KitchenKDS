package com.kitchenkds.data.decode

import com.kitchenkds.core.log.RingLog
import java.nio.charset.Charset

/**
 * 【第②层 · 编码级】EUC-KR / CP949 增量解码器。
 *
 * 增量是关键：USB 一次读回来 64 字节，很可能把最后一个韩文双字节切成两半。
 * 无脑 new String(chunk) 会在每个包边界产生一个替换符，订单行被切断。
 * 这里把不完整的尾字节挂到 pending，等下一个包拼上再解。
 */
class EucKrDecoder {

    private companion object {
        const val TAG = "EucKr"
        /** 最多挂 1 个半截字符（CP949 最大 2 字节），超了说明流已乱，丢弃 */
        const val MAX_PENDING = 4
    }

    val charset: Charset = resolveCharset()
    private var pending = ByteArray(0)

    private fun resolveCharset(): Charset {
        for (name in listOf("x-windows-949", "CP949", "EUC-KR", "MS949")) {
            try {
                val cs = Charset.forName(name)
                RingLog.d(TAG, "charset = " + cs.name())
                return cs
            } catch (t: Throwable) {
                // 试下一个
            }
        }
        RingLog.e(TAG, "no EUC-KR family charset, fallback UTF-8")
        return Charsets.UTF_8
    }

    fun decode(chunk: ByteArray): String {
        if (chunk.isEmpty()) return ""
        val data: ByteArray = if (pending.isEmpty()) chunk else pending + chunk

        var i = 0
        var completeEnd = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            if (b < 0x80) {
                i += 1
                completeEnd = i
            } else {
                if (i + 1 >= data.size) break     // 半截双字节，挂起
                i += 2
                completeEnd = i
            }
        }

        pending = if (completeEnd < data.size) data.copyOfRange(completeEnd, data.size) else ByteArray(0)
        if (pending.size > MAX_PENDING) pending = ByteArray(0)

        if (completeEnd == 0) return ""
        return String(data, 0, completeEnd, charset)
    }

    fun reset() {
        pending = ByteArray(0)
    }
}

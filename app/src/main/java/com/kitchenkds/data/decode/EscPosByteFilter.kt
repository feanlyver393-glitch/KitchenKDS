package com.kitchenkds.data.decode

import java.io.ByteArrayOutputStream

/**
 * 【第①层 · 字节级】ESC/POS 指令剥离器。
 *
 * 为什么必须在解码前做字节级处理：
 *   EUC-KR(CP949) 的 lead byte 范围 0xA1..0xFE，trail byte 范围 0x41..0xFE；
 *   而 ESC=0x1B、FS=0x1C、GS=0x1D 恒小于 0x40，
 *   所以这三个控制字节绝不可能出现在任何韩文字符的内部。
 *   → 在这三个字节处切分，永远不会切坏一个韩文双字节字符。
 *
 * 反过来做（先整体 new String(bytes) 再正则删乱码）会把 0x1B 与后续韩文
 * lead byte 错配成非法序列，产生大范围替换符，原文彻底不可恢复。
 *
 * 本类有状态，可跨 chunk 拼接（一个 ESC 序列被 TCP/USB 包切断也能正确跳过）。
 */
class EscPosByteFilter {

    private companion object {
        const val ESC = 0x1B
        const val FS = 0x1C
        const val GS = 0x1D

        const val STATE_TEXT = 0
        const val STATE_CMD = 1
        const val STATE_SKIP = 2
        const val STATE_DYN_LEN = 3
        const val STATE_SKIP_RUN = 4

        const val DYN_NONE = 0
        const val DYN_PAREN = 1

        /** 0x1D 0x6B 条码数据最长保护，防止吞掉整条订单 */
        const val MAX_RUN = 64
    }

    private var state = STATE_TEXT
    private var prefix = 0
    private var remain = 0
    private var dynKind = DYN_NONE
    private var dynParam = 0
    private var runCount = 0

    fun reset() {
        state = STATE_TEXT
        prefix = 0
        remain = 0
        dynKind = DYN_NONE
        dynParam = 0
        runCount = 0
    }

    fun feed(input: ByteArray, off: Int = 0, len: Int = input.size): ByteArray {
        val out = ByteArrayOutputStream(len)
        var i = off
        val end = off + len
        while (i < end) {
            val b = input[i].toInt() and 0xFF
            when (state) {
                STATE_TEXT -> {
                    when {
                        b == ESC || b == GS || b == FS -> {
                            prefix = b
                            state = STATE_CMD
                        }
                        b == 0x00 || b == 0x04 || b == 0x11 || b == 0x13 || b == 0x14 -> {
                            // NUL / EOT / DC1 / DC3 / DC4：打印机状态查询与填充，直接丢
                        }
                        b == 0x0D -> out.write(0x0A)      // CR → LF
                        b == 0x09 -> out.write(0x20)      // HT → 空格
                        else -> out.write(b)
                    }
                }

                STATE_CMD -> {
                    state = STATE_TEXT
                    when (prefix) {
                        ESC -> dispatchEsc(b, out)
                        GS -> dispatchGs(b, out)
                        FS -> dispatchFs(b, out)
                    }
                }

                STATE_SKIP -> {
                    remain--
                    if (remain <= 0) state = STATE_TEXT
                }

                STATE_DYN_LEN -> {
                    // GS ( / FS ( 的 pL pH 两字节
                    if (dynParam == 0) {
                        dynParam = b
                    } else {
                        remain = dynParam + b * 256
                        dynParam = 0
                        dynKind = DYN_NONE
                        state = if (remain > 0) STATE_SKIP else STATE_TEXT
                    }
                }

                STATE_SKIP_RUN -> {
                    runCount++
                    if (b == 0x00 || runCount > MAX_RUN) state = STATE_TEXT
                }
            }
            i++
        }
        return out.toByteArray()
    }

    private fun dispatchEsc(cmd: Int, out: ByteArrayOutputStream) {
        val n = when (cmd) {
            0x40 -> 0                       // ESC @  初始化
            0x21 -> 1                       // ESC !  字体
            0x24 -> 2                       // ESC $  绝对定位
            0x2A -> 3                       // ESC *  位图（后续像素数据近似跳过）
            0x2D -> 1                       // ESC -  下划线
            0x33 -> 1                       // ESC 3  行距
            0x45 -> 1                       // ESC E  粗体
            0x47 -> 1                       // ESC G  双击
            0x4A -> 1                       // ESC J  走纸
            0x4B -> 0                       // ESC K  旧式位图
            0x4D -> 1                       // ESC M  字体
            0x52 -> 1                       // ESC R  国际字符集
            0x56 -> 1                       // ESC V  旋转
            0x61 -> 1                       // ESC a  对齐
            0x64 -> 1                       // ESC d  走纸 n 行
            0x74 -> 1                       // ESC t  代码页选择（EUC-KR 常在此声明）
            0x78 -> 1                       // ESC x
            0x7B -> 1                       // ESC {  倒置
            else -> 1                       // 未知 ESC：按单参数丢弃，最安全
        }
        if (n > 0) {
            remain = n
            state = STATE_SKIP
        }
    }

    private fun dispatchGs(cmd: Int, out: ByteArrayOutputStream) {
        when (cmd) {
            0x21 -> setFixed(1)             // GS !  字号
            0x42 -> state = STATE_TEXT      // GS B  反白
            0x48 -> setFixed(1)             // GS H  HRI 位置
            0x49 -> setFixed(1)             // GS I  反白反转
            0x56 -> setFixed(1)             // GS V  切纸
            0x57 -> setFixed(2)             // GS W  条码宽度/高度
            0x68 -> setFixed(1)             // GS h  条码高度
            0x69 -> state = STATE_TEXT      // GS i  二维码
            0x77 -> setFixed(1)             // GS w  条码模块宽度
            0x2A -> setFixed(5)             // GS *  位图（像素数据量巨大，实际订单流不出现）
            0x76 -> setFixed(8)             // GS v  光栅位图参数
            0x6B -> {                       // GS k  条码：数据以 0x00 结束
                state = STATE_SKIP_RUN
                runCount = 0
            }
            0x28 -> {                       // GS (  扩展命令：pL pH len
                dynKind = DYN_PAREN
                dynParam = 0
                state = STATE_DYN_LEN
            }
            else -> setFixed(1)
        }
    }

    private fun dispatchFs(cmd: Int, out: ByteArrayOutputStream) {
        when (cmd) {
            0x26 -> state = STATE_TEXT      // FS &  进入汉字模式
            0x2E -> state = STATE_TEXT      // FS .  取消汉字模式
            0x43 -> state = STATE_TEXT      // FS C  代码页
            0x72 -> state = STATE_TEXT      // FS r
            0x21 -> setFixed(1)             // FS !  汉字字号
            0x28 -> {
                dynKind = DYN_PAREN
                dynParam = 0
                state = STATE_DYN_LEN
            }
            else -> state = STATE_TEXT      // FS 与汉字命令多数无参数
        }
    }

    private fun setFixed(n: Int) {
        remain = n
        state = if (n > 0) STATE_SKIP else STATE_TEXT
    }
}

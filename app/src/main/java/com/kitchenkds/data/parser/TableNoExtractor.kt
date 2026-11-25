package com.kitchenkds.data.parser

import com.kitchenkds.core.log.RingLog

/**
 * 桌号提取（V3）。
 *
 * 现场实测的高频畸形格式全部吃下：
 *   [] 13        空方括号 + 空格 + 数字（POS 排版错位，最高频）
 *   [13]         正常方括号
 *   테이블: 13   带全角/半角冒号
 *   테이블 13 / TABLE 13 / T13 / T:13 / NO.13 / 13번
 *
 * 匹配顺序即可信度顺序：先认最具体的，绝不认裸数字（那是数量或价格）。
 * 全部不中返回 "?"，让后厨一眼看出需要人工确认，不编造桌号。
 */
object TableNoExtractor {

    private const val TAG = "TableNo"

    private val patterns: List<Pair<String, Regex>> = listOf(
        // [] 13 / []: 13 / []-13  —— V3 新增，现场最高频
        "emptyBracket" to Regex("\\[\\s*\\]\\s*[:：\\-]?\\s*([A-Za-z]?\\d{1,3})"),
        // [13] / [T13]
        "bracket" to Regex("\\[\\s*([A-Za-z]?\\d{1,3})\\s*\\]"),
        // 테이블: 13 / 테 이 블 : 13 / 테이블-13
        "tableKr" to Regex("테\\s*이\\s*블\\s*[:：\\-]?\\s*([A-Za-z]?\\d{1,3})"),
        // TABLE 13 / TABLE:13
        "tableEn" to Regex("TABLE\\s*[:：\\-]?\\s*([A-Za-z]?\\d{1,3})", RegexOption.IGNORE_CASE),
        // NO.13 / NO:13
        "no" to Regex("\\bNO\\.?\\s*[:：\\-]?\\s*([A-Za-z]?\\d{1,3})", RegexOption.IGNORE_CASE),
        // T13 / T:13 / T-13（前面必须是行首或分隔符，防 "SET A" 误伤）
        "tPrefix" to Regex("(?:^|[\\s\\[\\()])T\\s*[:：\\-]?\\s*(\\d{1,3})(?![0-9])"),
        // 13번 / 13번테이블
        "beon" to Regex("([A-Za-z]?\\d{1,3})\\s*번")
    )

    fun extract(lines: List<String>): String {
        for (line in lines) {
            if (NoiseLineFilter.isMarker(line)) continue
            val hit = matchLine(line)
            if (hit != null) {
                RingLog.d(TAG, "table=" + hit + "  from: " + line.take(40))
                return hit
            }
        }
        RingLog.w(TAG, "table NOT FOUND in " + lines.size + " lines")
        return "?"
    }

    private fun matchLine(line: String): String? {
        for ((_, regex) in patterns) {
            val m = regex.find(line) ?: continue
            val value = m.groupValues.getOrNull(1)?.trim().orEmpty()
            if (value.isNotEmpty()) return value.uppercase()
        }
        return null
    }

    /** 供 OrderLineParser 复用，避免"桌号判定"和"菜名判定"两套逻辑打架 */
    fun looksLikeTableLine(line: String): Boolean = matchLine(line) != null
}

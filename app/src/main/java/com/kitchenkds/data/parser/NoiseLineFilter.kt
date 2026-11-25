package com.kitchenkds.data.parser

import com.kitchenkds.data.decode.TextNormalizer

/**
 * 订单行噪声过滤（V4.2）。
 *
 * V4.2 新增：前台自助点餐机的系统行
 *   "주문자:주문전용 2026-11"
 *   "주  문  자 : 주 문 전 용"
 * 这类行以「주문자 / 주문전용」开头，是设备自身的单据元信息，
 * 不是菜品。不拦的话会被解析成一道叫「주문자」的假菜上屏。
 */
object NoiseLineFilter {

    /** 表头关键词：按长度降序，避免短词先把长词切碎 */
    private val HEADER_KEYWORDS = listOf(
        "상품명", "상품", "품명", "품목", "메뉴명", "메뉴",
        "수량", "단가", "금액", "구분", "합계", "총액", "총합",
        "번호", "일자", "시간", "결제", "승인", "카드", "현금", "잔액"
    ).sortedByDescending { it.length }

    /** 前台系统噪音词：整行只要含其中之一就直接丢弃 */
    private val SYSTEM_NOISE_TOKENS = listOf(
        "주문자", "주문전용", "주문접수", "주문번호", "영수증", "매출"
    )

    /**
     * V4.3 单据标题词：把整行里的空格与 [ ] ( ) 全部抹掉后再判定，
     * 因此 "주 문 서]"、"[ 주 방 주 문 서 ]"、"영 수 증" 这类对齐排版一律命中。
     */
    private val TITLE_TOKENS = listOf("주방주문서", "주문서", "영수증")

    private val noisePatterns = listOf(
        Regex("^\\s*$"),
        Regex("^[\\-=*_.~+·•]{2,}$"),
        Regex("^(합\\s*계|총\\s*액|소\\s*계|TOTAL|SUBTOTAL)", RegexOption.IGNORE_CASE),
        Regex("^\\s*\\d{1,2}\\s*[:\\-]\\s*\\d{2}"),
        Regex("^\\s*(전\\s*화|TEL|사업자|대\\s*표|주\\s*소|업\\s*체|상\\s*호)"),
        Regex("(감사합니다|이용해\\s*주셔서|다시\\s*방문|맛있게\\s*드세요|주문\\s*완료)"),
        Regex("[￦₩]"),
        Regex("^\\s*\\d+\\s*원\\s*$"),
        Regex("^\\s*[-]?\\s*\\d{2,}\\s*$"),
        Regex("^(NO|No|no)\\.?\\s*$"),
        Regex("^\\s*[=]{2,}.*[=]{2,}\\s*$"),
        // 2026-11 / 2026.11.04 这类纯日期行
        Regex("^\\s*\\d{4}[-./]\\d{1,2}([-./]\\d{1,2})?\\s*$")
    )

    private val marker =
        Regex("\\[\\s*(재\\s*주\\s*문\\s*서|주\\s*문\\s*서)\\s*\\]|^\\s*(재\\s*주\\s*문\\s*서|주\\s*문\\s*서)")

    fun isMarker(line: String): Boolean = marker.containsMatchIn(line)

    fun isNoise(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return true
        if (TextNormalizer.isDecorLine(t)) return true
        if (isTitleLine(t)) return true
        if (isSystemNoise(t)) return true
        if (isHeaderLine(t)) return true

        for (p in noisePatterns) {
            if (p.containsMatchIn(t)) return true
        }
        if (Regex("\\d{1,3}(,\\d{3})+").containsMatchIn(t)) return true
        return false
    }

    /**
     * V4.3 单据标题拦截：
     *   "주 문 서]"            → 抹掉空格/括号 → "주문서"    → 命中
     *   "[주방주문서]"         → "주방주문서"                → 命中
     *   "주 방 주 문 서  16"   → "주문서16"                  → 命中
     * 只认标题词本身，正常菜名里不会出现这几个词，不会误杀。
     */
    fun isTitleLine(line: String): Boolean {
        val compact = line.replace(Regex("[\\[\\]()\\{\\}]"), "").replace(Regex("\\s+"), "")
        if (compact.isEmpty()) return false
        for (token in TITLE_TOKENS) {
            if (compact.contains(token)) return true
        }
        return false
    }

    /**
     * 前台系统噪音：去空格后包含「주문자 / 주문전용」等词 → 整行丢弃。
     * 例："주문자:주문전용 2026-11" → compact "주문자:주문전용2026-11" → 命中
     */
    fun isSystemNoise(line: String): Boolean {
        val compact = line.replace(Regex("\\s+"), "").lowercase()
        if (compact.isEmpty()) return false
        for (token in SYSTEM_NOISE_TOKENS) {
            if (compact.contains(token)) return true
        }
        return false
    }

    /**
     * 去空格表头判定：
     *   "상 품 명　수 량"    → compact "상품명수량" → 剥掉关键词后为空 → 表头 ✔
     *   "명품양갈비      2" → compact "명품양갈비2" → 剥不掉 → 不是表头 ✔
     */
    fun isHeaderLine(line: String): Boolean {
        val compact = line.replace(Regex("\\s+"), "").lowercase()
        if (compact.isEmpty()) return false
        if (compact.all { it.isDigit() || it == '.' || it == ',' || it == '-' }) return false

        var rest = compact
        for (kw in HEADER_KEYWORDS) {
            if (rest.contains(kw)) rest = rest.replace(kw, "")
        }
        if (rest.isEmpty()) return true

        return rest.all { !it.isLetterOrDigit() }
    }
}

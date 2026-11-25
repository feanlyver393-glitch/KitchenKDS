package com.kitchenkds.data.parser

import com.kitchenkds.core.log.RingLog

data class ParsedLine(
    val name: String,
    val quantity: Int,
    val unitHint: String? = null
)

/**
 * 菜品行解析（V3）。
 *
 * 相对 V2 的修正：
 *   表头判定统一交给 NoiseLineFilter.isHeaderLine（去空格后关键词吃空判定），
 *   "상 품 명 수량"、"단 가"、"금 액" 这类对齐空格排版也能整行拦截。
 *
 * 保留 V2 的两条铁律：
 *   ① 数量解析失败 ≠ 无效行 —— 식사 / 굴 / 부추 这类短词名必须活下来（qty = 1）
 *   ② 有效菜名必须含至少一个韩文/英文字母，纯数字（17:00、12345）一律不算菜
 */
object OrderLineParser {

    private const val TAG = "LineParser"

    private val qtyPrefixX = Regex("^(\\d{1,3})\\s*[xX×*]\\s*(.+)$")
    private val qtySuffixX = Regex("^(.+?)\\s*[xX×*]\\s*(\\d{1,3})$")
    private val qtySuffixPadded = Regex("^(.+?)\\s{2,}(\\d{1,3})$")
    private val qtyPrefixSpace = Regex("^(\\d{1,3})\\s+(.+)$")
    private val qtySuffixSpace = Regex("^(.+?)\\s+(\\d{1,3})$")

    /**
     * 【V5.1 关键修复】行尾「追加」状态词。
     *
     * POS 加单实际打印："꿔바로우       2 추가" / "생양꼬치       4 추가"。
     * 旧流程让整行带着 "추가" 去匹配数量正则（\\d{1,3}$ 要求行尾是数字），必然匹配失败，
     * 随后 cleanDishName 里的旧正则又把数字 2 一起删掉 → 全部退化 qty = 1。
     * 现在解析前只摘掉 "추가" 这两个字，数量数字原样保留，数量就能正常解析。
     */
    private val appendWord = Regex("\\s*추\\s*가\\s*$")

    fun parse(raw: String): ParsedLine? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // 【V5.1】"꿔바로우   2 추가" → "꿔바로우   2"（数字保留），再走五级数量解析
        val line = appendWord.replace(trimmed, "").trim()
        if (line.isEmpty()) return null

        // ① 单据标题拦截（V4.3：주 문 서] / 영수증 这类整行丢弃）
        if (NoiseLineFilter.isTitleLine(line)) {
            RingLog.d(TAG, "title dropped: " + line)
            return null
        }
        // ② 表头拦截（V3：去空格判定）
        if (NoiseLineFilter.isHeaderLine(line)) {
            RingLog.d(TAG, "header dropped: " + line)
            return null
        }
        // ② 桌号行拦截（与 TableNoExtractor 共用同一套规则）
        if (isDeskLine(line)) return null

        // ③ 五级数量解析
        qtyPrefixX.find(line)?.let { m ->
            val name = clean(m.groupValues[2])
            if (isNameLike(name)) return ParsedLine(name, clampQty(m.groupValues[1]))
        }
        qtySuffixX.find(line)?.let { m ->
            val name = clean(m.groupValues[1])
            if (isNameLike(name)) return ParsedLine(name, clampQty(m.groupValues[2]))
        }
        qtySuffixPadded.find(line)?.let { m ->
            val name = clean(m.groupValues[1])
            if (isNameLike(name)) return ParsedLine(name, clampQty(m.groupValues[2]))
        }
        qtyPrefixSpace.find(line)?.let { m ->
            val name = clean(m.groupValues[2])
            if (isNameLike(name)) return ParsedLine(name, clampQty(m.groupValues[1]))
        }
        qtySuffixSpace.find(line)?.let { m ->
            val name = clean(m.groupValues[1])
            if (isNameLike(name)) return ParsedLine(name, clampQty(m.groupValues[2]))
        }

        // ④ 没数量仍是有效行
        val bare = clean(line)
        if (!isNameLike(bare)) {
            RingLog.w(TAG, "reject line: " + line)
            return null
        }
        return ParsedLine(bare, 1)
    }

    // ─────────────────────────────────────────────────────────────
    // V4.3 折行合并：POS 按固定字节宽排版，长菜名会被硬拆成两行
    //   SET A(특미+갈비꼬   1   +   치+꿔바로우)
    //   명품양갈비(250  -1취소  +   g)
    // 必须在"拆解菜品"之前把续行拼回菜名，否则会多出两张不存在的小菜。
    // ─────────────────────────────────────────────────────────────

    private val hangul = Regex("[가-힣ㄱ-ㅎ]")
    private val hangulOrLetter = Regex("[가-힣ㄱ-ㅎA-Za-z]")
    private val cancelMark = Regex("취\\s*소")
    private val anyDigit = Regex("\\d")

    /** g) / ml) / ) 这类 ASCII 残留片段（不含韩文，避免吞掉 "굴" 这种短菜名） */
    private val residualFragment = Regex("^[A-Za-z()\\[\\]{}<>+\\-.,:;/_'\"%& ]+$")

    /** 行尾的「数量 + 状态词（취소 / 추가）」，用于把菜名和尾部切开 */
    private val nameTail = Regex(
        "^(.*?)((?:\\s{2,}|\\s*[-–—]\\s*|\\s)\\d{1,3}(?:\\s*[xX×*])?\\s*(?:취\\s*소|추\\s*가)?|\\s*(?:취\\s*소|추\\s*가))\\s*$"
    )

    /** 上一行菜名以「( + 数字」结尾：规格被从中间截断，如 "명품양갈비(2" */
    private val truncatedNumericParen = Regex("\\(\\d{1,4}$")

    /** 规格残片字符集：50g) / 0g) / g) / ) */
    private val specFragment = Regex("^[0-9A-Za-z()\\-]{1,6}$")

    /**
     * 【V4.3】折行合并预处理：把被 POS 拆断的续行拼回上一行菜名。
     *
     * 判定为"续行"必须同时满足：
     *   ① 上一行是能解析的菜品行，且菜名里还有未闭合的左括号；
     *      或上一行菜名已闭合，但当前行只剩 1~4 个 ASCII 残片（g) / ml)）。
     *   ② 当前行的"菜名部分"不含数字、也不含 취소 / 추가
     *      （行尾单独挂着的  -1취소 / 数量 / 추가 不算独立标记）。
     *   ③ 特例（V5.1）：上一行菜名停在 "( + 数字"（如 명품양갈비(2），
     *      当前行是 50g) 这类规格残片时，允许拼接 —— 拼完数量仍从行尾 3 추가 里解析。
     *
     * 拼接位置：插在菜名末尾、数量和 -1취소 **之前**，两边的尾部都原样保留。
     */
    fun foldWrappedLines(lines: List<String>): List<String> {
        if (lines.size <= 1) return lines
        val out = ArrayList<String>(lines.size)
        for (raw in lines) {
            val current = raw.trim()
            if (current.isEmpty()) continue
            val prev = out.lastOrNull()
            if (prev != null && isContinuationOf(prev, current)) {
                val merged = mergeTail(prev, current)
                out[out.size - 1] = merged
                RingLog.d(TAG, "fold merged -> " + merged)
                continue
            }
            out.add(raw)
        }
        return out
    }

    private fun isContinuationOf(prevRaw: String, current: String): Boolean {
        // 只看"菜名部分"：行尾单独挂着的  -1취소 / 数量 / 추가 不算独立标记
        //（POS 会把取消标记或规格残片印在折行后的第二行： "인분)  -1취소"、"50g)"）
        val curName = splitTail(current).first.trim()
        if (curName.isEmpty()) return false
        if (cancelMark.containsMatchIn(curName) || appendWord.containsMatchIn(curName)) return false

        val prevName = splitTail(prevRaw).first.trim()
        if (prevName.isEmpty() || !hangulOrLetter.containsMatchIn(prevName)) return false
        if (parse(prevRaw) == null) return false

        val open = prevName.count { it == '(' }
        val close = prevName.count { it == ')' }

        if (open > close) {
            // 【V5.1】数字规格被拆断：
            //   上一行 "명품양갈비(2 3 추가"（菜名停在 "(2"，行尾是 " 3 추가"）
            //   下一行 "50g)"
            // → 拼成 "명품양갈비(250g) 3 추가"，随后数量仍能解析出 3。
            // 这里只放行"规格残片"，避免把 "부추 1" 这类真实菜品行误并进来。
            if (truncatedNumericParen.containsMatchIn(prevName)) {
                return looksLikeSpecFragment(curName)
            }
            // 普通折行：当前行自带数字就说明它是独立菜品行
            if (anyDigit.containsMatchIn(curName)) return false
            if (curName.contains(")") || hangulOrLetter.containsMatchIn(curName)) return true
            return looksLikeResidual(curName)
        }

        // 括号已闭合：只接受 1~4 个纯 ASCII 残片（g) / ml) / )）
        if (anyDigit.containsMatchIn(curName)) return false
        return looksLikeResidual(curName)
    }

    /** 规格残片：不含韩文、极短、含数字或以 ) 收尾（50g) / 0g) / g) / )） */
    private fun looksLikeSpecFragment(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 6) return false
        if (hangul.containsMatchIn(t)) return false
        if (!specFragment.matches(t)) return false
        return anyDigit.containsMatchIn(t) || t.contains(")")
    }

    private fun looksLikeResidual(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 4) return false
        if (hangul.containsMatchIn(t)) return false
        return residualFragment.matches(t)
    }

    /**
     * 把当前行的菜名部分插到上一行菜名末尾。
     * 两边的行尾（数量 / -1취소）都原样保留，所以拼完仍是 POS 的合法排版：
     *   명품양갈비(250  -1취소  +  g)   →  명품양갈비(250g)  -1취소
     */
    private fun mergeTail(prevRaw: String, current: String): String {
        val (name, tail) = splitTail(prevRaw)
        val (curName, curTail) = splitTail(current)
        val addName = if (curName.isBlank()) current.trim() else curName.trim()
        val addTail = if (curName.isBlank()) "" else curTail
        return name.trimEnd() + addName + addTail + tail
    }

    /** 拆成 (菜名, 尾部)；尾部 = 空格 + 数量 + 可选的 취소 */
    private fun splitTail(raw: String): Pair<String, String> {
        val m = nameTail.find(raw) ?: return raw to ""
        val name = m.groupValues[1]
        if (name.isBlank()) return raw to ""
        return name to m.groupValues[2]
    }

    private fun isDeskLine(line: String): Boolean {
        if (TableNoExtractor.looksLikeTableLine(line)) return true
        val compact = line.replace(Regex("\\s+"), "").lowercase()
        if (compact.isEmpty()) return true
        val deskWords = listOf("테이블", "table", "홀", "포장", "배달")
        for (kw in deskWords) {
            if (compact.contains(kw) && compact.length <= 14) return true
        }
        return false
    }

    private fun clean(s: String): String {
        var t = s.trim()
        t = t.replace(Regex("^[\\[\\(\\{]\\s*\\d{1,3}\\s*[\\]\\)\\.]\\s*"), "")
        t = t.replace(Regex("^[-*·•]\\s*"), "")
        t = t.replace(Regex("\\s{2,}"), " ")
        return t.trim()
    }

    private fun isNameLike(s: String): Boolean {
        if (s.isEmpty() || s.length > 60) return false
        for (ch in s) {
            if (ch in '가'..'힣') return true
            if (ch in 'ㄱ'..'ㅎ') return true
            if (ch in 'a'..'z' || ch in 'A'..'Z') return true
        }
        return false
    }

    private fun clampQty(s: String): Int {
        val v = s.toIntOrNull() ?: return 1
        return if (v in 1..99) v else 1
    }
}

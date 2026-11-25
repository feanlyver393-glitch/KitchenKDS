package com.kitchenkds.domain.dict

import com.kitchenkds.core.log.RingLog
import java.util.LinkedHashSet

/**
 * 三级索引 + 5 级匹配。
 *
 * 之所以需要 5 级，是因为热敏打印机经常只印主名不印尺寸括号，
 * 或因为对齐空格把菜名切断，纯 HashMap 精确查必然大面积漏翻。
 */
object MenuDictionary {

    private const val TAG = "MenuDict"

    private val exactIndex = HashMap<String, MenuEntry>()
    private val variantIndex = HashMap<String, MenuEntry>()
    private val tokenKeys = ArrayList<String>()   // 按长度降序，供最长包含匹配
    private var built = false

    private val parenRegex = Regex("\\([^)]*\\)")
    private val sizeRegex = Regex("\\(([대중소/]+)\\)")
    private val tailDigitRegex = Regex("[0-9]+$")
    private val headDigitRegex = Regex("^[0-9]+")

    @Synchronized
    fun build() {
        if (built) return
        exactIndex.clear()
        variantIndex.clear()
        tokenKeys.clear()

        for (entry in MenuTable.ALL) {
            val key = norm(entry.korean)
            if (key.isNotEmpty()) exactIndex[key] = entry
            for (variant in expandVariants(entry)) {
                if (variant.isNotEmpty()) variantIndex[variant] = entry
            }
        }
        for (token in variantIndex.keys) {
            if (token.length >= 2) tokenKeys.add(token)
        }
        tokenKeys.sortByDescending { it.length }
        built = true
        RingLog.d(TAG, "built: exact=" + exactIndex.size + " variant=" + variantIndex.size)
    }

    /** 归一化：去零宽、去装饰符、NFKC(全角→半角)、去空白、转大写 */
    fun norm(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch.code < 0x20 -> Unit
                ch.code in 0x200B..0x200F -> Unit
                ch.code == 0xFEFF -> Unit
                ch == '·' || ch == '•' || ch == '-' || ch == '_' || ch == '*' || ch == '.' -> Unit
                ch.isWhitespace() -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString().uppercase()
    }

    /**
     * 变体展开：
     * "공부가주(대/중/소)" → 공부가주 / 공부가주대 / 공부가주중 / 공부가주소
     * "탕수육(중)"        → 탕수육 / 탕수육중
     * "SET A(꼬치+...) "  → SETA
     */
    private fun expandVariants(entry: MenuEntry): Set<String> {
        val out = LinkedHashSet<String>()
        out.add(norm(entry.korean))

        val noParen = entry.korean.replace(parenRegex, "")
        if (noParen != entry.korean) out.add(norm(noParen))

        val sizeHit = sizeRegex.find(entry.korean)
        if (sizeHit != null) {
            for (size in sizeHit.groupValues[1].split("/")) {
                if (size.isNotEmpty()) out.add(norm(noParen + size))
            }
        }
        return out
    }

    /** 主入口：输入一行（或已剥离数量的纯菜名），返回命中的词条 */
    fun lookup(raw: String): MenuEntry? {
        build()
        val s = norm(raw)
        if (s.isEmpty()) return null

        direct(s)?.let { return it }

        // 二次机会：剥掉残留的行首/行尾数字再试一次（防解析层漏切数量）
        var stripped = s.replace(tailDigitRegex, "").replace(headDigitRegex, "")
        stripped = stripped.replace(Regex("X$"), "")
        if (stripped.isNotEmpty() && stripped != s) {
            direct(stripped)?.let { return it }
        }

        // L4 模糊：编辑距离 ≤ 2，长度 ≥ 4，且首音节一致，防误判
        fuzzy(stripped.ifEmpty { s })?.let { return it }

        RingLog.w(TAG, "MISS: " + raw)
        return null
    }

    private fun direct(s: String): MenuEntry? {
        exactIndex[s]?.let { return it }
        variantIndex[s]?.let { return it }
        // L3 最长包含匹配：长键优先，防止 "양갈비" 抢走 "양갈비꼬치"
        for (key in tokenKeys) {
            if (s.length > key.length && s.contains(key)) return variantIndex[key]
        }
        return null
    }

    private fun fuzzy(s: String): MenuEntry? {
        if (s.length < 4) return null
        var best: MenuEntry? = null
        var bestDist = Int.MAX_VALUE
        val limit = if (s.length <= 6) 1 else 2
        for ((key, entry) in variantIndex) {
            if (key.isEmpty() || key[0] != s[0]) continue
            if (Math.abs(key.length - s.length) > limit) continue
            val d = levenshtein(s, key)
            if (d < bestDist) {
                bestDist = d
                best = entry
            }
        }
        return if (best != null && bestDist <= limit) best else null
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }

    /** 从原始行里回填尺寸/量词提示 */
    private val unitRegex = Regex("\\((대|중|소|1인분)\\)|(?:^|[^가-힣])(대|중|소)(?:[^가-힣]|$)")
    private val unitLoose = Regex("(대|중|소|1인분)")

    fun extractUnitHint(raw: String): String? {
        unitRegex.find(raw)?.let { m ->
            for (i in 1..m.groupValues.size - 1) {
                if (m.groupValues[i].isNotEmpty()) return m.groupValues[i]
            }
        }
        unitLoose.find(raw)?.let { return it.groupValues[1] }
        return null
    }
}

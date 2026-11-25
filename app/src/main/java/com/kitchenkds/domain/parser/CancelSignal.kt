package com.kitchenkds.domain.parser

/**
 * POS 取消单识别与菜名还原（V4）。
 *
 * 现场取消单的打印形态五花八门：
 *   마라쇼롱샤 -1취소
 *   마라쇼롱샤취소
 *   마라쇼롱샤 - 취소
 *   마라쇼롱샤 1취소
 *   玛 라 쇼 롱 샤 -1 취 소   （带对齐空格）
 *
 * 统一策略：先删掉所有「취소」（容忍字间空格），再剥掉尾部残留的数量与连字符。
 */
object CancelSignal {

    /** 任意位置的 취소（含字间空格） */
    private val cancelToken = Regex("취\\s*소")

    /** 尾部残留： "-1" / " - 1" / "1" */
    private val trailingQuantity = Regex("[-–—\\s]*\\d+\\s*$")

    /** 尾部残留的连字符 / 空格 */
    private val trailingPunct = Regex("[-–—\\s_*·]+$")

    private val multiSpace = Regex("\\s{2,}")

    /** 这一行是不是取消行 */
    fun isCancelLine(name: String): Boolean = cancelToken.containsMatchIn(name)

    /**
     * 还原真实菜名："마라쇼롱샤 -1취소" → "마라쇼롱샤"
     * 还原失败（返回空）时调用方应放弃合并，避免误删原单。
     */
    fun stripCancel(raw: String): String {
        var s = raw.replace(cancelToken, "")
        s = multiSpace.replace(s, " ")
        s = trailingQuantity.replace(s, "")
        s = trailingPunct.replace(s, "")
        return s.trim()
    }

    /** 菜名比较：忽略空格与大小写（打印机常插对齐空格） */
    fun sameDish(a: String, b: String): Boolean {
        val na = a.replace(Regex("\\s+"), "").uppercase()
        val nb = b.replace(Regex("\\s+"), "").uppercase()
        return na.isNotEmpty() && na == nb
    }
}

package com.kitchenkds.domain.parser

import com.kitchenkds.core.log.RingLog
import com.kitchenkds.domain.dict.MenuDictionary
import com.kitchenkds.domain.model.OrderItem

/**
 * V4.3 取消单模糊兜底匹配。
 *
 * 现场问题：POS 按固定字节宽把长菜名硬拆成两行，即使折行合并生效，
 * 仍可能出现「取消单里的名字只是原单名字的前半截」的情况：
 *   原单    명품양갈비(250g)
 *   取消单  명품양갈비(250  （在上一行）+ g)（被折到下一行）
 * 只做 koreanName 精确相等就会匹配失败，取消单被当成新菜上屏 → 多出一张分裂卡。
 *
 * 因此按优先级三级匹配（只在该桌最新未完成单内进行，默认跳过已取消项）：
 *   ① 清洗后精确相等（忽略空格 / 大小写）
 *   ② 去括号基础名相等，或前缀互相包含（取消菜名长度 >= 4）
 *   ③ 双方词典翻译后的中文名相同（非空且不等于原韩文）
 */
object CancelMatcher {

    private const val TAG = "CancelMatch"

    /** 括号规格：(250g) / (대) / (1인분) */
    private val parenBlock = Regex("\\([^)]*\\)")
    private val spaces = Regex("\\s+")

    /** 在该桌最新未完成单的菜品列表里找取消目标 */
    fun findTarget(
        candidates: List<OrderItem>,
        cancelName: String,
        includeCancelled: Boolean = false
    ): OrderItem? {
        val want = cancelName.trim()
        if (want.isEmpty()) return null

        val pool = if (includeCancelled) candidates else candidates.filterNot { it.isCancelled }
        if (pool.isEmpty()) return null

        // ① 清洗后精确相等
        pool.firstOrNull { CancelSignal.sameDish(it.koreanName, want) }?.let {
            RingLog.d(TAG, "exact hit: " + it.koreanName)
            return it
        }

        // ② 去括号基础名 / 前缀兜底
        val wantCompact = compact(want)
        val wantBase = compact(parenBlock.replace(want, ""))
        if (wantCompact.isNotEmpty() && wantBase.isNotEmpty()) {
            val baseHit = pool.firstOrNull { item ->
                val base = compact(parenBlock.replace(item.koreanName, ""))
                base.isNotEmpty() && base == wantBase
            }
            if (baseHit != null) {
                RingLog.d(TAG, "base hit: " + baseHit.koreanName + " <- " + want)
                return baseHit
            }
        }
        if (wantCompact.length >= 4) {
            val prefixHit = pool.firstOrNull { item ->
                val have = compact(item.koreanName)
                have.length >= 2 &&
                    (have.startsWith(wantCompact) || wantCompact.startsWith(have))
            }
            if (prefixHit != null) {
                RingLog.d(TAG, "prefix hit: " + prefixHit.koreanName + " <- " + want)
                return prefixHit
            }
        }

        // ③ 词典中文名相同
        val wantChinese = translate(want)
        if (wantChinese.isNotEmpty()) {
            val dictHit = pool.firstOrNull { translate(it.koreanName) == wantChinese }
            if (dictHit != null) {
                RingLog.d(TAG, "dict hit: " + dictHit.koreanName + " <- " + wantChinese)
                return dictHit
            }
        }

        return null
    }

    /** 词典译文；未命中或译文等于原名时返回空串，避免"没翻出来的韩文"互相匹配 */
    private fun translate(name: String): String {
        val cn = MenuDictionary.lookup(name)?.chinese.orEmpty().trim()
        if (cn.isEmpty()) return ""
        if (cn == name.trim()) return ""
        return cn
    }

    private fun compact(raw: String): String = spaces.replace(raw, "").uppercase()
}

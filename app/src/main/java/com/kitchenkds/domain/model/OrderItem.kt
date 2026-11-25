package com.kitchenkds.domain.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class OrderItem(
    /** 稳定 ID：Compose 的 key，也是"单品划线"状态的唯一定位符 */
    val id: String = UUID.randomUUID().toString(),
    /** 归一化前的韩文原名（打印原文，用于补词/排障） */
    val koreanName: String,
    /** 词典译文；未命中时等于 koreanName，UI 无需判空 */
    val chineseName: String,
    /** 只会是 FOOD 或 BEVERAGE */
    val category: ItemCategory,
    val quantity: Int = 1,
    /** 대 / 중 / 소 / 1인분，可空 */
    val unitHint: String? = null,
    val isDone: Boolean = false,
    /** V4：被 POS 取消单取消 → UI 画红色双横线，不需要制作 */
    val isCancelled: Boolean = false,
    /** 打印顺序，保证卡片内排列与原单一致 */
    val sortIndex: Int = 0,
    val rawLine: String = ""
) {
    /** 词典是否命中（黄点提示的依据） */
    val isMatched: Boolean
        get() = chineseName.isNotBlank() && chineseName != koreanName

    val isBeverage: Boolean get() = category == ItemCategory.BEVERAGE

    /** V4：单行双语展示「韩文 - 中文」 */
    val displayLine: String
        get() = if (isMatched) koreanName + " - " + chineseName else koreanName
}

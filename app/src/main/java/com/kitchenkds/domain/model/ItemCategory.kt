package com.kitchenkds.domain.model

/**
 * V2 严格两级分类法：只有两个合法值。
 *
 * 判定边界（写在 AssembleOrderUseCase，DB 层不存第三值）：
 *   BEVERAGE = 明确的酒、烧酒、啤酒、白酒、可乐/雪碧/芬达等碳酸饮料、茶饮
 *   FOOD     = 其余一切：菜品、米饭、面条、配菜、蘸料、套餐、小吃
 *
 * V1 的 UNKNOWN 已废除 —— 未命中词典的词一律强制归 FOOD，
 * 由 UI 上的黄点提示补词，而不是让它落到"第三类"里。
 */
enum class ItemCategory {
    FOOD,
    BEVERAGE;

    companion object {
        /** DB 字符串 → 枚举，非法值安全回落 FOOD（不会被历史脏数据炸掉） */
        fun fromDb(value: String?): ItemCategory =
            if (value == BEVERAGE.name) BEVERAGE else FOOD
    }
}

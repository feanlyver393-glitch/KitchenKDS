package com.kitchenkds.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单主表：一行 = 一张单。
 *
 * 拆表理由：后厨 KDS 的历史页/导出/统计只关心"单"这一层，
 * 把菜品明细放子表可以让历史列表查询不必把所有明细都读进内存。
 */
@Entity(
    tableName = "orders",
    indices = [
        Index("isCompleted"),
        Index("receivedAtMillis"),
        Index("completedAtMillis")
    ]
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val tableNo: String,
    /** NEW / REPRINT / UNKNOWN，存字符串不用 ordinal，避免枚举顺序变化污染历史数据 */
    val kind: String,
    val receivedAtMillis: Long,
    val completedAtMillis: Long?,
    val isCompleted: Boolean,
    /** 本地自增序号，同一毫秒多单的稳定排序兜底 */
    val seq: Int,

    // ── 冗余统计列：历史页与导出的头部直接可用，不必子查询 ──
    val foodItemCount: Int,
    val beverageItemCount: Int,
    val totalQuantity: Int,

    /** 打印机原始文本，事后补词 / 追溯的唯一凭证 */
    val rawText: String
)

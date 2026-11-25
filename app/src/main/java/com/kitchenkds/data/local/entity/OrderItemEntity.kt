package com.kitchenkds.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订单明细表。
 *
 * V4 新增 isCancelled：POS 发来的取消单不再单独成卡，
 * 而是把原单里对应菜品打上"已取消"标记（UI 画红色双横线）。
 */
@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("orderId"),
        Index("category"),
        Index("isDone"),
        Index("isCancelled")
    ]
)
data class OrderItemEntity(
    @PrimaryKey val id: String,
    val orderId: String,

    /** 韩文原名（打印原文，保留给现场补词） */
    val koreanName: String,
    /** 中文译文；未命中词典时等于 koreanName */
    val chineseName: String,

    /** 严格两级：FOOD / BEVERAGE */
    val category: String,

    val quantity: Int,
    /** 대 / 중 / 소 / 1인분，可空 */
    val unitHint: String?,
    val isDone: Boolean,

    /** V4：是否已被 POS 取消单取消（红色双横线） */
    val isCancelled: Boolean = false,

    /** 打印顺序，保证卡片内排列与原单一致 */
    val sortIndex: Int,
    val rawLine: String
)

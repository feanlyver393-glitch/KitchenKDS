package com.kitchenkds.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Room 关系查询结果：一张单 + 它的全部明细 */
data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val items: List<OrderItemEntity>
) {
    val foodItems: List<OrderItemEntity>
        get() = items.filter { it.category == "FOOD" }.sortedBy { it.sortIndex }

    val beverageItems: List<OrderItemEntity>
        get() = items.filter { it.category == "BEVERAGE" }.sortedBy { it.sortIndex }
}

package com.kitchenkds.data.local

import com.kitchenkds.data.local.entity.OrderEntity
import com.kitchenkds.data.local.entity.OrderItemEntity
import com.kitchenkds.data.local.entity.OrderWithItems
import com.kitchenkds.domain.model.ItemCategory
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.domain.model.OrderKind

/** Entity ↔ Domain 双向映射。只在这一层做类型转换。 */
object OrderMapper {

    fun OrderWithItems.toDomain(): Order {
        val food = ArrayList<OrderItem>()
        val bev = ArrayList<OrderItem>()
        for (item in items.sortedBy { it.sortIndex }) {
            val domain = item.toDomain()
            if (domain.category == ItemCategory.BEVERAGE) bev.add(domain) else food.add(domain)
        }
        return Order(
            id = order.id,
            tableNo = order.tableNo,
            kind = parseKind(order.kind),
            receivedAtMillis = order.receivedAtMillis,
            seq = order.seq,
            foodItems = food,
            beverageItems = bev,
            isCompleted = order.isCompleted,
            completedAtMillis = order.completedAtMillis
        )
    }

    fun OrderItemEntity.toDomain(): OrderItem = OrderItem(
        id = id,
        koreanName = koreanName,
        chineseName = chineseName,
        category = ItemCategory.fromDb(category),
        quantity = quantity,
        unitHint = unitHint,
        isDone = isDone,
        isCancelled = isCancelled,
        sortIndex = sortIndex,
        rawLine = rawLine
    )

    fun Order.toEntity(rawText: String = ""): OrderEntity = OrderEntity(
        id = id,
        tableNo = tableNo,
        kind = kind.name,
        receivedAtMillis = receivedAtMillis,
        completedAtMillis = completedAtMillis,
        isCompleted = isCompleted,
        seq = seq,
        foodItemCount = foodItems.size,
        beverageItemCount = beverageItems.size,
        totalQuantity = allItems.sumOf { it.quantity },
        rawText = rawText
    )

    fun Order.toItemEntities(): List<OrderItemEntity> =
        allItems.sortedBy { it.sortIndex }.map { item ->
            OrderItemEntity(
                id = item.id,
                orderId = id,
                koreanName = item.koreanName,
                chineseName = item.chineseName,
                category = if (item.category == ItemCategory.BEVERAGE) {
                    ItemCategory.BEVERAGE.name
                } else {
                    ItemCategory.FOOD.name
                },
                quantity = item.quantity,
                unitHint = item.unitHint,
                isDone = item.isDone,
                isCancelled = item.isCancelled,
                sortIndex = item.sortIndex,
                rawLine = item.rawLine
            )
        }

    private fun parseKind(raw: String): OrderKind = when (raw) {
        OrderKind.REPRINT.name -> OrderKind.REPRINT
        OrderKind.NEW.name -> OrderKind.NEW
        else -> OrderKind.UNKNOWN
    }
}

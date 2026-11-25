package com.kitchenkds.domain.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Order(
    val id: String = UUID.randomUUID().toString(),
    /** 桌号（已剥离 "테이블/T/NO." 前缀） */
    val tableNo: String,
    val kind: OrderKind,
    /** 接单时刻，计时器基准 */
    val receivedAtMillis: Long,
    /** 本地自增序号：同一毫秒内多单的稳定排序依据，防止 LazyGrid 抖动 */
    val seq: Int,

    // ★ 核心要求：构造时即物理分离，UI 不再做 filter
    val foodItems: List<OrderItem> = emptyList(),
    val beverageItems: List<OrderItem> = emptyList(),

    /** 整单斩击完成态 */
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null
) {
    /** 只读合并视图，供统计使用 */
    val allItems: List<OrderItem> get() = foodItems + beverageItems

    val hasBeverage: Boolean get() = beverageItems.isNotEmpty()
    val totalQuantity: Int get() = allItems.sumOf { it.quantity }
    val doneQuantity: Int get() = allItems.filter { it.isDone }.sumOf { it.quantity }
    val itemCount: Int get() = allItems.size
    val doneItemCount: Int get() = allItems.count { it.isDone }
    val allItemsDone: Boolean get() = itemCount > 0 && doneItemCount == itemCount

    /** 切换单个品项的完成态；两列表同时 map，杜绝状态漂移 */
    fun toggleItem(itemId: String): Order = mapItems { item ->
        if (item.id == itemId) item.copy(isDone = !item.isDone) else item
    }

    fun markAllItemsDone(): Order = mapItems { it.copy(isDone = true) }

    /** 整单斩击：单子不消失，留作历史参考 */
    fun strikeThrough(nowMillis: Long): Order = copy(
        isCompleted = true,
        completedAtMillis = nowMillis,
        foodItems = foodItems.map { it.copy(isDone = true) },
        beverageItems = beverageItems.map { it.copy(isDone = true) }
    )

    /** 撤销斩击（误触恢复） */
    fun undoStrikeThrough(): Order = copy(
        isCompleted = false,
        completedAtMillis = null
    )

    private fun mapItems(transform: (OrderItem) -> OrderItem): Order {
        val newFood = foodItems.map(transform)
        val newBev = beverageItems.map(transform)
        val allDone = (newFood.isNotEmpty() || newBev.isNotEmpty()) &&
            newFood.all { it.isDone } && newBev.all { it.isDone }
        return copy(
            foodItems = newFood,
            beverageItems = newBev,
            // 单品全部勾完 → 自动整单完成；有单品取消勾选 → 自动撤销整单完成
            isCompleted = allDone,
            completedAtMillis = if (allDone) (completedAtMillis ?: System.currentTimeMillis()) else null
        )
    }

    companion object {
        /** 唯一构造入口：在这里完成分类拆分，杜绝散落各处的 filter */
        fun assemble(
            tableNo: String,
            kind: OrderKind,
            receivedAtMillis: Long,
            seq: Int,
            items: List<OrderItem>
        ): Order {
            val food = ArrayList<OrderItem>(items.size)
            val bev = ArrayList<OrderItem>()
            for (item in items) {
                // V2 严格两级：只有 BEVERAGE 进饮品区，其余一切（含米饭面条配菜）进菜品区
                if (item.category == ItemCategory.BEVERAGE) bev.add(item) else food.add(item)
            }
            return Order(
                tableNo = tableNo,
                kind = kind,
                receivedAtMillis = receivedAtMillis,
                seq = seq,
                foodItems = food,
                beverageItems = bev
            )
        }
    }
}

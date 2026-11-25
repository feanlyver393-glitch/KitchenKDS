package com.kitchenkds.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kitchenkds.data.local.entity.OrderEntity
import com.kitchenkds.data.local.entity.OrderItemEntity
import com.kitchenkds.data.local.entity.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OrderDao {

    // ─────────────── 查询 ───────────────

    @Transaction
    @Query("SELECT * FROM orders WHERE isCompleted = 0 ORDER BY receivedAtMillis ASC, seq ASC")
    abstract fun observeActiveOrders(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE isCompleted = 1 ORDER BY completedAtMillis DESC")
    abstract fun observeHistoryOrders(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    abstract suspend fun loadOrder(orderId: String): OrderWithItems?

    /** V4：取消/重单合并的目标 —— 该桌号下最新的一笔未完成订单 */
    @Transaction
    @Query(
        "SELECT * FROM orders WHERE tableNo = :tableNo AND isCompleted = 0 " +
            "ORDER BY receivedAtMillis DESC, seq DESC LIMIT 1"
    )
    abstract suspend fun loadLatestActiveByTable(tableNo: String): OrderWithItems?

    @Query("SELECT * FROM orders ORDER BY receivedAtMillis ASC")
    abstract suspend fun loadAllOrders(): List<OrderEntity>

    @Query("SELECT * FROM order_items ORDER BY orderId, sortIndex")
    abstract suspend fun loadAllItems(): List<OrderItemEntity>

    @Query("SELECT COUNT(*) FROM orders WHERE isCompleted = 0")
    abstract suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM orders WHERE isCompleted = 1")
    abstract suspend fun countHistory(): Int

    // ─────────────── 写入 ───────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertItems(items: List<OrderItemEntity>)

    @Transaction
    open suspend fun insertFull(order: OrderEntity, items: List<OrderItemEntity>) {
        upsertOrder(order)
        if (items.isNotEmpty()) upsertItems(items)
    }

    @Query("UPDATE order_items SET isDone = :done WHERE id = :itemId")
    abstract suspend fun setItemDone(itemId: String, done: Boolean)

    @Query("UPDATE order_items SET isCancelled = :cancelled WHERE id = :itemId")
    abstract suspend fun setItemCancelled(itemId: String, cancelled: Boolean)

    @Query("UPDATE order_items SET quantity = :quantity WHERE id = :itemId")
    abstract suspend fun setItemQuantity(itemId: String, quantity: Int)

    /** V4：取消单合并 —— 标记取消 + 数量归位，一条事务 */
    @Transaction
    open suspend fun applyCancel(itemId: String) {
        setItemCancelled(itemId, true)
        setItemDone(itemId, false)
    }

    /** V4：取消后重新下单 —— 红线消失，数量更新 */
    @Transaction
    open suspend fun applyRevive(itemId: String, quantity: Int) {
        setItemCancelled(itemId, false)
        setItemQuantity(itemId, quantity)
    }

    @Query("UPDATE orders SET isCompleted = 1, completedAtMillis = :timestamp WHERE id = :orderId")
    abstract suspend fun markCompleted(orderId: String, timestamp: Long)

    @Query("UPDATE orders SET isCompleted = 0, completedAtMillis = NULL WHERE id = :orderId")
    abstract suspend fun markActive(orderId: String)

    @Transaction
    open suspend fun strikeThrough(orderId: String, timestamp: Long) {
        markCompleted(orderId, timestamp)
        val itemIds = loadOrder(orderId)?.items?.map { it.id } ?: emptyList()
        for (id in itemIds) setItemDone(id, true)
    }

    @Transaction
    open suspend fun undoStrikeThrough(orderId: String) {
        markActive(orderId)
        val itemIds = loadOrder(orderId)?.items?.map { it.id } ?: emptyList()
        for (id in itemIds) setItemDone(id, false)
    }

    // ─────────────── 删除 ───────────────

    @Query("DELETE FROM orders WHERE id IN (:ids)")
    abstract suspend fun deleteOrders(ids: List<String>)

    @Query("DELETE FROM orders WHERE isCompleted = 1")
    abstract suspend fun deleteAllCompleted()

    @Transaction
    open suspend fun deleteHistory(ids: List<String>) {
        if (ids.isEmpty()) deleteAllCompleted() else deleteOrders(ids)
    }

    /** V4：数据重置 / 定时清理 —— 清空全部订单与明细 */
    @Query("DELETE FROM orders")
    abstract suspend fun deleteEverything()
}

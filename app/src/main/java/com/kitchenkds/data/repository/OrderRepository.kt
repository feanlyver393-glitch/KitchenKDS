package com.kitchenkds.data.repository

import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.local.OrderDao
import com.kitchenkds.data.local.OrderMapper.toDomain
import com.kitchenkds.data.local.OrderMapper.toEntity
import com.kitchenkds.data.local.OrderMapper.toItemEntities
import com.kitchenkds.data.local.entity.OrderWithItems
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.domain.parser.CancelMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * V4 仓储。新增取消/重单合并所需的三个原子操作：
 *   - latestActiveByTable：按桌号取最新未完成单
 *   - markItemCancelled   ：打红标
 *   - reviveItem          ：去红标并更新数量
 */
class OrderRepository(private val dao: OrderDao) {

    private companion object {
        const val TAG = "OrderRepo"
    }

    val activeOrders: Flow<List<Order>> =
        dao.observeActiveOrders().map { rows -> rows.map { it.toDomain() } }

    val historyOrders: Flow<List<Order>> =
        dao.observeHistoryOrders().map { rows -> rows.map { it.toDomain() } }

    suspend fun save(order: Order, rawText: String = "") {
        try {
            dao.insertFull(order.toEntity(rawText), order.toItemEntities())
            RingLog.d(TAG, "saved table=" + order.tableNo + " items=" + order.allItems.size)
        } catch (t: Throwable) {
            RingLog.e(TAG, "save fail: " + t.message)
        }
    }

    suspend fun setItemDone(itemId: String, done: Boolean) = dao.setItemDone(itemId, done)

    suspend fun latestActiveByTable(tableNo: String): Order? =
        dao.loadLatestActiveByTable(tableNo)?.toDomain()

    /**
     * 【V4.3】取消目标解析：在该桌最新未完成单里按
     *   ① 精确名 → ② 去括号基础名/前缀 → ③ 词典中文名
     * 的三级模糊规则找菜品。POS 折行导致的 "명품양갈비(250" 也能命中 "명품양갈비(250g)"。
     */
    suspend fun resolveCancelTarget(tableNo: String, cancelName: String): OrderItem? {
        val target = dao.loadLatestActiveByTable(tableNo)?.toDomain() ?: return null
        val hit = CancelMatcher.findTarget(target.allItems, cancelName)
        if (hit == null) {
            RingLog.w(TAG, "cancel target not found: " + cancelName + " table=" + tableNo)
        }
        return hit
    }

    suspend fun markItemCancelled(itemId: String) {
        dao.applyCancel(itemId)
        RingLog.d(TAG, "cancel item=" + itemId)
    }

    suspend fun reviveItem(itemId: String, quantity: Int) {
        dao.applyRevive(itemId, quantity)
        RingLog.d(TAG, "revive item=" + itemId + " qty=" + quantity)
    }

    suspend fun strikeThrough(orderId: String) {
        dao.strikeThrough(orderId, System.currentTimeMillis())
    }

    suspend fun undoStrikeThrough(orderId: String) = dao.undoStrikeThrough(orderId)

    suspend fun deleteHistory(ids: List<String>) = dao.deleteHistory(ids)

    /** V4：数据重置 / 定时清理 */
    suspend fun deleteEverything() {
        dao.deleteEverything()
        RingLog.w(TAG, "ALL ORDERS DELETED")
    }

    suspend fun loadOrder(orderId: String): Order? = dao.loadOrder(orderId)?.toDomain()

    suspend fun snapshotAll(): List<Order> {
        val orders = dao.loadAllOrders()
        val itemsByOrder = dao.loadAllItems().groupBy { it.orderId }
        return orders
            .sortedBy { it.receivedAtMillis }
            .map { entity ->
                OrderWithItems(entity, itemsByOrder[entity.id] ?: emptyList()).toDomain()
            }
    }

    suspend fun countActive(): Int = dao.countActive()

    suspend fun countHistory(): Int = dao.countHistory()
}

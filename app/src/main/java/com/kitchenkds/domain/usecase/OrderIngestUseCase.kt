package com.kitchenkds.domain.usecase

import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.parser.RawFrame
import com.kitchenkds.data.repository.OrderRepository
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.domain.parser.CancelMatcher
import com.kitchenkds.domain.parser.CancelSignal

/**
 * 入库前的"取消 / 重单"合并闸门（V4 核心业务逻辑）。
 *
 * 三种情形：
 *
 *  ① 取消单（含 취소）
 *     → 找该桌最新未完成单，把对应菜品 isCancelled = true
 *     → 整单都是取消项时 **不插入新卡**，直接 return
 *
 *  ② 取消后重新下单（正常单，且原单里同名菜是 isCancelled = true）
 *     → 还原 isCancelled = false、数量更新为新单数量
 *     → 全部菜都被还原时 **不插入新卡**
 *
 *  ③ 纯新单 / 混合单
 *     → 剩下的菜组装成新卡插入
 */
class OrderIngestUseCase(
    private val repository: OrderRepository,
    private val assembleOrderUseCase: AssembleOrderUseCase
) {

    private companion object {
        const val TAG = "Ingest"
    }

    data class Outcome(
        val inserted: Boolean,
        val cancelledCount: Int = 0,
        val revivedCount: Int = 0,
        val tableNo: String = ""
    )

    suspend fun ingest(frame: RawFrame, seq: Int): Outcome {
        val order = assembleOrderUseCase.invoke(frame, seq) ?: return Outcome(false)
        if (order.allItems.isEmpty()) return Outcome(false)

        val cancelItems = order.allItems.filter { CancelSignal.isCancelLine(it.koreanName) }
        val normalItems = order.allItems.filterNot { CancelSignal.isCancelLine(it.koreanName) }

        val target = repository.latestActiveByTable(order.tableNo)
        var cancelled = 0
        var revived = 0

        // ── ① 处理取消项 ──
        if (cancelItems.isNotEmpty()) {
            if (target == null) {
                // 找不到原单：宁可丢弃，也不能让"取消"变成一张真订单
                RingLog.w(TAG, "cancel without active order, dropped. table=" + order.tableNo)
                return Outcome(false, cancelItems.size, 0, order.tableNo)
            }
            for (cancel in cancelItems) {
                val realName = CancelSignal.stripCancel(cancel.koreanName)
                if (realName.isEmpty()) continue
                // 【V4.3】三级模糊兜底：精确名 → 基础名/前缀 → 词典中文名
                val hit = repository.resolveCancelTarget(order.tableNo, realName)
                if (hit != null) {
                    repository.markItemCancelled(hit.id)
                    cancelled++
                } else {
                    RingLog.w(TAG, "cancel target not found: " + realName)
                }
            }
            // 纯取消单：到此为止，绝不插入新卡
            if (normalItems.isEmpty()) {
                RingLog.d(TAG, "pure cancel order, merged " + cancelled + " item(s)")
                return Outcome(false, cancelled, 0, order.tableNo)
            }
        }

        // ── ② 正常项：先尝试"复活"原单里被取消的同名菜 ──
        val remaining = ArrayList<OrderItem>(normalItems.size)
        for (item in normalItems) {
            val hit = CancelMatcher.findTarget(
                candidates = target?.allItems.orEmpty(),
                cancelName = item.koreanName,
                includeCancelled = true
            )
            if (hit != null && hit.isCancelled) {
                repository.reviveItem(hit.id, item.quantity)
                revived++
                RingLog.d(TAG, "revived: " + item.koreanName + " qty=" + item.quantity)
            } else {
                remaining.add(item)
            }
        }

        if (remaining.isEmpty()) {
            // 全部都被复活，没有新菜
            return Outcome(false, cancelled, revived, order.tableNo)
        }

        // ── ③ 剩下的作为新卡插入 ──
        val toInsert = Order.assemble(
            tableNo = order.tableNo,
            kind = order.kind,
            receivedAtMillis = order.receivedAtMillis,
            seq = order.seq,
            items = remaining
        )
        repository.save(toInsert, frame.text())
        RingLog.d(
            TAG,
            "inserted table=" + toInsert.tableNo +
                " items=" + remaining.size + " cancelled=" + cancelled + " revived=" + revived
        )
        return Outcome(true, cancelled, revived, order.tableNo)
    }
}

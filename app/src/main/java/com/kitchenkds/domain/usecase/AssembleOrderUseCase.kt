package com.kitchenkds.domain.usecase

import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.decode.TextNormalizer
import com.kitchenkds.data.parser.OrderLineParser
import com.kitchenkds.data.parser.RawFrame
import com.kitchenkds.data.parser.TableNoExtractor
import com.kitchenkds.domain.dict.MenuDictionary
import com.kitchenkds.domain.dict.MenuTable
import com.kitchenkds.domain.model.ItemCategory
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.domain.model.OrderKind

/**
 * 领域入口：把一帧原始文本行，变成一张可上屏的 Order。
 * 全 App 唯一"字符串 → 领域对象"的地方。
 *
 * V2 分类铁律：
 *   - 命中词典且条目是 BEVERAGE → BEVERAGE
 *   - 其余一切（含未命中词典的朝鲜文/中文/字母行）→ 强制 FOOD
 *   - 不存在"第三类"，DB 里也永远存不进第三种值
 */
class AssembleOrderUseCase {

    private companion object {
        const val TAG = "Assemble"
    }

    fun invoke(frame: RawFrame, seq: Int): Order? {
        val raw = frame.lines.filter { !isMarkerLine(it) }
        if (raw.isEmpty()) return null

        // 【V4.3】拆菜之前先做折行合并：把被 POS 硬拆开的菜名续行拼回上一行
        val body = OrderLineParser.foldWrappedLines(raw)
        if (body.size != raw.size) {
            RingLog.d(TAG, "fold: " + raw.size + " -> " + body.size + " lines")
        }

        val tableNo = TableNoExtractor.extract(body)
        val collected = ArrayList<OrderItem>(body.size)
        var sortIndex = 0

        for (line in body) {
            val parsed = OrderLineParser.parse(line) ?: continue

            // 【V4.2】先洗掉行尾的「追加」状态（향라오징어 1 추가 → 향라오징어），再查词典
            val dishName = TextNormalizer.cleanDishName(parsed.name)
            if (dishName.isEmpty()) continue

            // 修饰词：곱배기(加倍) 不是菜，整行丢弃
            val flat = dishName.replace(" ", "")
            if (MenuTable.IGNORED_TOKENS.contains(flat)) continue

            // 【V5.1】查词典只用纯菜名：上游若残留「空格 + 数量」，这里再剥一层；
            // 入库的 koreanName 仍是干净菜名（数量由 parsed.quantity 单独承载）。
            val lookupName = TextNormalizer.stripQuantitySuffix(dishName)
            val entry = MenuDictionary.lookup(lookupName)
            val category = when {
                entry == null -> ItemCategory.FOOD           // 未命中 → 强制菜品
                entry.category == ItemCategory.BEVERAGE -> ItemCategory.BEVERAGE
                else -> ItemCategory.FOOD
            }

            collected.add(
                OrderItem(
                    koreanName = dishName,
                    chineseName = entry?.chinese ?: dishName,
                    category = category,
                    quantity = parsed.quantity,
                    unitHint = MenuDictionary.extractUnitHint(dishName),
                    sortIndex = sortIndex++,
                    rawLine = line
                )
            )
        }

        if (collected.isEmpty()) {
            RingLog.w(TAG, "frame has no items, dropped")
            return null
        }

        val merged = mergeSameItems(collected)
        RingLog.d(
            TAG,
            "order table=" + tableNo + " kind=" + frame.kind +
                " items=" + merged.size +
                " food=" + merged.count { it.category == ItemCategory.FOOD } +
                " bev=" + merged.count { it.category == ItemCategory.BEVERAGE }
        )

        return Order.assemble(
            tableNo = tableNo,
            kind = if (frame.kind == OrderKind.UNKNOWN) OrderKind.NEW else frame.kind,
            receivedAtMillis = frame.receivedAtMillis,
            seq = seq,
            items = merged
        )
    }

    private fun isMarkerLine(line: String): Boolean {
        val c = line.replace(Regex("\\s+"), "")
        return c.contains("[주문서]") || c.contains("재주문서")
    }

    /** 同一道菜被打印机拆成多行时合并数量；合并后重排 sortIndex 保持打印顺序 */
    private fun mergeSameItems(items: List<OrderItem>): List<OrderItem> {
        val merged = LinkedHashMap<String, OrderItem>()
        for (item in items) {
            val key = item.koreanName + "#" + (item.unitHint ?: "")
            val exist = merged[key]
            if (exist == null) {
                merged[key] = item
            } else {
                merged[key] = exist.copy(quantity = exist.quantity + item.quantity)
            }
        }
        return merged.values.mapIndexed { index, item -> item.copy(sortIndex = index) }
    }
}
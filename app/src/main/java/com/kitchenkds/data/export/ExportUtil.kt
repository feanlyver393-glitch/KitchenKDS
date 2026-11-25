package com.kitchenkds.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.domain.model.ItemCategory
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.OrderKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史订单 / 运行日志导出到手机 Download 目录。
 *
 * Android 10(API 29) 起走 MediaStore，不需要任何存储权限；
 * Android 9 及以下退回公共目录 + WRITE_EXTERNAL_STORAGE（已在 Manifest 里以 maxSdkVersion=28 声明）。
 */
object ExportUtil {

    private const val TAG = "Export"

    enum class Format(val extension: String, val mime: String) {
        TXT("txt", "text/plain"),
        CSV("csv", "text/csv")
    }

    data class Result(val success: Boolean, val message: String, val uri: Uri? = null)

    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)

    // ─────────────────────── 订单导出 ───────────────────────

    fun exportOrders(context: Context, orders: List<Order>, format: Format): Result {
        if (orders.isEmpty()) {
            return Result(false, "没有可导出的订单")
        }
        val fileName = "KitchenKDS_orders_" + stampFmt.format(Date()) + "." + format.extension
        val content = when (format) {
            Format.TXT -> buildOrderTxt(orders)
            Format.CSV -> buildOrderCsv(orders)
        }
        return write(context, fileName, content, format.mime, addBom = format == Format.CSV)
    }

    fun buildOrderTxt(orders: List<Order>): String {
        val sb = StringBuilder(4096)
        sb.append("========== KitchenKDS 订单导出 ==========").append('\n')
        sb.append("导出时间: ").append(timeFmt.format(Date())).append('\n')
        sb.append("订单总数: ").append(orders.size).append('\n')
        val doneCount = orders.count { it.isCompleted }
        sb.append("已完成: ").append(doneCount).append("  未完成: ").append(orders.size - doneCount).append('\n')
        sb.append('\n')

        orders.forEachIndexed { index, order ->
            sb.append("[").append(index + 1).append("] ")
                .append(order.tableNo).append("号")
                .append("  ").append(kindLabel(order.kind))
                .append("  ").append(if (order.isCompleted) "已完成" else "进行中")
                .append('\n')
            sb.append("    下单: ").append(timeFmt.format(Date(order.receivedAtMillis)))
            order.completedAtMillis?.let {
                sb.append("    完成: ").append(timeFmt.format(Date(it)))
            }
            sb.append('\n')

            if (order.foodItems.isNotEmpty()) {
                sb.append("    --- 菜品 ---").append('\n')
                order.foodItems.forEach { item ->
                    sb.append("      ").append(item.koreanName)
                        .append("  <->  ").append(item.chineseName)
                        .append("   x").append(item.quantity)
                    if (item.unitHint != null) sb.append(" [").append(item.unitHint).append("]")
                    sb.append(if (item.isDone) "   [已出]" else "   [未出]")
                    sb.append('\n')
                }
            }
            if (order.beverageItems.isNotEmpty()) {
                sb.append("    --- 饮品 ---").append('\n')
                order.beverageItems.forEach { item ->
                    sb.append("      ").append(item.koreanName)
                        .append("  <->  ").append(item.chineseName)
                        .append("   x").append(item.quantity)
                    if (item.unitHint != null) sb.append(" [").append(item.unitHint).append("]")
                    sb.append(if (item.isDone) "   [已出]" else "   [未出]")
                    sb.append('\n')
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun buildOrderCsv(orders: List<Order>): String {
        val sb = StringBuilder(4096)
        sb.append("订单序号,订单ID,桌号,订单类型,状态,下单时间,完成时间,分类,韩文名,中文名,数量,规格,是否出餐\n")
        orders.forEachIndexed { index, order ->
            val base = StringBuilder()
            base.append(index + 1).append(',')
                .append(csv(order.id)).append(',')
                .append(csv(order.tableNo)).append(',')
                .append(csv(kindLabel(order.kind))).append(',')
                .append(csv(if (order.isCompleted) "已完成" else "进行中")).append(',')
                .append(csv(timeFmt.format(Date(order.receivedAtMillis)))).append(',')
                .append(csv(order.completedAtMillis?.let { timeFmt.format(Date(it)) } ?: "")).append(',')

            order.allItems.forEach { item ->
                sb.append(base)
                    .append(csv(if (item.category == ItemCategory.BEVERAGE) "饮品" else "菜品")).append(',')
                    .append(csv(item.koreanName)).append(',')
                    .append(csv(item.chineseName)).append(',')
                    .append(item.quantity).append(',')
                    .append(csv(item.unitHint ?: "")).append(',')
                    .append(csv(if (item.isDone) "是" else "否"))
                    .append('\n')
            }
        }
        return sb.toString()
    }

    // ─────────────────────── 日志导出 ───────────────────────

    fun exportLog(context: Context, logText: String): Result {
        val fileName = "KitchenKDS_log_" + stampFmt.format(Date()) + ".txt"
        val body = if (logText.isBlank()) "(无日志)" else logText
        return write(context, fileName, body, "text/plain", addBom = false)
    }

    /**
     * 周期报表用：单张订单的简短一行，便于打印小票核对。
     * 例如 "16号 17:00 新单 3项 已完成"
     */
    fun brief(order: Order): String {
        val total = order.allItems.sumOf { it.quantity }
        return order.tableNo + "号 " +
            clockFmt.format(Date(order.receivedAtMillis)) + " " +
            kindLabel(order.kind) + " " +
            total + "件 " +
            (if (order.isCompleted) "已完成" else "进行中")
    }

    // ─────────────────────── 落盘 ───────────────────────

    private fun write(
        context: Context,
        fileName: String,
        content: String,
        mime: String,
        addBom: Boolean
    ): Result {
        return try {
            val bytes = encode(content, addBom)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result(false, "无法创建下载条目")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: return Result(false, "无法写入下载目录")
                RingLog.d(TAG, "exported via MediaStore: " + fileName)
                Result(true, "已导出到 Download/" + fileName, uri)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                RingLog.d(TAG, "exported to " + file.absolutePath)
                Result(true, "已导出到 " + file.absolutePath)
            }
        } catch (t: Throwable) {
            RingLog.e(TAG, "export fail: " + t.message)
            Result(false, "导出失败: " + (t.message ?: "未知错误"))
        }
    }

    /** CSV 加 BOM，Excel 打开中文/韩文不乱码 */
    private fun encode(content: String, addBom: Boolean): ByteArray {
        val body = content.toByteArray(Charsets.UTF_8)
        if (!addBom) return body
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        return bom + body
    }

    private fun csv(raw: String): String {
        if (raw.isEmpty()) return ""
        val needQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        val escaped = raw.replace("\"", "\"\"")
        return if (needQuote) "\"" + escaped + "\"" else escaped
    }

    private fun kindLabel(kind: OrderKind): String = when (kind) {
        OrderKind.NEW -> "新单"
        OrderKind.REPRINT -> "重打"
        OrderKind.UNKNOWN -> "订单"
    }
}

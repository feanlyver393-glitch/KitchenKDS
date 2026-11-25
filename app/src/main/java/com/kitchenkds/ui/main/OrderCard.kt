package com.kitchenkds.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.core.ext.formatElapsed
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.UiSettings
import com.kitchenkds.ui.theme.KdsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * V4.2 订单卡片。
 *
 * 【结构改动】状态栏从底部搬到**顶部第一项**。
 * 原因：菜品多到卡片达到高度上限时，列表内部滚动，底部状态栏会被滚出去 ——
 * 后厨看不到桌号就等于丢单。现在状态栏在滚动容器之外，永远可见。
 *
 *   ┌──────────────────────────┐
 *   │ 16号          17:00 03:21 │  ← 状态栏（固定，天蓝/浅黄，圆角贴合）
 *   ├──────────────────────────┤
 *   │ 菜品                      │  ← 列表（独立滚动）
 *   │ 마라쇼롱샤 - 麻辣小龙虾 ×2 │
 *   │ 饮品                      │
 *   │ 참이슬 - 真露烧酒    ×3   │
 *   └──────────────────────────┘
 *
 * 高度：wrapContent，上限 = 屏高 × cardHeightRatio（30% ~ 100%，V4.3 取消了一半屏的硬限制）。
 * 蓝条字号 = 基础字号 × fontScale × headerScale，桌号 / 时间 / 计时共用同一倍率。
 */
private const val OLD_ORDER_THRESHOLD_MS = 15L * 60L * 1000L

@Composable
fun OrderCard(
    order: Order,
    nowMillis: Long,
    settings: UiSettings,
    onItemToggle: (String, Boolean) -> Unit,
    onStrikeThrough: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = KdsTheme.colors
    val cardCorner = 12.dp
    val shape = RoundedCornerShape(cardCorner)
    val thresholdPx = with(LocalDensity.current) { 88.dp.toPx() }
    var dragOffset by remember(order.id) { mutableFloatStateOf(0f) }
    val armed = abs(dragOffset) >= thresholdPx * 0.55f

    val configuration = LocalConfiguration.current
    // 【V4.3】废弃写死的 screenHeightDp / 2 半屏上限：
    // 改成「屏高 × 比例」，比例在设置页里 30% ~ 100% 自由调节（1.00 = 整屏自动撑开）
    val heightRatio = settings.cardHeightRatio
        .coerceIn(UiSettings.CARD_HEIGHT_RATIO_MIN, UiSettings.CARD_HEIGHT_RATIO_MAX)
    val maxCardHeight = (configuration.screenHeightDp.dp * heightRatio).coerceAtLeast(150.dp)

    val elapsed = (nowMillis - order.receivedAtMillis).coerceAtLeast(0L)
    val isOld = elapsed >= OLD_ORDER_THRESHOLD_MS
    val bottomBackground = if (isOld) c.bottomOld else c.bottomNew

    val clockText = remember(order.receivedAtMillis) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(order.receivedAtMillis))
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .heightIn(max = maxCardHeight)
            .graphicsLayer {
                translationX = dragOffset * 0.3f
                alpha = if (armed) 0.88f else 1f
            }
            .pointerInput(order.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onDragEnd = {
                        if (abs(dragOffset) >= thresholdPx) onStrikeThrough(order.id)
                        dragOffset = 0f
                    }
                ) { _, dragAmount ->
                    dragOffset = (dragOffset + dragAmount)
                        .coerceIn(-thresholdPx * 1.6f, thresholdPx * 1.6f)
                }
            }
    ) {
        Column(modifier = Modifier.heightIn(max = maxCardHeight)) {

            // ── 第一项：状态栏（置顶、固定、圆角贴合 Card 顶部） ──
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = cardCorner, topEnd = cardCorner))
                    .background(bottomBackground)
            ) {
                val compact = maxWidth < 230.dp
                val veryCompact = maxWidth < 180.dp

                // 【V4.3】蓝条字号：基础字号 × fontScale × headerScale
                // 桌号 / 时钟 / 计时器三者共用同一个倍率，绝不各自为政
                val headerTextScale = settings.headerTextScale
                val baseTable = when {
                    veryCompact -> 13f
                    compact -> 15f
                    else -> 18f
                }
                val baseTime = when {
                    veryCompact -> 9f
                    compact -> 10.5f
                    else -> 12f
                }
                val tableSize = (baseTable * headerTextScale).sp
                val timeSize = (baseTime * headerTextScale).sp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 只设"最小高度"，内容更高时自动撑开：大字号下蓝条不会截断文字
                        .heightIn(min = settings.headerHeight.dp)
                        .padding(
                            horizontal = if (compact) 8.dp else 10.dp,
                            vertical = if (compact) 5.dp else 7.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = order.tableNo + "号",
                        color = Color.Black,
                        fontSize = tableSize,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = clockText,
                        color = Color.White,
                        fontSize = timeSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(if (compact) 5.dp else 8.dp))
                    Text(
                        text = formatElapsed(elapsed),
                        color = Color.White,
                        fontSize = timeSize,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // ── 第二项：菜品 / 饮品列表（独立滚动，不会盖住状态栏） ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                ItemSection(
                    title = "菜品",
                    items = order.foodItems,
                    accent = c.foodAccent,
                    fontScale = settings.fontScale,
                    verticalSpacing = settings.itemSpacing,
                    onToggle = onItemToggle
                )
                ItemSection(
                    title = "饮品",
                    items = order.beverageItems,
                    accent = c.drinkAccent,
                    fontScale = settings.fontScale,
                    verticalSpacing = settings.itemSpacing,
                    onToggle = onItemToggle
                )

                if (armed) {
                    Text(
                        text = "松手 → 完成",
                        color = c.danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}

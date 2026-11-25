package com.kitchenkds.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.ui.theme.KdsTheme

/**
 * V4 单品行：单行「韩文 - 中文」，超宽 basicMarquee 横向滚动。
 *
 * 取消态（item.isCancelled）：文字变灰 + 红色双横线，表示这道菜不用做了。
 * 取消后重新下单时，数据库 isCancelled 变回 false，Compose 自动重绘、红线消失。
 */
@Composable
fun OrderItemRow(
    item: OrderItem,
    fontScale: Float,
    verticalSpacing: Float,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = KdsTheme.colors

    val baseColor = when {
        item.isCancelled -> Color.Gray.copy(alpha = 0.55f)
        item.isDone -> c.textDone
        else -> c.textPrimary
    }
    val textColor by animateColorAsState(targetValue = baseColor, label = "itemColor")

    val mainSize = (16f * fontScale).sp
    val qtySize = (15f * fontScale).sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onToggle(item.id, !item.isDone) }
            .padding(horizontal = 2.dp, vertical = (verticalSpacing / 2f).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.displayLine,
            color = textColor,
            fontSize = mainSize,
            fontWeight = if (item.isCancelled) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textDecoration = if (item.isDone && !item.isCancelled) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
            modifier = Modifier
                .weight(1f)
                .basicMarquee()
                .drawWithContent {
                    drawContent()
                    if (item.isCancelled) {
                        val stroke = 2.dp.toPx()
                        val y1 = size.height / 2f - 2.dp.toPx()
                        val y2 = size.height / 2f + 2.dp.toPx()
                        drawLine(
                            color = Color.Red,
                            start = Offset(0f, y1),
                            end = Offset(size.width, y1),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = Color.Red,
                            start = Offset(0f, y2),
                            end = Offset(size.width, y2),
                            strokeWidth = stroke
                        )
                    }
                }
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "× " + item.quantity,
            color = textColor,
            fontSize = qtySize,
            fontWeight = FontWeight.Black
        )
    }
}

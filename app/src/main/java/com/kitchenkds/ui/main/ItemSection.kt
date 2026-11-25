package com.kitchenkds.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.ui.common.SectionLabel

/** 分区容器：菜品区 / 饮品区，空列表整块 self-hide */
@Composable
fun ItemSection(
    title: String,
    items: List<OrderItem>,
    accent: Color,
    fontScale: Float,
    verticalSpacing: Float,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = title, color = accent)
        items.forEach { item ->
            OrderItemRow(
                item = item,
                fontScale = fontScale,
                verticalSpacing = verticalSpacing,
                onToggle = onToggle
            )
        }
    }
}

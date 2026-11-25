package com.kitchenkds.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.UiSettings

/**
 * 网格：列数由 DataStore 的 cardWidth 决定（Adaptive）。
 * V4：卡片高度改为 wrapContent（不再 fillMaxHeight），多单时自然堆叠，不浪费纵向空间。
 */
@Composable
fun OrderGrid(
    orders: List<Order>,
    nowMillis: Long,
    settings: UiSettings,
    onItemToggle: (String, Boolean) -> Unit,
    onStrikeThrough: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = settings.cardWidth.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = orders, key = { it.id }) { order ->
            OrderCard(
                order = order,
                nowMillis = nowMillis,
                settings = settings,
                onItemToggle = onItemToggle,
                onStrikeThrough = onStrikeThrough
            )
        }
    }
}

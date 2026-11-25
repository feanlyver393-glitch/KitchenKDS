package com.kitchenkds.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.domain.model.Order
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.EmptyHint
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: KdsUiState,
    onBack: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onUndoStrike: (String) -> Unit
) {
    val c = KdsTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "历史记录", onBack = onBack) {
            TextButton(onClick = onToggleSelectionMode) {
                Text(
                    text = if (state.selectionMode) "取消" else "选择",
                    color = if (state.selectionMode) c.danger else c.accent,
                    fontSize = 13.sp
                )
            }
            TextButton(onClick = onSelectAll, enabled = state.selectionMode) {
                Text(
                    text = if (state.allSelected) "取消全选" else "全选",
                    color = if (state.selectionMode) c.accent else c.textSecondary.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }
            TextButton(onClick = onDeleteSelected) {
                Text(text = "删除", color = c.danger, fontSize = 13.sp)
            }
        }

        KdsDivider()

        if (state.historyOrders.isEmpty()) {
            EmptyHint("暂无历史订单")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.historyOrders, key = { it.id }) { order ->
                    HistoryRow(
                        order = order,
                        selectionMode = state.selectionMode,
                        selected = state.selectedIds.contains(order.id),
                        onClick = {
                            if (state.selectionMode) onToggleSelect(order.id) else onUndoStrike(order.id)
                        },
                        onUndo = { onUndoStrike(order.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    order: Order,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onUndo: () -> Unit
) {
    val c = KdsTheme.colors
    val timeFmt = remember(order.completedAtMillis) {
        SimpleDateFormat("MM-dd HH:mm", Locale.US)
    }
    val completeText = order.completedAtMillis?.let { timeFmt.format(Date(it)) } ?: "--"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) c.accent else c.surfaceHigh)
                )
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.tableNo + "号",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = order.totalQuantity.toString() + " 件 / " + order.itemCount + " 项",
                        color = c.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "完成于 " + completeText,
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
            }

            if (!selectionMode) {
                IconButton(onClick = onUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = "撤销回主界面",
                        tint = c.textSecondary
                    )
                }
            }
        }
    }
}

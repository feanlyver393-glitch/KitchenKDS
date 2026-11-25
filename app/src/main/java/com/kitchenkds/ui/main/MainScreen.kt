package com.kitchenkds.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.ui.common.ConnectionDot
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState

/**
 * V4.3 主界面：右上角悬浮控件条 → 极窄独立顶栏（32dp）。
 *
 * 旧版把「订单数 / 历史 / 设置」浮在网格上面（Box 叠放），
 * 图标正好压住最右侧那张卡片的蓝色状态栏 → 桌号与时间被遮。
 * 现在改成 Column 布局里的真实顶栏：它自己占 32dp，网格从它下面开始，
 * 任何一张卡片的桌号 / 时间都不可能再被图标盖住。
 */
private val TOP_BAR_HEIGHT = 32.dp

@Composable
fun MainScreen(
    state: KdsUiState,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onItemToggle: (String, Boolean) -> Unit,
    onStrikeThrough: (String) -> Unit,
    onLaunchSilentUpdateCheck: () -> Unit = {}
) {
    val c = KdsTheme.colors

    // V5.1：主界面首次进入时后台静默检查一次更新（失败静默，不弹任何东西）
    LaunchedEffect(Unit) {
        onLaunchSilentUpdateCheck()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        // ── 极窄顶栏：只放状态与两个入口，高度 32dp ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOP_BAR_HEIGHT)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.activeCount > 0) "订单 " + state.activeCount else "无进行中订单",
                color = c.textSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            ConnectionDot(connected = state.isConnected, size = 8)
            Spacer(Modifier.width(10.dp))
            BarIconButton(
                icon = Icons.Outlined.History,
                contentDescription = "历史记录",
                onClick = onOpenHistory
            )
            Spacer(Modifier.width(4.dp))
            BarIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "设置",
                onClick = onOpenSettings
            )
        }

        if (state.activeOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "等待订单...",
                    color = c.textSecondary,
                    fontSize = 22.sp,
                    letterSpacing = 3.sp
                )
            }
        } else {
            OrderGrid(
                orders = state.activeOrders,
                nowMillis = state.nowMillis,
                settings = state.settings,
                onItemToggle = onItemToggle,
                onStrikeThrough = onStrikeThrough,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }
    }
}

/** 顶栏图标：28dp 点按区，避开 IconButton 强制 48dp 触控尺寸把 32dp 顶栏撑破 */
@Composable
private fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val c = KdsTheme.colors
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = c.textSecondary,
            modifier = Modifier.size(17.dp)
        )
    }
}

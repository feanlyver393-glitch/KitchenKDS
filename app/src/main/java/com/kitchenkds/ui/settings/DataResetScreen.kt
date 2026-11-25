package com.kitchenkds.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel

/**
 * 二级菜单：数据重置。
 *   ① 订单重置（手动，二次确认）
 *   ② 定时重置（三级）
 */
@Composable
fun DataResetScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    val c = KdsTheme.colors
    var showConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BackTopBar(title = "数据重置", onBack = onBack)
        KdsDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showConfirm = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "订单重置", color = c.danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "清空全部进行中与历史订单（当前 " +
                        state.activeCount + " + " + state.historyCount + " 条），不可恢复",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
        KdsDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSchedule() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "定时重置", color = c.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (state.settings.autoResetEnabled) {
                        "已开启 · 每天 " + pad(state.settings.autoResetHour) + ":" +
                            pad(state.settings.autoResetMinute) + " 自动清理"
                    } else {
                        "关闭 · 每天固定时刻后台自动清空订单库"
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.textSecondary
            )
        }
        KdsDivider()

        Spacer(Modifier.height(12.dp))
        Text(
            text = "定时重置由 WorkManager 在后台执行，App 被划掉也会按时清理；" +
                "重启手机后任务会自动恢复。",
            color = c.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = c.surface,
            title = {
                Text(text = "确认清空所有订单？", color = c.textPrimary, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "将删除数据库中全部进行中与历史订单，此操作不可撤销。\n" +
                        "建议先在「数据导出」里导出一份 CSV 留档。",
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllOrders()
                    showConfirm = false
                }) {
                    Text(text = "确认清空", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(text = "取消", color = Color.Gray)
                }
            }
        )
    }
}

private fun pad(v: Int): String = if (v < 10) "0" + v else v.toString()

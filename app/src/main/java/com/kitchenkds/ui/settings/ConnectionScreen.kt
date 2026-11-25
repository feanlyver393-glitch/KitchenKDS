package com.kitchenkds.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.core.state.SerialState
import com.kitchenkds.data.serial.SerialConfig
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel

/** 设置 → 连接状态：把主界面曾经那堆串口文本全部收纳在这里 */
@Composable
fun ConnectionScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit
) {
    val c = KdsTheme.colors
    var logText by remember { mutableStateOf(vm.dumpLog()) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "连接状态", onBack = onBack) {
            TextButton(onClick = { logText = vm.dumpLog() }) {
                Text(text = "刷新", color = c.accent, fontSize = 13.sp)
            }
        }
        KdsDivider()

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            InfoRow("串口状态", statusText(state.serial))
            InfoRow("设备", deviceText(state.serial))
            InfoRow(
                "通信参数",
                SerialConfig.BAUD_RATE.toString() + " bps · " +
                    SerialConfig.DATA_BITS + "-" + parityText() + "-" + SerialConfig.STOP_BITS + " (8-N-1)"
            )
            InfoRow("编码", "EUC-KR / CP949（三级回退）")
            InfoRow("订单库", "进行中 " + state.activeCount + " / 历史 " + state.historyCount)

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.onRetryConnection() }) {
                    Text(text = "重连串口", color = c.accent, fontSize = 13.sp)
                }
                TextButton(onClick = { vm.clearLog(); logText = vm.dumpLog() }) {
                    Text(text = "清空日志", color = c.textSecondary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "运行日志（最近 200 条）",
                color = c.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = logText.ifBlank { "(暂无日志)" },
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = c.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            color = c.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun statusText(serial: SerialState): String = when (serial) {
    is SerialState.Connected -> "已连接"
    SerialState.Disconnected -> "未连接"
    SerialState.PermissionPending -> "等待 USB 授权"
    SerialState.PermissionDenied -> "USB 授权被拒绝"
    SerialState.NoDevice -> "未检测到设备"
    is SerialState.Error -> "故障：" + serial.message
}

private fun deviceText(serial: SerialState): String =
    if (serial is SerialState.Connected) serial.deviceName else "—"

private fun parityText(): String = when (SerialConfig.PARITY) {
    0 -> "N"
    1 -> "O"
    2 -> "E"
    else -> "N"
}

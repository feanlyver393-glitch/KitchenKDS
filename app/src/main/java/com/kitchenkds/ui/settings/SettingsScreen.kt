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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenkds.BuildConfig
import com.kitchenkds.domain.model.AlertSounds
import com.kitchenkds.core.state.SerialState
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.ConnectionDot
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel
import java.util.Locale

/**
 * 一级菜单：设置。
 *
 * V4：
 *   - 根布局加 verticalScroll，横屏小高度下也能滑到底
 *   - 「数据导出」收进独立的三级页，首页只留入口
 *   - 新增「音效设置」「数据重置」两个二级入口
 */
@Composable
fun SettingsScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenSize: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenDataReset: () -> Unit
) {
    val c = KdsTheme.colors
    val s = state.settings
    // V5.1：在线更新状态（检查中 → 按钮转圈）
    val update by vm.updateState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BackTopBar(title = "设置", onBack = onBack)
        KdsDivider()

        GroupLabel("显示与界面")

        SettingRow(
            title = "连接状态",
            subtitle = statusText(state.serial),
            onClick = onOpenConnection
        ) {
            ConnectionDot(connected = state.isConnected, modifier = Modifier.padding(end = 6.dp))
            Chevron()
        }

        SettingRow(
            title = "尺寸调整",
            subtitle = "宽度 " + s.cardWidth.toInt() + "dp · 高度 " +
                String.format(Locale.US, "%.0f", s.cardHeightRatio * 100f) + "%屏 · 字号 " +
                String.format(Locale.US, "%.1f", s.fontScale) + "x · 行距 " +
                s.itemSpacing.toInt() + "dp",
            onClick = onOpenSize
        ) { Chevron() }

        SettingRow(
            title = "主题设置",
            subtitle = s.themeMode.label,
            onClick = onOpenTheme
        ) { Chevron() }

        KdsDivider()
        GroupLabel("音效与提醒")

        SettingRow(
            title = "音效设置",
            subtitle = if (s.soundEnabled) {
                AlertSounds.labelOf(s.soundMode, s.customSoundName) + " · " +
                    s.soundTimes + " 声 · 间隔 " + s.soundIntervalSec + "秒" +
                    if (s.soundUseAlarmChannel) " · 闹钟通道" else " · 媒体通道"
            } else {
                "已关闭"
            },
            onClick = onOpenSound
        ) { Chevron() }

        KdsDivider()
        GroupLabel("数据管理")

        SettingRow(
            title = "数据导出",
            subtitle = "订单 TXT / CSV、运行日志",
            onClick = onOpenExport
        ) { Chevron() }

        SettingRow(
            title = "数据重置",
            subtitle = if (s.autoResetEnabled) {
                "手动清空 · 每天 " + pad(s.autoResetHour) + ":" + pad(s.autoResetMinute) + " 自动清理"
            } else {
                "手动清空 · 定时清理未开启"
            },
            onClick = onOpenDataReset
        ) { Chevron() }

        KdsDivider()
        GroupLabel("软件版本与更新")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前版本 v" + BuildConfig.VERSION_NAME,
                    color = c.textPrimary,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (update.checking) {
                        "正在向 GitHub 查询最新版本…"
                    } else {
                        "从 GitHub Releases 获取更新，下载后可直接覆盖安装"
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = { vm.checkUpdateManually() },
                enabled = !update.checking,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent)
            ) {
                if (update.checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = c.surface
                    )
                } else {
                    Text(text = "检查更新", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        InfoRow(
            label = "订单库",
            value = "进行中 " + state.activeCount + " / 历史 " + state.historyCount
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun pad(v: Int): String = if (v < 10) "0" + v else v.toString()

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = KdsTheme.colors.textSecondary
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        color = KdsTheme.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = c.textPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(text = subtitle, color = c.textSecondary, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = c.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = c.textSecondary, fontSize = 13.sp)
    }
}

private fun statusText(serial: SerialState): String = when (serial) {
    is SerialState.Connected -> "已连接 · " + serial.deviceName
    SerialState.Disconnected -> "未连接"
    SerialState.PermissionPending -> "等待 USB 授权"
    SerialState.PermissionDenied -> "USB 授权被拒绝"
    SerialState.NoDevice -> "未检测到串口设备"
    is SerialState.Error -> "故障 · " + serial.message
}

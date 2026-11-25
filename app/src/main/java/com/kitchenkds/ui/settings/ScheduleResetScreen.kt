package com.kitchenkds.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.data.work.ResetScheduler
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel
import java.util.Calendar

/**
 * 三级菜单：定时重置（V4.1）。
 *
 * 行为与手机"定时关机"一致 —— 按系统日期时间在每天的 HH:mm 触发，
 * 不是从设置那一刻起算的倒计时。
 *
 * 显示的是**下次执行的绝对时间**（11-04 04:00 周二），而不是"还剩几小时"，
 * 因为后者会让人误以为是倒计时。
 */
@Composable
fun ScheduleResetScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit
) {
    val c = KdsTheme.colors
    val s = state.settings
    val context = LocalContext.current

    val triggerAt = ResetScheduler.nextTriggerMillis(s.autoResetHour, s.autoResetMinute)
    val exactOk = ResetScheduler.canScheduleExact(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BackTopBar(title = "定时重置", onBack = onBack)
        KdsDivider()

        // ── 开关 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "开启自动清理", color = c.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (s.autoResetEnabled) "已开启，按手机系统时间每天执行" else "当前关闭",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Checkbox(
                checked = s.autoResetEnabled,
                onCheckedChange = { vm.setAutoResetEnabled(it) },
                colors = CheckboxDefaults.colors(checkedColor = c.accent)
            )
        }
        KdsDivider()

        // ── 下次执行（绝对时间） ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "下次执行",
                color = c.textSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (s.autoResetEnabled) {
                    ResetScheduler.describe(triggerAt)
                } else {
                    "未调度"
                },
                color = c.accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "系统当前时间：" + ResetScheduler.describe(System.currentTimeMillis()),
                color = c.textSecondary.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
        KdsDivider()

        // ── 时间设置 ──
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "时间设置（每天）",
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            StepperRow(
                label = "小时（0-23）",
                valueText = pad(s.autoResetHour) + " 时",
                onMinus = { vm.setAutoResetTime((s.autoResetHour + 23) % 24, s.autoResetMinute) },
                onPlus = { vm.setAutoResetTime((s.autoResetHour + 1) % 24, s.autoResetMinute) }
            )

            Spacer(Modifier.height(12.dp))

            StepperRow(
                label = "分钟（5 分一档）",
                valueText = pad(s.autoResetMinute) + " 分",
                onMinus = { vm.setAutoResetTime(s.autoResetHour, (s.autoResetMinute + 55) % 60) },
                onPlus = { vm.setAutoResetTime(s.autoResetHour, (s.autoResetMinute + 5) % 60) }
            )

            Spacer(Modifier.height(8.dp))

            Row {
                QuickChip("04:00") { vm.setAutoResetTime(4, 0) }
                Spacer(Modifier.width(6.dp))
                QuickChip("09:00") { vm.setAutoResetTime(9, 0) }
                Spacer(Modifier.width(6.dp))
                QuickChip("23:00") { vm.setAutoResetTime(23, 0) }
            }
        }

        // ── 精确定时权限 ──
        if (!exactOk) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceHigh)
                    .padding(12.dp)
            ) {
                Text(
                    text = "系统未授予「精确定时」权限",
                    color = c.warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "清理仍会在设定时刻触发，但可能被系统推迟几分钟。" +
                        "授予权限后即可精确到分钟。",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                TextButton(onClick = {
                    openExactAlarmSettings(context)
                }) {
                    Text(text = "去授权", color = c.accent, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "执行内容：清空 orders 与 order_items 全部数据。\n" +
                "改为按手机系统时间调度（AlarmManager RTC 闹钟），不是倒计时；" +
                "重启手机、手动改系统时间或改时区后会自动按新时间重新排队。",
            color = c.textSecondary,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    val c = KdsTheme.colors
    TextButton(onClick = onClick) {
        Text(text = text, color = c.accent, fontSize = 12.sp)
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:" + context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (t: Throwable) {
        // 部分机型没有该设置页，忽略
    }
}

private fun pad(v: Int): String = if (v < 10) "0" + v else v.toString()

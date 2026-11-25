package com.kitchenkds.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import com.kitchenkds.core.log.RingLog
import java.util.Calendar
import java.util.Locale

/**
 * 每日定时重置调度器（V4.1 重写）。
 *
 * 【为什么不用倒计时】
 * 倒计时（delay / WorkManager initialDelay）算的是"从入队那一刻起过了多久"，
 * 设备休眠、Doze、进程重启都会让实际触发点漂移，且与系统日期时间脱钩。
 *
 * 【现在用的是系统墙上时间】
 * AlarmManager 的 RTC_WAKEUP 类型，触发点直接写成"下一个 HH:mm 的绝对时间戳"，
 * 语义与手机自带的定时关机完全一致：
 *   - 到了系统时间 09:00 就触发
 *   - 用户手动改系统时间 / 改时区后，由 BootReceiver 重新按新时间排队
 *
 * 精度：
 *   Android 12+ 若未授予 SCHEDULE_EXACT_ALARM，降级为 setAndAllowWhileIdle（可能延迟几分钟）
 *   Android 11 及以下、或已授权 → setExactAndAllowWhileIdle（精确到分钟）
 */
object ResetScheduler {

    private const val TAG = "ResetSched"
    const val ACTION_DAILY_RESET = "com.kitchenkds.action.DAILY_RESET"
    const val EXTRA_HOUR = "reset_hour"
    const val EXTRA_MINUTE = "reset_minute"
    private const val REQUEST_CODE = 0x4B44

    /** 排下一天（也用于首次开启、开机自启、改时间后重建） */
    fun schedule(context: Context, hour: Int, minute: Int): Long {
        val triggerAt = nextTriggerMillis(hour, minute)
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = buildPendingIntent(context, hour, minute)

            val exact = canScheduleExact(context)
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            } else {
                // 没有精确闹钟权限：仍然是绝对时间点，只是系统可能合并投递
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            }
            RingLog.d(
                TAG,
                "armed " + describe(triggerAt) + " exact=" + exact +
                    " (now=" + describe(System.currentTimeMillis()) + ")"
            )
        } catch (t: Throwable) {
            RingLog.e(TAG, "schedule fail: " + t.message)
        }
        return triggerAt
    }

    fun cancel(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(buildPendingIntent(context, 0, 0, create = false))
            RingLog.d(TAG, "cancelled")
        } catch (t: Throwable) {
            RingLog.e(TAG, "cancel fail: " + t.message)
        }
    }

    /**
     * 下一个 HH:mm 的绝对时间戳。
     * 今天该时刻已过 → 顺延到明天；否则就是今天。
     * 完全基于系统当前日期时间，不做任何"剩余秒数"的有状态计算。
     */
    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    /** 是否拥有精确定时权限（Android 12+ 需要用户授权） */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } catch (t: Throwable) {
            false
        }
    }

    /** 跳系统设置页让用户授权精确定时 */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:" + context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** 供 UI 显示："11-04 04:00 周二" */
    fun describe(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val date = DateFormat.format("MM-dd HH:mm", cal).toString()
        val week = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }
        return date + " " + week
    }

    private fun buildPendingIntent(
        context: Context,
        hour: Int,
        minute: Int,
        create: Boolean = true
    ): PendingIntent {
        val intent = Intent(context, DailyResetReceiver::class.java).apply {
            action = ACTION_DAILY_RESET
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (!create) flags = flags or PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            ?: PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}

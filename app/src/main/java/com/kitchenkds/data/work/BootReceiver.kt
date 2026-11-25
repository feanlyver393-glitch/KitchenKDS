package com.kitchenkds.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 系统时间相关的自愈（V4.1）。
 *
 * 三种情况都必须重新排队，否则"每天 04:00 清库"会静默失效：
 *   ① 手机重启（BOOT_COMPLETED）              —— AlarmManager 的闹钟不跨重启
 *   ② 用户手动改系统时间（TIME_SET）           —— 绝对时间戳全变了
 *   ③ 用户改时区（TIMEZONE_CHANGED）           —— 同上
 *   ④ App 被覆盖安装（MY_PACKAGE_REPLACED）    —— 闹钟随旧包一起消失
 *
 * 这正是"按系统日期时间"与"按倒计时"的本质区别：
 * 倒计时不怕改时间，因为它是相对量；而我们要的就是跟随手机时钟。
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val handled = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!handled) return

        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = AppPreferences(appContext).settings.first()
                if (settings.autoResetEnabled) {
                    ResetScheduler.schedule(
                        appContext,
                        settings.autoResetHour,
                        settings.autoResetMinute
                    )
                    RingLog.d(
                        TAG,
                        action + " → re-armed " +
                            pad(settings.autoResetHour) + ":" + pad(settings.autoResetMinute)
                    )
                } else {
                    RingLog.d(TAG, action + " → auto reset disabled, skip")
                }
            } catch (t: Throwable) {
                RingLog.e(TAG, "re-arm fail: " + t.message)
            } finally {
                pending.finish()
            }
        }
    }

    private fun pad(v: Int): String = if (v < 10) "0" + v else v.toString()
}

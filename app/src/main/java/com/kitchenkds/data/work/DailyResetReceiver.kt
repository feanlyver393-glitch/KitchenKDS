package com.kitchenkds.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟到点后的执行体（V4.1）。
 *
 * 由 AlarmManager 在系统时间达到设定值（如每天 04:00）时拉起，
 * 即使 App 已被划掉、甚至没打开过，系统也会唤醒进程投递这个广播。
 *
 * 执行顺序：清库 → 立刻排明天 → finish()。
 * 先排明天再 finish，避免清理过程中被系统回收导致"只执行一次"。
 */
class DailyResetReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "DailyReset"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ResetScheduler.ACTION_DAILY_RESET) return

        val hour = intent.getIntExtra(ResetScheduler.EXTRA_HOUR, 4)
        val minute = intent.getIntExtra(ResetScheduler.EXTRA_MINUTE, 0)
        val appContext = context.applicationContext
        val pending = goAsync()

        RingLog.w(TAG, "alarm fired at " + ResetScheduler.describe(System.currentTimeMillis()))

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppDatabase.get(appContext).orderDao().deleteEverything()
                RingLog.w(TAG, "orders cleared, rescheduling for tomorrow " + pad(hour) + ":" + pad(minute))
            } catch (t: Throwable) {
                RingLog.e(TAG, "clear fail: " + t.message)
            } finally {
                // 无论成功失败都续约下一天
                ResetScheduler.schedule(appContext, hour, minute)
                pending.finish()
            }
        }
    }

    private fun pad(v: Int): String = if (v < 10) "0" + v else v.toString()
}

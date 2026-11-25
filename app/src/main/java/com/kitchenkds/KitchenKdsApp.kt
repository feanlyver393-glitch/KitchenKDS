package com.kitchenkds

import android.app.Application
import com.kitchenkds.core.audio.AlertSoundPlayer
import com.kitchenkds.data.local.AppDatabase
import com.kitchenkds.data.prefs.AppPreferences
import com.kitchenkds.data.repository.OrderRepository
import com.kitchenkds.data.serial.UsbSerialHub
import com.kitchenkds.data.work.ResetScheduler
import com.kitchenkds.domain.dict.MenuDictionary
import com.kitchenkds.domain.usecase.AssembleOrderUseCase
import com.kitchenkds.domain.usecase.OrderIngestUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 手写依赖容器（不引入 Hilt）。
 *
 * 注意：这里 **不主动启动串口**。
 * 进程可能只是被定时清理的闹钟唤醒，那种情况下不该去申请 USB 权限、也不该弹任何窗。
 * 串口由 KdsViewModel 在界面真正创建时启动。
 */
class KitchenKdsApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var orderRepository: OrderRepository
        private set

    lateinit var serialHub: UsbSerialHub
        private set

    lateinit var preferences: AppPreferences
        private set

    /** V5 来单提示音引擎（MediaPlayer + ToneGenerator 兜底） */
    lateinit var soundPlayer: AlertSoundPlayer
        private set

    lateinit var ingestUseCase: OrderIngestUseCase
        private set

    val assembleOrderUseCase: AssembleOrderUseCase by lazy { AssembleOrderUseCase() }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MenuDictionary.build()

        database = AppDatabase.get(this)
        orderRepository = OrderRepository(database.orderDao())

        preferences = AppPreferences(this)
        soundPlayer = AlertSoundPlayer(this)
        ingestUseCase = OrderIngestUseCase(orderRepository, assembleOrderUseCase)

        serialHub = UsbSerialHub(this)

        // 若定时清理处于开启状态，进进程就补排一次（防止被杀后闹钟丢失）
        appScope.launch {
            try {
                val settings = preferences.settings.first()
                if (settings.autoResetEnabled) {
                    ResetScheduler.schedule(
                        this@KitchenKdsApp,
                        settings.autoResetHour,
                        settings.autoResetMinute
                    )
                }
            } catch (t: Throwable) {
                // 冷启动期的异常不影响主流程
            }
        }
    }
}

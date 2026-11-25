package com.kitchenkds.data.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.core.state.SerialState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * USB 串口中枢：权限申请 + CH340 枚举 + 打开 + 读协程 + 掉线自愈。
 *
 * 设计要点：
 *  - 整个 App 只有一个实例（挂在 Application 上），Activity 重建不断连；
 *  - 读循环永不退出：read 抛 IOException 就关口，重连协程 2 秒后再扫一次；
 *  - 权限被拒不会弹第二次（避免对话框风暴），改为 UI 上一个"重新申请"按钮。
 */
class UsbSerialHub(private val appContext: Context) {

    companion object {
        private const val TAG = "UsbSerial"
        const val ACTION_USB_PERMISSION = "com.kitchenkds.USB_PERMISSION"
    }

    private val usbManager =
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SerialState>(SerialState.Disconnected)
    val state: StateFlow<SerialState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SerialEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SerialEvent> = _events.asSharedFlow()

    private var port: UsbSerialPort? = null
    private var readJob: Job? = null
    private var supervisorJob: Job? = null
    private var registered = false

    @Volatile private var permissionRequestedFor: Int = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    RingLog.d(TAG, "permission granted = " + granted)
                    if (granted) {
                        _state.value = SerialState.Disconnected
                        openNow()
                    } else {
                        _state.value = SerialState.PermissionDenied
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    RingLog.d(TAG, "device attached")
                    permissionRequestedFor = -1
                    openNow()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    RingLog.d(TAG, "device detached")
                    permissionRequestedFor = -1
                    closePort()
                }
            }
        }
    }

    // ─────────────────────────── 生命周期 ───────────────────────────

    fun start() {
        registerReceiver()
        startSupervisor()
    }

    fun stop() {
        supervisorJob?.cancel()
        supervisorJob = null
        closePort()
        unregisterReceiver()
    }

    /** UI 上"重新连接"按钮 */
    fun retry() {
        permissionRequestedFor = -1
        closePort()
        startSupervisor()
    }

    private fun registerReceiver() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    private fun unregisterReceiver() {
        if (!registered) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (t: Throwable) {
            RingLog.w(TAG, "unregister: " + t.message)
        }
        registered = false
    }

    private fun startSupervisor() {
        if (supervisorJob?.isActive == true) return
        supervisorJob = scope.launch {
            while (isActive) {
                if (port == null) {
                    tryConnect()
                }
                delay(SerialConfig.RECONNECT_DELAY_MS)
            }
        }
    }

    // ─────────────────────────── 连接 ───────────────────────────

    private fun openNow() {
        scope.launch { tryConnect() }
    }

    private fun tryConnect() {
        if (port != null) return

        val drivers: List<UsbSerialDriver> = try {
            UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        } catch (t: Throwable) {
            RingLog.e(TAG, "probe fail: " + t.message)
            emptyList()
        }

        val driver = drivers.firstOrNull()
        if (driver == null) {
            _state.value = SerialState.NoDevice
            RingLog.w(TAG, "no usb-serial device found")
            return
        }

        val device: UsbDevice = driver.device
        val name = describe(driver)
        RingLog.d(TAG, "found driver: " + name)

        if (!usbManager.hasPermission(device)) {
            if (permissionRequestedFor != device.deviceId) {
                permissionRequestedFor = device.deviceId
                _state.value = SerialState.PermissionPending
                requestPermission(device)
            }
            return
        }

        openDriver(driver, name)
    }

    private fun requestPermission(device: UsbDevice) {
        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
            val pi = PendingIntent.getBroadcast(appContext, 0, intent, flags)
            usbManager.requestPermission(device, pi)
            RingLog.d(TAG, "requestPermission for " + device.deviceId)
        } catch (t: Throwable) {
            RingLog.e(TAG, "requestPermission fail: " + t.message)
            _state.value = SerialState.Error("权限申请失败")
        }
    }

    private fun openDriver(driver: UsbSerialDriver, name: String) {
        var opened: UsbSerialPort? = null
        try {
            val connection = usbManager.openDevice(driver.device)
                ?: throw IOException("openDevice returned null")

            val newPort = driver.ports[0]
            newPort.open(connection)
            newPort.setParameters(
                SerialConfig.BAUD_RATE,
                SerialConfig.DATA_BITS,
                SerialConfig.STOP_BITS,
                SerialConfig.PARITY
            )
            // 部分 CH340 固件需要显式清 DTR/RTS，否则收不到数据
            try {
                newPort.dtr = true
                newPort.rts = true
            } catch (t: Throwable) {
                RingLog.w(TAG, "dtr/rts: " + t.message)
            }

            opened = newPort
            port = newPort
            _state.value = SerialState.Connected(name, SerialConfig.BAUD_RATE)
            RingLog.d(TAG, "opened " + name + " @ " + SerialConfig.BAUD_RATE)
            startReadLoop(newPort)
        } catch (t: Throwable) {
            RingLog.e(TAG, "open fail: " + t.message)
            try {
                opened?.close()
            } catch (ignore: Throwable) {
            }
            port = null
            _state.value = SerialState.Error(t.message ?: "串口打开失败")
        }
    }

    private fun describe(driver: UsbSerialDriver): String {
        val d = driver.device
        val vendor = String.format("0x%04X", d.vendorId)
        val product = String.format("0x%04X", d.productId)
        val cn = driver.javaClass.simpleName
        return cn + " [" + vendor + ":" + product + "]"
    }

    // ─────────────────────────── 读循环 ───────────────────────────

    private fun startReadLoop(target: UsbSerialPort) {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(SerialConfig.BUFFER_SIZE)
            RingLog.d(TAG, "read loop start")
            while (isActive) {
                val p = port
                if (p == null || p !== target) break
                try {
                    val n = p.read(buffer, SerialConfig.READ_TIMEOUT_MS)
                    if (n > 0) {
                        val copy = buffer.copyOf(n)
                        _events.tryEmit(SerialEvent.Data(copy))
                    } else {
                        _events.tryEmit(SerialEvent.Idle)
                    }
                } catch (t: Throwable) {
                    RingLog.e(TAG, "read loop break: " + t.message)
                    _events.tryEmit(SerialEvent.Failure(t.message ?: "读取中断"))
                    closePort()
                    break
                }
            }
            RingLog.d(TAG, "read loop exit")
        }
    }

    @Synchronized
    private fun closePort() {
        readJob?.cancel()
        readJob = null
        val p = port
        port = null
        try {
            p?.close()
        } catch (t: Throwable) {
            RingLog.w(TAG, "close: " + t.message)
        }
        if (_state.value !is SerialState.PermissionDenied) {
            _state.value = SerialState.Disconnected
        }
    }
}

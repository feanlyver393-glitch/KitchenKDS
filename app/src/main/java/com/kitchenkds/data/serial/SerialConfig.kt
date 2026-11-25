package com.kitchenkds.data.serial

import com.hoho.android.usbserial.driver.UsbSerialPort

/** 现场唯一的通信契约：9600, 8-N-1（Easy POS → RJ45/DB9 → CH340 → Type-C） */
object SerialConfig {
    const val BAUD_RATE = 9600
    const val DATA_BITS = 8
    const val STOP_BITS = UsbSerialPort.STOPBITS_1
    const val PARITY = UsbSerialPort.PARITY_NONE

    /** 单次 read 的阻塞超时；返回 0 即视为一次空闲心跳 */
    const val READ_TIMEOUT_MS = 200
    const val BUFFER_SIZE = 1024

    const val RECONNECT_DELAY_MS = 2000L
    const val PERMISSION_RETRY_MS = 5000L
}

package com.kitchenkds.data.serial

sealed interface SerialEvent {
    /** 收到一段原始字节（未剥离 ESC/POS、未解码） */
    data class Data(val bytes: ByteArray) : SerialEvent

    /** 读超时，无数据：供上层做空闲封包 */
    data object Idle : SerialEvent

    /** 链路故障，上层只做提示，不终止重连 */
    data class Failure(val message: String) : SerialEvent
}

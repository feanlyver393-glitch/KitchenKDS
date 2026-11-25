package com.kitchenkds.core.state

/** 串口链路状态：放在中立层，data 与 ui 双向可见，不产生依赖倒挂 */
sealed interface SerialState {
    data object Disconnected : SerialState
    data object PermissionPending : SerialState
    data object PermissionDenied : SerialState
    data object NoDevice : SerialState
    data class Connected(val deviceName: String, val baudRate: Int) : SerialState
    data class Error(val message: String) : SerialState
}

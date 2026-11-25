package com.kitchenkds.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.kitchenkds.core.state.SerialState
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.UiSettings

/** 页面栈（手写导航，不引 navigation-compose） */
enum class Screen {
    Main,
    History,
    Settings,
    Connection,
    SizeSettings,
    ThemeSettings,
    Export,
    SoundSettings,
    DataReset,
    ScheduleReset
}

@Immutable
data class KdsUiState(
    val serial: SerialState = SerialState.Disconnected,
    val activeOrders: List<Order> = emptyList(),
    val historyOrders: List<Order> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val screen: Screen = Screen.Main,
    val settings: UiSettings = UiSettings()
) {
    val isConnected: Boolean get() = serial is SerialState.Connected
    val activeCount: Int get() = activeOrders.size
    val historyCount: Int get() = historyOrders.size
    val allSelected: Boolean
        get() = historyOrders.isNotEmpty() && selectedIds.size == historyOrders.size
}

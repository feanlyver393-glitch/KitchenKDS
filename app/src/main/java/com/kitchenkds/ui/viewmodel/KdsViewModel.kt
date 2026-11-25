package com.kitchenkds.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.kitchenkds.BuildConfig
import com.kitchenkds.core.audio.AlertSoundPlayer
import com.kitchenkds.core.audio.CustomSoundStore
import com.kitchenkds.core.update.AppUpdater
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.data.export.ExportUtil
import com.kitchenkds.data.parser.RawFrame
import com.kitchenkds.data.pipeline.OrderPipeline
import com.kitchenkds.data.prefs.AppPreferences
import com.kitchenkds.data.repository.OrderRepository
import com.kitchenkds.data.serial.SerialEvent
import com.kitchenkds.data.serial.UsbSerialHub
import com.kitchenkds.data.work.ResetScheduler
import com.kitchenkds.domain.model.AlertSounds
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.ThemeMode
import com.kitchenkds.domain.model.UiSettings
import com.kitchenkds.domain.usecase.OrderIngestUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KdsViewModel(
    application: Application,
    private val hub: UsbSerialHub,
    private val repository: OrderRepository,
    private val ingestUseCase: OrderIngestUseCase,
    private val preferences: AppPreferences,
    private val soundPlayer: AlertSoundPlayer
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "KdsVM"
    }

    private val pipeline = OrderPipeline()
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private val screenFlow = MutableStateFlow(Screen.Main)
    private val selectionModeFlow = MutableStateFlow(false)
    private val selectedIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    val settingsFlow: StateFlow<UiSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UiSettings()
    )

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    // ───────────────── V5.1 在线更新 ─────────────────

    data class UpdateUiState(
        val checking: Boolean = false,
        val info: AppUpdater.UpdateInfo? = null,
        val download: AppUpdater.DownloadState? = null,
        val dialogVisible: Boolean = false
    )

    private val updater by lazy { AppUpdater(getApplication()) }
    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()
    private var silentUpdateChecked = false

    /** 已下载好的安装包：用户去授权「安装未知应用」后可以再次拉起安装器，不必重新下载 */
    private var downloadedApk: java.io.File? = null

    val uiState: StateFlow<KdsUiState> = combine(
        hub.state,
        repository.activeOrders,
        repository.historyOrders,
        combine(nowFlow, settingsFlow) { now, settings -> now to settings },
        combine(screenFlow, selectionModeFlow, selectedIdsFlow) { screen, mode, ids ->
            Triple(screen, mode, ids)
        }
    ) { serial, active, history, nowSettings, nav ->
        KdsUiState(
            serial = serial,
            activeOrders = active,
            historyOrders = history,
            nowMillis = nowSettings.first,
            settings = nowSettings.second,
            selectionMode = nav.second,
            selectedIds = nav.third,
            screen = nav.first
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KdsUiState()
    )

    private var seqCounter = 0

    init {
        hub.start()

        viewModelScope.launch(Dispatchers.Default) {
            hub.events.collect { event ->
                val now = System.currentTimeMillis()
                when (event) {
                    is SerialEvent.Data -> pipeline.onData(event.bytes, now) { frame -> persist(frame) }
                    SerialEvent.Idle -> pipeline.onIdle(now) { frame -> persist(frame) }
                    is SerialEvent.Failure -> RingLog.w(TAG, "serial failure: " + event.message)
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1_000L)
                nowFlow.value = System.currentTimeMillis()
            }
        }

        // 启动时若开着自动重置，补排一次（WorkManager 重启后也会自行恢复）
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsFlow.value
            if (s.autoResetEnabled) {
                ResetScheduler.schedule(getApplication(), s.autoResetHour, s.autoResetMinute)
            }
        }
    }

    /**
     * 入库闸门：先过取消/重单合并，真正插入新卡时才响铃。
     * 取消单与"复活单"不会插入，自然也不会响 —— 避免后厨听到两声。
     */
    private fun persist(frame: RawFrame) {
        if (frame.lines.isEmpty()) return
        val seq = seqCounter++
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = ingestUseCase.ingest(frame, seq)
            if (outcome.inserted) {
                val s = settingsFlow.value
                if (s.soundEnabled) {
                    playAlert(s)
                }
            } else if (outcome.cancelledCount > 0 || outcome.revivedCount > 0) {
                RingLog.d(
                    TAG,
                    "merged: cancelled=" + outcome.cancelledCount + " revived=" + outcome.revivedCount
                )
            }
        }
    }

    // ───────────────── 导航 ─────────────────

    fun navigate(screen: Screen) {
        screenFlow.value = screen
        if (screen != Screen.History) exitSelection()
    }

    fun back() {
        val next = when (screenFlow.value) {
            Screen.Connection,
            Screen.SizeSettings,
            Screen.ThemeSettings,
            Screen.Export,
            Screen.SoundSettings,
            Screen.DataReset,
            Screen.ScheduleReset -> Screen.Settings

            Screen.Settings, Screen.History -> Screen.Main
            Screen.Main -> Screen.Main
        }
        screenFlow.value = next
        if (next != Screen.History) exitSelection()
    }

    // ───────────────── 单品 / 整单 ─────────────────

    fun onItemToggle(itemId: String, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repository.setItemDone(itemId, done) }
    }

    fun onStrikeThrough(orderId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.strikeThrough(orderId) }
    }

    fun onUndoStrikeThrough(orderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.undoStrikeThrough(orderId)
            _toast.tryEmit("已移回主界面")
        }
    }

    fun onDeleteOrder(orderId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteHistory(listOf(orderId)) }
    }

    // ───────────────── 历史选择 ─────────────────

    fun toggleSelectionMode() {
        val next = !selectionModeFlow.value
        selectionModeFlow.value = next
        if (!next) selectedIdsFlow.value = emptySet()
    }

    fun toggleSelect(orderId: String) {
        val current = selectedIdsFlow.value
        selectedIdsFlow.value =
            if (current.contains(orderId)) current - orderId else current + orderId
    }

    fun selectAll() {
        val all = uiState.value.historyOrders.map { it.id }.toSet()
        selectedIdsFlow.value = if (selectedIdsFlow.value.size == all.size) emptySet() else all
    }

    fun exitSelection() {
        selectionModeFlow.value = false
        selectedIdsFlow.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selectedIdsFlow.value.toList()
        if (ids.isEmpty()) {
            _toast.tryEmit("请先选择要删除的订单")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHistory(ids)
            selectedIdsFlow.value = emptySet()
            _toast.tryEmit("已删除 " + ids.size + " 条历史")
        }
    }

    // ───────────────── 导出 ─────────────────

    fun exportOrders(format: ExportUtil.Format) {
        viewModelScope.launch(Dispatchers.IO) {
            val orders: List<Order> = repository.snapshotAll()
            val result = ExportUtil.exportOrders(getApplication(), orders, format)
            _toast.tryEmit(result.message)
        }
    }

    fun exportRuntimeLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ExportUtil.exportLog(getApplication(), dumpLog())
            _toast.tryEmit(result.message)
        }
    }

    // ───────────────── UI 偏好 ─────────────────

    fun setCardWidth(value: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setCardWidth(value) }
    }

    fun setItemSpacing(value: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setItemSpacing(value) }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setFontScale(value) }
    }

    /** V4.3 卡片最大高度占屏比（0.30 ~ 1.00） */
    fun setCardHeightRatio(value: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setCardHeightRatio(value) }
    }

    /** V4.3 顶部蓝条最小高度（32 ~ 180dp） */
    fun setHeaderHeight(value: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setHeaderHeight(value) }
    }

    /** V4.3 顶部蓝条字号附加倍率（0.5 ~ 3.0x） */
    fun setHeaderScale(value: Float) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setHeaderScale(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setThemeMode(mode) }
    }

    fun resetUiSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.resetToDefault()
            _toast.tryEmit("已恢复默认尺寸与主题")
        }
    }

    // ───────────────── V4 音效 ─────────────────

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setSoundEnabled(enabled) }
    }

    /** 切换内置音效（"0".."4"）或 CUSTOM，并立刻试听一次 */
    fun setSoundMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setSoundMode(mode) }
        playAlert(settingsFlow.value.copy(soundMode = mode), timesOverride = 1)
    }

    /** 闹钟通道 / 媒体通道切换 */
    fun setSoundUseAlarmChannel(useAlarm: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setSoundUseAlarmChannel(useAlarm) }
    }

    fun setSoundTimes(times: Int) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setSoundTimes(times) }
    }

    fun setSoundInterval(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) { preferences.setSoundInterval(seconds) }
    }

    /** 试听：严格按当前「响声次数 + 响声间隔」实际播放，后厨现场调音量用 */
    fun previewSound() {
        playAlert(settingsFlow.value)
    }

    /**
     * 导入自定义提示音（SAF 返回的 content:// URI）。
     * 核心：立刻复制进私有目录 filesDir/custom_alert_sound，只存文件名做展示，
     * 绝不把 URI 本身当音源 —— 否则重启 / 换机 / 清缓存后就哑火。
     */
    fun importCustomSound(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val displayName = CustomSoundStore.import(getApplication(), uri)
                preferences.setCustomSoundName(displayName)
                preferences.setSoundMode(AlertSounds.MODE_CUSTOM)
                _toast.tryEmit("已导入提示音：" + displayName)
                delay(200L)
                // 导入成功自动试听一次，现场立刻能确认音量
                playAlert(
                    settingsFlow.value.copy(
                        soundMode = AlertSounds.MODE_CUSTOM,
                        customSoundName = displayName
                    )
                )
            } catch (t: Throwable) {
                RingLog.e(TAG, "import custom sound fail: " + t.message)
                _toast.tryEmit("导入失败：" + (t.message ?: "无法读取该文件"))
            }
        }
    }

    /** 删掉已导入的自定义音效，并切回第 1 个内置音 */
    fun clearCustomSound() {
        viewModelScope.launch(Dispatchers.IO) {
            CustomSoundStore.clear(getApplication())
            preferences.setCustomSoundName("")
            preferences.setSoundMode(AlertSounds.fallback().mode)
            _toast.tryEmit("已移除自定义提示音")
        }
    }

    /**
     * 统一的播放入口：把 UiSettings 翻译成播放规格。
     * @param timesOverride 试听时强制只响 1 声（切音效时的即时确认）
     */
    private fun playAlert(s: UiSettings, timesOverride: Int? = null) {
        soundPlayer.play(
            AlertSoundPlayer.Spec(
                mode = s.soundMode,
                times = timesOverride ?: s.soundTimes,
                intervalMs = s.soundIntervalSec * 1000L,
                useAlarmChannel = s.soundUseAlarmChannel
            )
        )
    }

    // ───────────────── V5.1 在线更新（GitHub Releases） ─────────────────

    /**
     * 手动检查更新：失败给 Toast，成功但没有新版本也给 Toast「已是最新」。
     */
    fun checkUpdateManually() {
        if (_updateState.value.checking) return
        _updateState.value = _updateState.value.copy(checking = true)
        viewModelScope.launch {
            try {
                val info = updater.checkUpdate()
                if (info == null) {
                    _updateState.value = _updateState.value.copy(checking = false)
                    _toast.tryEmit("当前已是最新版本 v" + BuildConfig.VERSION_NAME)
                } else {
                    _updateState.value = UpdateUiState(
                        checking = false,
                        info = info,
                        download = null,
                        dialogVisible = true
                    )
                }
            } catch (t: Throwable) {
                RingLog.w(TAG, "manual update check failed: " + t.message)
                _updateState.value = _updateState.value.copy(checking = false)
                _toast.tryEmit("检查更新失败：" + (t.message ?: "网络异常"))
            }
        }
    }

    /**
     * 启动静默检查：只做一次，任何失败都吞掉 —— 绝不能影响后厨接单。
     * 只有确实存在新版本时才让弹窗出现。
     */
    fun checkUpdateSilently() {
        if (silentUpdateChecked) return
        silentUpdateChecked = true
        viewModelScope.launch {
            try {
                val info = updater.checkUpdate() ?: return@launch
                _updateState.value = _updateState.value.copy(info = info, dialogVisible = true)
            } catch (t: Throwable) {
                RingLog.w(TAG, "silent update check skipped: " + t.message)
            }
        }
    }

    /** 下载更新包；完成后自动拉起系统安装器 */
    fun downloadUpdate() {
        val info = _updateState.value.info ?: return
        if (_updateState.value.download is AppUpdater.DownloadState.Downloading) return
        viewModelScope.launch {
            updater.downloadApk(info.downloadUrl).collect { state ->
                _updateState.value = _updateState.value.copy(download = state, dialogVisible = true)
                when (state) {
                    is AppUpdater.DownloadState.Success -> {
                        downloadedApk = state.apkFile
                        val launched = updater.installApk(state.apkFile)
                        if (!launched) {
                            _toast.tryEmit("请允许「安装未知应用」，再点「打开安装器」")
                        }
                    }
                    is AppUpdater.DownloadState.Error -> {
                        _toast.tryEmit("下载失败：" + state.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    /** 已下载完成但被系统拦下（未授权安装）时，用户授权后点这里直接拉起安装器 */
    fun installDownloadedApk() {
        val file = downloadedApk
        if (file == null || !file.exists()) {
            _toast.tryEmit("安装包不存在，请重新下载")
            return
        }
        if (!updater.installApk(file)) {
            _toast.tryEmit("请在系统设置里允许「安装未知应用」")
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = _updateState.value.copy(dialogVisible = false, download = null)
    }

    // ───────────────── V4 数据重置 ─────────────────

    fun resetAllOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEverything()
            _toast.tryEmit("所有订单已清空")
        }
    }

    fun setAutoResetEnabled(enabled: Boolean) {
        val s = settingsFlow.value
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setAutoResetEnabled(enabled)
            if (enabled) {
                ResetScheduler.schedule(getApplication(), s.autoResetHour, s.autoResetMinute)
                _toast.tryEmit("已开启每日 " + pad(s.autoResetHour) + ":" + pad(s.autoResetMinute) + " 自动清理")
            } else {
                ResetScheduler.cancel(getApplication())
                _toast.tryEmit("已关闭定时清理")
            }
        }
    }

    fun setAutoResetTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.setAutoResetTime(hour, minute)
            if (settingsFlow.value.autoResetEnabled) {
                ResetScheduler.schedule(getApplication(), hour, minute)
            }
            _toast.tryEmit("清理时间已设为 " + pad(hour) + ":" + pad(minute))
        }
    }

    // ───────────────── 连接 ─────────────────

    fun onRetryConnection() {
        hub.retry()
        _toast.tryEmit("正在重新连接串口…")
    }

    fun dumpLog(): String = RingLog.dump()

    fun clearLog() = RingLog.clear()

    private fun pad(v: Int): String = if (v < 10) "0" + v else v.toString()

    override fun onCleared() {
        super.onCleared()
        hub.stop()
        soundPlayer.release()
    }
}

class KdsViewModelFactory(
    private val application: Application,
    private val hub: UsbSerialHub,
    private val repository: OrderRepository,
    private val ingestUseCase: OrderIngestUseCase,
    private val preferences: AppPreferences,
    private val soundPlayer: AlertSoundPlayer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return KdsViewModel(application, hub, repository, ingestUseCase, preferences, soundPlayer) as T
    }
}

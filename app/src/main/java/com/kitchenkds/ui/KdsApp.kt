package com.kitchenkds.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenkds.ui.history.HistoryScreen
import com.kitchenkds.ui.main.MainScreen
import com.kitchenkds.ui.settings.ConnectionScreen
import com.kitchenkds.ui.settings.DataResetScreen
import com.kitchenkds.ui.settings.ExportScreen
import com.kitchenkds.ui.settings.ScheduleResetScreen
import com.kitchenkds.ui.settings.SettingsScreen
import com.kitchenkds.ui.settings.SizeSettingsScreen
import com.kitchenkds.ui.settings.SoundSettingsScreen
import com.kitchenkds.ui.settings.ThemeSettingsScreen
import com.kitchenkds.ui.settings.UpdateDialog
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsViewModel
import com.kitchenkds.ui.viewmodel.Screen

/**
 * 根容器：单 Activity + 手写状态导航。
 * 主界面 / 历史 / 设置 / 连接状态 / 尺寸调整 / 主题设置 / 数据导出 / 音效设置 / 数据重置 / 定时重置
 */
@Composable
fun KdsApp(vm: KdsViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = state.screen != Screen.Main) {
        vm.back()
    }

    Surface(color = KdsTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            Screen.Main -> MainScreen(
                state = state,
                onOpenHistory = { vm.navigate(Screen.History) },
                onOpenSettings = { vm.navigate(Screen.Settings) },
                onItemToggle = vm::onItemToggle,
                onStrikeThrough = vm::onStrikeThrough,
                onLaunchSilentUpdateCheck = { vm.checkUpdateSilently() }
            )

            Screen.History -> HistoryScreen(
                state = state,
                onBack = { vm.navigate(Screen.Main) },
                onToggleSelectionMode = vm::toggleSelectionMode,
                onSelectAll = vm::selectAll,
                onDeleteSelected = vm::deleteSelected,
                onToggleSelect = vm::toggleSelect,
                onUndoStrike = vm::onUndoStrikeThrough
            )

            Screen.Settings -> SettingsScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Main) },
                onOpenConnection = { vm.navigate(Screen.Connection) },
                onOpenSize = { vm.navigate(Screen.SizeSettings) },
                onOpenTheme = { vm.navigate(Screen.ThemeSettings) },
                onOpenExport = { vm.navigate(Screen.Export) },
                onOpenSound = { vm.navigate(Screen.SoundSettings) },
                onOpenDataReset = { vm.navigate(Screen.DataReset) }
            )

            Screen.Connection -> ConnectionScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Settings) }
            )

            Screen.SizeSettings -> SizeSettingsScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Settings) }
            )

            Screen.ThemeSettings -> ThemeSettingsScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Settings) }
            )

            Screen.Export -> ExportScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Settings) }
            )

            Screen.SoundSettings -> SoundSettingsScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Settings) }
            )

            Screen.DataReset -> DataResetScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.Settings) },
                onOpenSchedule = { vm.navigate(Screen.ScheduleReset) }
            )

            Screen.ScheduleReset -> ScheduleResetScreen(
                state = state,
                vm = vm,
                onBack = { vm.navigate(Screen.DataReset) }
            )
        }

        // V5.1 全局更新弹窗：静默检查与手动检查共用
        val info = update.info
        if (update.dialogVisible && info != null) {
            UpdateDialog(
                info = info,
                download = update.download,
                onDismiss = { vm.dismissUpdateDialog() },
                onStartDownload = { vm.downloadUpdate() },
                onInstall = { vm.installDownloadedApk() }
            )
        }
    }
}

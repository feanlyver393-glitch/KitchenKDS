package com.kitchenkds

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitchenkds.ui.KdsApp
import com.kitchenkds.ui.theme.KitchenKdsTheme
import com.kitchenkds.ui.viewmodel.KdsViewModel
import com.kitchenkds.ui.viewmodel.KdsViewModelFactory

/**
 * 单一 Activity。
 *
 * V4.3：LG V50 桌面模式（Screen+ / 桌面窗口）下，系统栏与桌面模式标题栏会反复弹回，
 * 因此这里做"严格沉浸式全屏"三连：
 *   ① onCreate 里 setDecorFitsSystemWindows(false) + 第一次进入沉浸式；
 *   ② onResume 重新进入（从后台/弹窗回来时系统会恢复系统栏）；
 *   ③ onWindowFocusChanged(true) 再进入（点击弹窗、切回窗口、桌面模式重新聚焦）。
 *
 * WindowInsetsControllerCompat 负责 Android 11+，legacy systemUiVisibility 覆盖 Android 10
 * （LG V50 常见系统版本），两套同时设置，互不冲突。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 后厨常驻显示：屏幕常亮 + 强制横屏 + 边到边布局
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveFullscreen()

        val app = application as KitchenKdsApp

        setContent {
            val vm: KdsViewModel = viewModel(
                factory = KdsViewModelFactory(
                    application = app,
                    hub = app.serialHub,
                    repository = app.orderRepository,
                    ingestUseCase = app.ingestUseCase,
                    preferences = app.preferences,
                    soundPlayer = app.soundPlayer
                )
            )
            val state by vm.uiState.collectAsStateWithLifecycle()

            KitchenKdsTheme(themeMode = state.settings.themeMode) {
                KdsApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台、弹窗、桌面模式切回来后系统会把栏重新显示出来，这里再压一次
        enterImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveFullscreen()
        }
    }

    /**
     * 严格沉浸式全屏：隐藏状态栏 / 导航栏（含 LG 桌面模式的顶部标题栏），
     * 允许用户从边缘划出临时显示，并保持屏幕常亮。
     */
    @Suppress("DEPRECATION")
    private fun enterImmersiveFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ① Android 11+ 官方通道
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // ② Android 10 及以下（LG V50 常见版本）legacy 通道
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }
}

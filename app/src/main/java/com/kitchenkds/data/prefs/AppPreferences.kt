package com.kitchenkds.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.domain.model.AlertSounds
import com.kitchenkds.domain.model.ThemeMode
import com.kitchenkds.domain.model.UiSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** DataStore 实例必须挂在 Context 顶层，全局唯一 */
private val Context.kdsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kds_prefs")

/**
 * 全局偏好仓库。
 *
 * V3：尺寸三项 + 主题
 * V4：音效开关/音色/次数/间隔 + 定时重置开关/时间
 */
class AppPreferences(private val context: Context) {

    private companion object {
        const val TAG = "Prefs"

        val KEY_CARD_WIDTH = floatPreferencesKey("ui_card_width")
        val KEY_ITEM_SPACING = floatPreferencesKey("ui_item_spacing")
        val KEY_FONT_SCALE = floatPreferencesKey("ui_font_scale")
        val KEY_THEME_MODE = stringPreferencesKey("ui_theme_mode")
        // V4.3 卡片高度占比 / 蓝条高度 / 蓝条字号
        val KEY_CARD_HEIGHT_RATIO = floatPreferencesKey("ui_card_height_ratio")
        val KEY_HEADER_HEIGHT = floatPreferencesKey("ui_header_height")
        val KEY_HEADER_SCALE = floatPreferencesKey("ui_header_scale")

        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        /** V5：内置音效 "0".."4" 或 "CUSTOM" */
        val KEY_SOUND_MODE = stringPreferencesKey("sound_mode")
        /** V5：自定义音效原始文件名（仅展示） */
        val KEY_CUSTOM_SOUND_NAME = stringPreferencesKey("custom_sound_name")
        /** V5：true = 闹钟通道（静音也响） */
        val KEY_SOUND_ALARM_CHANNEL = booleanPreferencesKey("sound_alarm_channel")
        /** V4 遗留：只用于老配置的迁移读取 */
        val KEY_SOUND_ID = intPreferencesKey("sound_id")
        val KEY_SOUND_TIMES = intPreferencesKey("sound_times")
        val KEY_SOUND_INTERVAL = intPreferencesKey("sound_interval_sec")

        val KEY_AUTO_RESET_ENABLED = booleanPreferencesKey("auto_reset_enabled")
        val KEY_AUTO_RESET_HOUR = intPreferencesKey("auto_reset_hour")
        val KEY_AUTO_RESET_MINUTE = intPreferencesKey("auto_reset_minute")
    }

    val settings: Flow<UiSettings> = context.kdsDataStore.data
        .catch { e ->
            RingLog.e(TAG, "read fail: " + e.message)
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            UiSettings(
                cardWidth = p[KEY_CARD_WIDTH] ?: 260f,
                itemSpacing = p[KEY_ITEM_SPACING] ?: 4f,
                fontScale = p[KEY_FONT_SCALE] ?: 1.0f,
                cardHeightRatio = p[KEY_CARD_HEIGHT_RATIO] ?: 0.85f,
                headerHeight = p[KEY_HEADER_HEIGHT] ?: 44f,
                headerScale = p[KEY_HEADER_SCALE] ?: 1.0f,
                themeMode = ThemeMode.fromName(p[KEY_THEME_MODE]),
                soundEnabled = p[KEY_SOUND_ENABLED] ?: true,
                // 老版本只有 sound_id(0..2)，没有 sound_mode：缺省时用它迁移
                soundMode = p[KEY_SOUND_MODE]
                    ?: ((p[KEY_SOUND_ID] ?: 0).coerceIn(0, 4).toString()),
                customSoundName = p[KEY_CUSTOM_SOUND_NAME] ?: "",
                soundUseAlarmChannel = p[KEY_SOUND_ALARM_CHANNEL] ?: true,
                soundTimes = p[KEY_SOUND_TIMES] ?: 2,
                soundIntervalSec = p[KEY_SOUND_INTERVAL] ?: 1,
                autoResetEnabled = p[KEY_AUTO_RESET_ENABLED] ?: false,
                autoResetHour = p[KEY_AUTO_RESET_HOUR] ?: 4,
                autoResetMinute = p[KEY_AUTO_RESET_MINUTE] ?: 0
            ).sanitized()
        }

    suspend fun setCardWidth(value: Float) = edit {
        it[KEY_CARD_WIDTH] = value.coerceIn(UiSettings.CARD_WIDTH_MIN, UiSettings.CARD_WIDTH_MAX)
    }

    suspend fun setItemSpacing(value: Float) = edit {
        it[KEY_ITEM_SPACING] = value.coerceIn(UiSettings.ITEM_SPACING_MIN, UiSettings.ITEM_SPACING_MAX)
    }

    suspend fun setFontScale(value: Float) = edit {
        it[KEY_FONT_SCALE] = value.coerceIn(UiSettings.FONT_SCALE_MIN, UiSettings.FONT_SCALE_MAX)
    }

    /** 卡片最大高度占屏比 0.30 ~ 1.00 */
    suspend fun setCardHeightRatio(value: Float) = edit {
        it[KEY_CARD_HEIGHT_RATIO] = value.coerceIn(
            UiSettings.CARD_HEIGHT_RATIO_MIN,
            UiSettings.CARD_HEIGHT_RATIO_MAX
        )
    }

    /** 蓝条最小高度 32 ~ 180dp */
    suspend fun setHeaderHeight(value: Float) = edit {
        it[KEY_HEADER_HEIGHT] = value.coerceIn(
            UiSettings.HEADER_HEIGHT_MIN,
            UiSettings.HEADER_HEIGHT_MAX
        )
    }

    /** 蓝条字号附加倍率 0.5 ~ 3.0 */
    suspend fun setHeaderScale(value: Float) = edit {
        it[KEY_HEADER_SCALE] = value.coerceIn(
            UiSettings.HEADER_SCALE_MIN,
            UiSettings.HEADER_SCALE_MAX
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit {
        it[KEY_THEME_MODE] = mode.name
    }

    suspend fun setSoundEnabled(enabled: Boolean) = edit {
        it[KEY_SOUND_ENABLED] = enabled
    }

    /** V5：切换内置音效 / CUSTOM */
    suspend fun setSoundMode(mode: String) = edit {
        val safe = if (AlertSounds.isValidMode(mode)) mode else AlertSounds.fallback().mode
        it[KEY_SOUND_MODE] = safe
    }

    /** V5：自定义音效展示名 */
    suspend fun setCustomSoundName(name: String) = edit {
        it[KEY_CUSTOM_SOUND_NAME] = name
    }

    /** V5：闹钟通道开关 */
    suspend fun setSoundUseAlarmChannel(useAlarm: Boolean) = edit {
        it[KEY_SOUND_ALARM_CHANNEL] = useAlarm
    }

    suspend fun setSoundTimes(times: Int) = edit {
        it[KEY_SOUND_TIMES] = times.coerceIn(UiSettings.SOUND_TIMES_MIN, UiSettings.SOUND_TIMES_MAX)
    }

    suspend fun setSoundInterval(seconds: Int) = edit {
        it[KEY_SOUND_INTERVAL] =
            seconds.coerceIn(UiSettings.SOUND_INTERVAL_MIN, UiSettings.SOUND_INTERVAL_MAX)
    }

    suspend fun setAutoReset(enabled: Boolean, hour: Int, minute: Int) = edit {
        it[KEY_AUTO_RESET_ENABLED] = enabled
        it[KEY_AUTO_RESET_HOUR] = hour.coerceIn(0, 23)
        it[KEY_AUTO_RESET_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setAutoResetEnabled(enabled: Boolean) = edit {
        it[KEY_AUTO_RESET_ENABLED] = enabled
    }

    suspend fun setAutoResetTime(hour: Int, minute: Int) = edit {
        it[KEY_AUTO_RESET_HOUR] = hour.coerceIn(0, 23)
        it[KEY_AUTO_RESET_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun resetToDefault() = edit {
        it[KEY_CARD_WIDTH] = 260f
        it[KEY_ITEM_SPACING] = 4f
        it[KEY_FONT_SCALE] = 1.0f
        it[KEY_CARD_HEIGHT_RATIO] = 0.85f
        it[KEY_HEADER_HEIGHT] = 44f
        it[KEY_HEADER_SCALE] = 1.0f
        it[KEY_THEME_MODE] = ThemeMode.DARK.name
        it[KEY_SOUND_MODE] = AlertSounds.fallback().mode
        it[KEY_CUSTOM_SOUND_NAME] = ""
        it[KEY_SOUND_ALARM_CHANNEL] = true
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.kdsDataStore.edit { prefs -> block(prefs) }
        } catch (t: Throwable) {
            RingLog.e(TAG, "write fail: " + t.message)
        }
    }
}

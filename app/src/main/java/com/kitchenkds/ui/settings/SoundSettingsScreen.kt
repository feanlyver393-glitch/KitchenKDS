package com.kitchenkds.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.core.audio.CustomSoundStore
import com.kitchenkds.domain.model.AlertSounds
import com.kitchenkds.domain.model.UiSettings
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel

/**
 * 三级菜单：音效设置（V5 重构）。
 *
 * 这一页要解决现场两件事：
 *   ① 换手机后没声 —— 音源换成 res/raw 的真实音频 + MediaPlayer + 闹钟通道 + ToneGenerator 兜底；
 *   ② 想用自家店里的提示音 —— SAF 选文件，复制进私有目录，永久有效。
 *
 * 结构：总开关 -> 通道开关 -> 内置/自定义音效列表 -> 导入按钮 -> 次数/间隔 -> 试听。
 */
@Composable
fun SoundSettingsScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit
) {
    val c = KdsTheme.colors
    val s = state.settings
    val context = LocalContext.current

    // SAF：让用户从手机里挑任意音频文件（MP3 / WAV / OGG / M4A）
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.importCustomSound(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BackTopBar(title = "音效设置", onBack = onBack) {
            TextButton(onClick = { vm.previewSound() }) {
                Text(text = "试听", color = c.accent, fontSize = 13.sp)
            }
        }
        KdsDivider()

        // ── 总开关 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "来单提示音", color = c.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "新订单入库时播放（取消单与恢复单不响）",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = s.soundEnabled,
                onCheckedChange = { vm.setSoundEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
            )
        }

        // ── 通道开关：静音/震动机器也能响 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "使用闹钟通道", color = c.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (s.soundUseAlarmChannel) {
                        "USAGE_ALARM：备用机静音 / 震动时也能响（推荐）"
                    } else {
                        "USAGE_MEDIA：跟随媒体音量，静音时不响"
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = s.soundUseAlarmChannel,
                onCheckedChange = { vm.setSoundUseAlarmChannel(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
            )
        }
        KdsDivider()

        // ── 内置音效 ──
        GroupLabel("内置提示音")
        for (item in AlertSounds.builtins) {
            SoundOption(
                title = item.label,
                note = item.note,
                selected = s.soundMode == item.mode,
                badge = null,
                onClick = { vm.setSoundMode(item.mode) }
            )
        }

        // ── 自定义音效 ──
        GroupLabel("自定义提示音")
        SoundOption(
            title = if (s.customSoundName.isBlank()) "未选择" else s.customSoundName,
            note = if (s.customSoundName.isBlank()) {
                "点下面的按钮，从手机里选一段音频"
            } else {
                "已保存到应用私有目录 · 约 " + CustomSoundStore.sizeKb(context) + " KB"
            },
            selected = AlertSounds.isCustom(s.soundMode),
            badge = if (s.customSoundName.isBlank()) null else "清除",
            onClick = {
                if (s.customSoundName.isNotBlank()) vm.setSoundMode(AlertSounds.MODE_CUSTOM)
            },
            onBadgeClick = { vm.clearCustomSound() }
        )

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { pickAudio.launch(arrayOf("audio/*")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent)
        ) {
            Text(text = "从手机选择音频文件", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 MP3 / WAV / OGG / M4A。导入后会自动复制到应用私有目录并立即试听，" +
                "重启手机、换目录都不会失效。",
            color = c.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // ── 次数 / 间隔 ──
        GroupLabel("响声次数")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            StepperRow(
                label = "每次来单响 " + s.soundTimes + " 声",
                valueText = s.soundTimes.toString() + " 次",
                minusEnabled = s.soundTimes > UiSettings.SOUND_TIMES_MIN,
                plusEnabled = s.soundTimes < UiSettings.SOUND_TIMES_MAX,
                onMinus = { vm.setSoundTimes(s.soundTimes - 1) },
                onPlus = { vm.setSoundTimes(s.soundTimes + 1) }
            )
        }

        GroupLabel("响声间隔")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            StepperRow(
                label = "两声之间停顿 " + s.soundIntervalSec + " 秒",
                valueText = s.soundIntervalSec.toString() + " s",
                minusEnabled = s.soundIntervalSec > UiSettings.SOUND_INTERVAL_MIN,
                plusEnabled = s.soundIntervalSec < UiSettings.SOUND_INTERVAL_MAX,
                onMinus = { vm.setSoundInterval(s.soundIntervalSec - 1) },
                onPlus = { vm.setSoundInterval(s.soundIntervalSec + 1) }
            )
        }

        // ── 试听 ──
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { vm.previewSound() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "试听当前提示音（" + s.soundTimes + " 声 / 间隔 " + s.soundIntervalSec + " 秒）",
                fontSize = 14.sp,
                color = c.textPrimary
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "当前音源：" + AlertSounds.labelOf(s.soundMode, s.customSoundName) +
                " · " + (if (s.soundUseAlarmChannel) "闹钟通道" else "媒体通道") +
                "。若仍听不到声音：先确认音源有效，其次检查系统「闹钟音量」是否为 0。",
            color = c.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SoundOption(
    title: String,
    note: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    onBadgeClick: (() -> Unit)? = null
) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = c.accent,
                unselectedColor = c.textSecondary
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = note,
                color = c.textSecondary,
                fontSize = 11.sp,
                maxLines = 2
            )
        }
        if (badge != null && onBadgeClick != null) {
            Text(
                text = badge,
                color = c.danger,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.surfaceHigh)
                    .clickable { onBadgeClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        color = KdsTheme.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

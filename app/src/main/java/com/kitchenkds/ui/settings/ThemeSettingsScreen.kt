package com.kitchenkds.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.domain.model.ThemeMode
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel

/** 二级菜单：主题设置（单选） */
@Composable
fun ThemeSettingsScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit
) {
    val c = KdsTheme.colors
    val current = state.settings.themeMode

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "主题设置", onBack = onBack)
        KdsDivider()

        ThemeMode.entries.forEach { mode ->
            ThemeOption(
                mode = mode,
                selected = mode == current,
                onClick = { vm.setThemeMode(mode) }
            )
            KdsDivider()
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "「跟随系统」会读取手机的深色模式开关；\n" +
                "两条固定选项会覆盖系统设置，适合后厨固定环境。",
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
        Text(
            text = mode.label,
            color = c.textPrimary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        ThemeChip(mode)
    }
}

/** 右侧小色板，直观显示该模式的样子 */
@Composable
private fun ThemeChip(mode: ThemeMode) {
    val bg = when (mode) {
        ThemeMode.DARK -> androidx.compose.ui.graphics.Color(0xFF15181B)
        ThemeMode.LIGHT -> androidx.compose.ui.graphics.Color(0xFFF4F6F8)
        ThemeMode.SYSTEM -> androidx.compose.ui.graphics.Color(0xFF6E7B85)
    }
    val fg = if (mode == ThemeMode.LIGHT) {
        androidx.compose.ui.graphics.Color(0xFF15181B)
    } else {
        androidx.compose.ui.graphics.Color(0xFFECEFF1)
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "A", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

package com.kitchenkds.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.ui.theme.KdsTheme

@Composable
fun KdsDivider(modifier: Modifier = Modifier, color: Color = KdsTheme.colors.divider) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

/** 二级页面统一顶栏：返回箭头 + 标题 + 右侧动作 */
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = c.textPrimary
            )
        }
        Text(
            text = title,
            color = c.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** 连接状态指示灯：黄点=未连接，绿点=已连接 */
@Composable
fun ConnectionDot(connected: Boolean, modifier: Modifier = Modifier, size: Int = 11) {
    val c = KdsTheme.colors
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (connected) c.success else c.warning)
    )
}

@Composable
fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun EmptyHint(text: String) {
    val c = KdsTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().padding(PaddingValues(top = 120.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = c.textSecondary, fontSize = 20.sp, letterSpacing = 3.sp)
    }
}

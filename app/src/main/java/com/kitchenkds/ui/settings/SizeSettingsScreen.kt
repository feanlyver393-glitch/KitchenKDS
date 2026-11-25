package com.kitchenkds.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.domain.model.ItemCategory
import com.kitchenkds.domain.model.Order
import com.kitchenkds.domain.model.OrderItem
import com.kitchenkds.domain.model.OrderKind
import com.kitchenkds.domain.model.UiSettings
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.main.OrderCard
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 三级菜单：尺寸调整（V4.3）。
 *
 * 布局：左侧 0.8 倍缩放的 Dummy 卡片预览，右侧 +/- 数值调节组。
 * 全部用 Stepper —— Slider 在横屏 411dp 高度里必重叠，Stepper 不会。
 *
 * V4.3 六项参数（每一项都可在 14 寸桌面模式大屏下放大到实用尺度）：
 *   卡片宽度        180 ~ 900dp，步进 20dp
 *   卡片最大高度    屏高 30% ~ 100%，步进 5%（1.00 = 整屏自动撑开）
 *   字体缩放        0.5 ~ 4.5x，步进 0.1x（蓝条桌号/时间同步联动）
 *   行间距          0 ~ 48dp，步进 2dp
 *   蓝条高度        32 ~ 180dp，步进 4dp
 *   蓝条字号        0.5 ~ 3.0x，步进 0.1x
 *
 * 所有改动即时写 DataStore，主界面下一帧就跟着变。
 */
@Composable
fun SizeSettingsScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit
) {
    val c = KdsTheme.colors
    val settings = state.settings

    var localWidth by remember { mutableFloatStateOf(settings.cardWidth) }
    var localSpacing by remember { mutableFloatStateOf(settings.itemSpacing) }
    var localFont by remember { mutableFloatStateOf(settings.fontScale) }
    var localRatio by remember { mutableFloatStateOf(settings.cardHeightRatio) }
    var localBarHeight by remember { mutableFloatStateOf(settings.headerHeight) }
    var localBarScale by remember { mutableFloatStateOf(settings.headerScale) }

    LaunchedEffect(settings.cardWidth) { localWidth = settings.cardWidth }
    LaunchedEffect(settings.itemSpacing) { localSpacing = settings.itemSpacing }
    LaunchedEffect(settings.fontScale) { localFont = settings.fontScale }
    LaunchedEffect(settings.cardHeightRatio) { localRatio = settings.cardHeightRatio }
    LaunchedEffect(settings.headerHeight) { localBarHeight = settings.headerHeight }
    LaunchedEffect(settings.headerScale) { localBarScale = settings.headerScale }

    val previewSettings = UiSettings(
        cardWidth = localWidth,
        itemSpacing = localSpacing,
        fontScale = localFont,
        cardHeightRatio = localRatio,
        headerHeight = localBarHeight,
        headerScale = localBarScale,
        themeMode = settings.themeMode
    )
    val previewOrder = remember { buildPreviewOrder() }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "尺寸调整", onBack = onBack) {
            TextButton(onClick = { vm.resetUiSettings() }) {
                Text(text = "恢复默认", color = c.accent, fontSize = 13.sp)
            }
        }
        KdsDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 左：缩放预览 ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                OrderCard(
                    order = previewOrder,
                    nowMillis = previewOrder.receivedAtMillis + 3 * 60 * 1000L,
                    settings = previewSettings,
                    onItemToggle = { _, _ -> },
                    onStrikeThrough = { },
                    modifier = Modifier
                        .width(localWidth.dp)
                        .scale(0.8f)
                )
            }

            // ── 右：数值调节 ──
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ① 卡片宽度
                StepperRow(
                    label = "卡片宽度",
                    valueText = String.format(Locale.US, "%.0f dp", localWidth),
                    onMinus = {
                        localWidth = stepTo(
                            localWidth, -UiSettings.CARD_WIDTH_STEP,
                            UiSettings.CARD_WIDTH_MIN, UiSettings.CARD_WIDTH_MAX
                        )
                        vm.setCardWidth(localWidth)
                    },
                    onPlus = {
                        localWidth = stepTo(
                            localWidth, UiSettings.CARD_WIDTH_STEP,
                            UiSettings.CARD_WIDTH_MIN, UiSettings.CARD_WIDTH_MAX
                        )
                        vm.setCardWidth(localWidth)
                    },
                    minusEnabled = localWidth > UiSettings.CARD_WIDTH_MIN,
                    plusEnabled = localWidth < UiSettings.CARD_WIDTH_MAX
                )

                // ② 卡片最大高度（占屏高比例）
                StepperRow(
                    label = "卡片最大高度（占屏高）",
                    valueText = String.format(Locale.US, "%.0f %%", localRatio * 100f),
                    onMinus = {
                        localRatio = stepTo(
                            localRatio, -UiSettings.CARD_HEIGHT_RATIO_STEP,
                            UiSettings.CARD_HEIGHT_RATIO_MIN, UiSettings.CARD_HEIGHT_RATIO_MAX
                        )
                        vm.setCardHeightRatio(localRatio)
                    },
                    onPlus = {
                        localRatio = stepTo(
                            localRatio, UiSettings.CARD_HEIGHT_RATIO_STEP,
                            UiSettings.CARD_HEIGHT_RATIO_MIN, UiSettings.CARD_HEIGHT_RATIO_MAX
                        )
                        vm.setCardHeightRatio(localRatio)
                    },
                    minusEnabled = localRatio > UiSettings.CARD_HEIGHT_RATIO_MIN,
                    plusEnabled = localRatio < UiSettings.CARD_HEIGHT_RATIO_MAX
                )

                // ③ 字体缩放（菜品名 / 数量 / 蓝条同步）
                StepperRow(
                    label = "字体缩放（含蓝条）",
                    valueText = String.format(Locale.US, "%.1f x", localFont),
                    onMinus = {
                        localFont = stepTo(
                            localFont, -UiSettings.FONT_SCALE_STEP,
                            UiSettings.FONT_SCALE_MIN, UiSettings.FONT_SCALE_MAX
                        )
                        vm.setFontScale(localFont)
                    },
                    onPlus = {
                        localFont = stepTo(
                            localFont, UiSettings.FONT_SCALE_STEP,
                            UiSettings.FONT_SCALE_MIN, UiSettings.FONT_SCALE_MAX
                        )
                        vm.setFontScale(localFont)
                    },
                    minusEnabled = localFont > UiSettings.FONT_SCALE_MIN,
                    plusEnabled = localFont < UiSettings.FONT_SCALE_MAX
                )

                // ④ 行间距
                StepperRow(
                    label = "菜品行间距",
                    valueText = String.format(Locale.US, "%.0f dp", localSpacing),
                    onMinus = {
                        localSpacing = stepTo(
                            localSpacing, -UiSettings.ITEM_SPACING_STEP,
                            UiSettings.ITEM_SPACING_MIN, UiSettings.ITEM_SPACING_MAX
                        )
                        vm.setItemSpacing(localSpacing)
                    },
                    onPlus = {
                        localSpacing = stepTo(
                            localSpacing, UiSettings.ITEM_SPACING_STEP,
                            UiSettings.ITEM_SPACING_MIN, UiSettings.ITEM_SPACING_MAX
                        )
                        vm.setItemSpacing(localSpacing)
                    },
                    minusEnabled = localSpacing > UiSettings.ITEM_SPACING_MIN,
                    plusEnabled = localSpacing < UiSettings.ITEM_SPACING_MAX
                )

                // ⑤ 蓝条高度
                StepperRow(
                    label = "顶部蓝条高度",
                    valueText = String.format(Locale.US, "%.0f dp", localBarHeight),
                    onMinus = {
                        localBarHeight = stepTo(
                            localBarHeight, -UiSettings.HEADER_HEIGHT_STEP,
                            UiSettings.HEADER_HEIGHT_MIN, UiSettings.HEADER_HEIGHT_MAX
                        )
                        vm.setHeaderHeight(localBarHeight)
                    },
                    onPlus = {
                        localBarHeight = stepTo(
                            localBarHeight, UiSettings.HEADER_HEIGHT_STEP,
                            UiSettings.HEADER_HEIGHT_MIN, UiSettings.HEADER_HEIGHT_MAX
                        )
                        vm.setHeaderHeight(localBarHeight)
                    },
                    minusEnabled = localBarHeight > UiSettings.HEADER_HEIGHT_MIN,
                    plusEnabled = localBarHeight < UiSettings.HEADER_HEIGHT_MAX
                )

                // ⑥ 蓝条字号附加倍率
                StepperRow(
                    label = "顶部蓝条字号",
                    valueText = String.format(Locale.US, "%.1f x", localBarScale),
                    onMinus = {
                        localBarScale = stepTo(
                            localBarScale, -UiSettings.HEADER_SCALE_STEP,
                            UiSettings.HEADER_SCALE_MIN, UiSettings.HEADER_SCALE_MAX
                        )
                        vm.setHeaderScale(localBarScale)
                    },
                    onPlus = {
                        localBarScale = stepTo(
                            localBarScale, UiSettings.HEADER_SCALE_STEP,
                            UiSettings.HEADER_SCALE_MIN, UiSettings.HEADER_SCALE_MAX
                        )
                        vm.setHeaderScale(localBarScale)
                    },
                    minusEnabled = localBarScale > UiSettings.HEADER_SCALE_MIN,
                    plusEnabled = localBarScale < UiSettings.HEADER_SCALE_MAX
                )

                Text(
                    text = "宽度决定一屏几列；最大高度按屏高百分比，100% = 整屏自动撑开；" +
                        "字号倍率同时作用于菜品名、数量与蓝条桌号/时间。",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** 按步长加减并钉在区间内，顺便抹掉浮点累加误差 */
private fun stepTo(current: Float, delta: Float, min: Float, max: Float): Float {
    val next = (current + delta).coerceIn(min, max)
    return (next * 100f).roundToInt() / 100f
}

/** +/- 数值调节器（替代 Slider，横屏不会重叠） */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    plusEnabled: Boolean = true,
    minusEnabled: Boolean = true
) {
    val c = KdsTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(text = "−", enabled = minusEnabled, onClick = onMinus)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueText,
                    color = c.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            StepButton(text = "+", enabled = plusEnabled, onClick = onPlus)
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KdsTheme.colors
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (enabled) c.accent else c.surfaceHigh)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) androidx.compose.ui.graphics.Color.White else c.textSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** 预览用 Dummy 订单 */
private fun buildPreviewOrder(): Order {
    val now = System.currentTimeMillis()
    return Order(
        id = "preview-order",
        tableNo = "16",
        kind = OrderKind.NEW,
        receivedAtMillis = now - 3L * 60L * 1000L,
        seq = 0,
        foodItems = listOf(
            OrderItem(
                koreanName = "마라쇼롱샤",
                chineseName = "麻辣小龙虾",
                category = ItemCategory.FOOD,
                quantity = 2,
                sortIndex = 0
            ),
            OrderItem(
                koreanName = "꿔바로우",
                chineseName = "锅包肉",
                category = ItemCategory.FOOD,
                quantity = 1,
                sortIndex = 1
            )
        ),
        beverageItems = listOf(
            OrderItem(
                koreanName = "참이슬",
                chineseName = "真露烧酒",
                category = ItemCategory.BEVERAGE,
                quantity = 3,
                sortIndex = 2
            )
        )
    )
}

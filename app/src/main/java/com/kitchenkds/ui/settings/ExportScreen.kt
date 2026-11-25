package com.kitchenkds.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.data.export.ExportUtil
import com.kitchenkds.ui.common.BackTopBar
import com.kitchenkds.ui.common.KdsDivider
import com.kitchenkds.ui.theme.KdsTheme
import com.kitchenkds.ui.viewmodel.KdsUiState
import com.kitchenkds.ui.viewmodel.KdsViewModel

/** 三级菜单：数据导出（V4 从设置首页收纳进来） */
@Composable
fun ExportScreen(
    state: KdsUiState,
    vm: KdsViewModel,
    onBack: () -> Unit
) {
    val c = KdsTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BackTopBar(title = "数据导出", onBack = onBack)
        KdsDivider()

        Text(
            text = "共 " + state.historyCount + " 条历史订单，" + state.activeCount + " 条进行中",
            color = c.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
        )

        ExportRow(
            title = "导出全部订单（TXT）",
            subtitle = "人读格式，含韩中对照、数量、出餐状态",
            onClick = { vm.exportOrders(ExportUtil.Format.TXT) }
        )
        ExportRow(
            title = "导出全部订单（CSV）",
            subtitle = "Excel 可直接打开，含 BOM 不乱码",
            onClick = { vm.exportOrders(ExportUtil.Format.CSV) }
        )
        ExportRow(
            title = "导出运行日志（TXT）",
            subtitle = "串口事件 / 词典未命中 / 取消合并记录",
            onClick = { vm.exportRuntimeLog() }
        )
        ExportRow(
            title = "清空运行日志",
            subtitle = "只清内存环形日志，不动订单库",
            onClick = { vm.clearLog() }
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "文件保存位置：内部存储 / Download / KitchenKDS_orders_日期_时间.csv",
            color = c.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ExportRow(title: String, subtitle: String, onClick: () -> Unit) {
    val c = KdsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = c.textPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(text = subtitle, color = c.textSecondary, fontSize = 12.sp)
        }
    }
    KdsDivider()
}

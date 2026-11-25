package com.kitchenkds.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchenkds.BuildConfig
import com.kitchenkds.core.update.AppUpdater
import com.kitchenkds.ui.theme.KdsTheme

/**
 * 全局更新弹窗（V5.1）。
 *
 * 两种触发：
 *   ① 启动时静默检查到新版本 → 自动弹出（只在确实有新版本时）；
 *   ② 设置页手动点「检查更新」→ 有更新时弹出。
 *
 * 交互：立即更新 → 原地变成进度条 → 下载完自动拉起系统安装器。
 */
@Composable
fun UpdateDialog(
    info: AppUpdater.UpdateInfo,
    download: AppUpdater.DownloadState?,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val c = KdsTheme.colors
    val downloading = download is AppUpdater.DownloadState.Downloading
    val finished = download is AppUpdater.DownloadState.Success
    val progress = when (download) {
        is AppUpdater.DownloadState.Downloading -> download.progress
        is AppUpdater.DownloadState.Success -> 100
        else -> 0
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = c.surface,
        title = {
            Column {
                Text(
                    text = "发现新版本 v" + info.version,
                    color = c.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "当前 v" + BuildConfig.VERSION_NAME + " → 新版本 v" + info.version,
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "更新说明",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surfaceHigh)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = info.changelog,
                        color = c.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                if (download is AppUpdater.DownloadState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "下载失败：" + download.message,
                        color = c.danger,
                        fontSize = 12.sp
                    )
                }

                if (downloading || finished) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = c.accent,
                        trackColor = c.surfaceHigh
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (finished) "下载完成，正在打开安装器…" else "正在下载更新包",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = progress.toString() + "%",
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (finished) {
                // 被「安装未知应用」拦下过也没关系：授权后点这里直接拉起安装器，无需重新下载
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                ) {
                    Text(text = "打开安装器", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else if (downloading) {
                TextButton(onClick = { }, enabled = false) {
                    Text(text = "下载中…", color = c.textSecondary, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = onStartDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                ) {
                    Text(
                        text = if (download is AppUpdater.DownloadState.Error) "重新下载" else "立即更新",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) {
                Text(text = "稍后", color = c.textSecondary, fontSize = 14.sp)
            }
        }
    )
}

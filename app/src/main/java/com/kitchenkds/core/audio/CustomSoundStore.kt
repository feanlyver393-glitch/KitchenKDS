package com.kitchenkds.core.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kitchenkds.core.log.RingLog
import java.io.File

/**
 * 自定义提示音的落地存储（V5）。
 *
 * 为什么必须复制到私有目录：
 *   SAF 返回的 content:// URI 只是一张"临时通行证"，重启 / 换目录 / 清理缓存后读权限就没了，
 *   直接存 URI 的 App 一定会出现"今天能响，明天换台手机就不响"。
 * 因此拿到 Uri 立刻 openInputStream 全量复制到 filesDir/custom_alert_sound，
 * 之后播放只认这个真实文件路径。
 */
object CustomSoundStore {

    private const val TAG = "CustomSound"

    /** 私有目录里的固定文件名（覆盖式更新，永远只有一份） */
    const val FILE_NAME = "custom_alert_sound"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean {
        val f = file(context)
        return f.exists() && f.length() > 0L
    }

    fun sizeKb(context: Context): Long {
        val f = file(context)
        if (!f.exists()) return 0L
        return (f.length() / 1024L).coerceAtLeast(1L)
    }

    /**
     * 把用户选中的音频复制进私有目录（覆盖旧的）。
     * @return 展示给用户的原始文件名
     */
    fun import(context: Context, uri: Uri): String {
        val displayName = queryDisplayName(context, uri)
        val target = file(context)
        val tmp = File(context.filesDir, FILE_NAME + ".tmp")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法打开所选文件")
            input.use { src ->
                tmp.outputStream().use { dst -> src.copyTo(dst, 64 * 1024) }
            }
            if (tmp.length() <= 0L) throw IllegalStateException("所选文件是空的")
            if (target.exists() && !target.delete()) {
                RingLog.w(TAG, "old custom sound delete failed, will overwrite")
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            RingLog.d(TAG, "import ok: " + displayName + " bytes=" + target.length())
            return displayName
        } catch (t: Throwable) {
            try {
                if (tmp.exists()) tmp.delete()
            } catch (ignore: Throwable) {
            }
            RingLog.e(TAG, "import fail: " + t.message)
            throw t
        }
    }

    /** 删除自定义音效（切换回内置音时可选调用） */
    fun clear(context: Context) {
        val f = file(context)
        if (f.exists()) f.delete()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        var name = ""
        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx).orEmpty()
                }
            }
        } catch (t: Throwable) {
            RingLog.w(TAG, "query name fail: " + t.message)
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        }
        if (name.isBlank()) name = "自定义音频"
        return name
    }
}

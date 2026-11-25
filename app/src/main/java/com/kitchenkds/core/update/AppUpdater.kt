package com.kitchenkds.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kitchenkds.BuildConfig
import com.kitchenkds.core.log.RingLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 发版仓库（改仓库只改这两行） */
const val GITHUB_OWNER = "feanlyver393-glitch"
const val GITHUB_REPO = "KitchenKDS"

/**
 * 基于 GitHub Releases 的在线更新（V5.1）。
 *
 * 设计原则：
 *   ① 不引第三方网络库 —— 只用 HttpURLConnection + org.json，避免和 usb-serial / Room 抢依赖；
 *   ② 静默检查绝不打扰后厨接单：任何异常都只写 RingLog，不弹任何提示；
 *   ③ 下载走 Flow 实时百分比，安装包固定落在 cacheDir/updates/update.apk，
 *      通过 FileProvider 交给系统安装器（Android 8.0+ 需要"安装未知应用"授权）。
 *
 * Release 约定：
 *   - tag_name 用 v<versionName>（例如 v5.2.0），开头 v/V 会被自动去掉；
 *   - 必须上传一个以 .apk 结尾的附件，取 assets 里第一个 .apk。
 */
class AppUpdater(private val context: Context) {

    private companion object {
        const val TAG = "Updater"
        const val RELEASES_API = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest"
        const val USER_AGENT = "KitchenKDS"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val MAX_REDIRECTS = 5
        const val BUFFER_SIZE = 64 * 1024
    }

    /** 发现的新版本 */
    data class UpdateInfo(
        /** 去掉 v 前缀后的远程版本号，如 "1.0.2" */
        val version: String,
        /** Release body（更新说明） */
        val changelog: String,
        /** 第一个 .apk 附件的下载地址 */
        val downloadUrl: String,
        /** 原始 tag，如 "v1.0.2" */
        val tagName: String
    )

    /** 下载状态 */
    sealed class DownloadState {
        data class Downloading(val progress: Int) : DownloadState()
        data class Success(val apkFile: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    // ───────────────── 检查更新 ─────────────────

    /**
     * 查询最新 Release。
     * @return 有新版本 → UpdateInfo；已是最新 / 没有可用 Release → null
     * @throws IOException 网络或解析异常（手动检查时由 UI 提示，静默检查时忽略）
     */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        // 仓库还没有任何 Release 时 GitHub 返回 404：这是"暂无更新"，不是错误
        val body = requestLatestRelease() ?: return@withContext null
        val json = JSONObject(body)

        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) {
            RingLog.w(TAG, "release has no tag_name")
            return@withContext null
        }
        val remoteVersion = normalizeVersion(tag)
        val changelog = json.optString("body").trim().ifEmpty { "该版本没有填写更新说明。" }

        val assets = json.optJSONArray("assets")
        var downloadUrl = ""
        var apkName = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = asset.optString("browser_download_url")
                    apkName = name
                    break
                }
            }
        }
        if (downloadUrl.isEmpty()) {
            RingLog.w(TAG, "release " + tag + " has no .apk asset")
            return@withContext null
        }

        RingLog.d(
            TAG,
            "latest=" + remoteVersion + " local=" + BuildConfig.VERSION_NAME + " apk=" + apkName
        )
        if (!isNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@withContext null
        UpdateInfo(remoteVersion, changelog, downloadUrl, tag)
    }

    /** @return Release JSON；仓库还没有 Release（404）时返回 null */
    private fun requestLatestRelease(): String? {
        val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                RingLog.d(TAG, "no release published yet (404)")
                return null
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub 返回 HTTP " + code)
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ───────────────── 下载 ─────────────────

    /** 下载 APK，实时发射 0~100 的百分比，完成后给出本地文件 */
    fun downloadApk(downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))

        val dir = File(context.cacheDir, "updates")
        // 下载前清空旧安装包，避免缓存目录越积越大（也防止装到上一次的残包）
        runCatching {
            dir.listFiles()?.forEach { old -> if (old.isFile) old.delete() }
        }
        if (!dir.exists() && !dir.mkdirs()) {
            emit(DownloadState.Error("无法创建下载目录"))
            return@flow
        }

        val target = File(dir, "update.apk")
        val part = File(dir, "update.apk.part")
        var conn: HttpURLConnection? = null
        try {
            conn = openFollowing(downloadUrl, 0)
            val total = conn.contentLengthLong

            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0L) {
                            val percent = (written * 100L / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(DownloadState.Downloading(percent))
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (part.length() <= 0L) throw IOException("下载到的文件为空")
            if (target.exists() && !target.delete()) {
                RingLog.w(TAG, "old update.apk delete failed, will overwrite")
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            RingLog.d(TAG, "download ok bytes=" + target.length() + " path=" + target.absolutePath)
            emit(DownloadState.Downloading(100))
            emit(DownloadState.Success(target))
        } catch (t: Throwable) {
            // 协程取消要原样抛出，别把"用户退出"当成下载失败
            if (t is kotlinx.coroutines.CancellationException) throw t
            runCatching { if (part.exists()) part.delete() }
            RingLog.e(TAG, "download fail: " + t.message)
            emit(DownloadState.Error(t.message ?: "下载失败"))
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** 跟随 GitHub CDN 的 302 跳转（HttpURLConnection 跨域重定向不总是自动跟随） */
    private fun openFollowing(url: String, depth: Int): HttpURLConnection {
        if (depth > MAX_REDIRECTS) throw IOException("重定向次数过多")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/octet-stream")
        conn.connect()

        val code = conn.responseCode
        if (code in 300..399) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) throw IOException("重定向缺少 Location")
            return openFollowing(URL(URL(url), location).toString(), depth + 1)
        }
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("下载失败 HTTP " + code)
        }
        return conn
    }

    // ───────────────── 安装 ─────────────────

    /** Android 8.0+ 需要"安装未知应用"的全局授权 */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 跳去系统设置页授权"安装未知应用" */
    fun openUnknownSourcesSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            RingLog.e(TAG, "open unknown sources settings fail: " + t.message)
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t2: Throwable) {
                RingLog.e(TAG, "open settings fallback fail: " + t2.message)
            }
        }
    }

    /**
     * 拉起系统安装器。
     * @return true = 已经拉起安装界面；false = 需要先授权（已自动跳转到授权页）
     */
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            RingLog.e(TAG, "install fail: apk missing " + apkFile.absolutePath)
            return false
        }
        if (!canInstallPackages()) {
            RingLog.w(TAG, "no REQUEST_INSTALL_PACKAGES permission, opening settings")
            openUnknownSourcesSettings()
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            RingLog.d(TAG, "installer launched: " + uri)
            true
        } catch (t: Throwable) {
            RingLog.e(TAG, "install fail: " + t.message)
            false
        }
    }

    // ───────────────── 版本比较 ─────────────────

    /**
     * 把 tag 归一成纯版本号，兼容各种发版命名：
     *   v5.2.0 → 5.2.0 | V5.2.0 → 5.2.0 | KitchenKDS-v5.2.0 → 5.2.0 | release_5.2.0 → 5.2.0
     */
    fun normalizeVersion(tag: String): String {
        val raw = tag.trim()
        val firstDigit = raw.indexOfFirst { it.isDigit() }
        if (firstDigit > 0) return raw.substring(firstDigit).trim()
        return raw.removePrefix("v").removePrefix("V").trim()
    }

    /**
     * 语义化版本比较：按 . 分段转数字逐位比较，缺位补 0。
     *   "1.0.2" > "1.0.1" ✔    "1.1.0" > "1.0.9" ✔    "1.0.10" > "1.0.9" ✔
     */
    fun isNewer(remote: String, local: String): Boolean {
        val r = segments(remote)
        val l = segments(local)
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private fun segments(version: String): List<Int> =
        normalizeVersion(version)
            .split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }
}

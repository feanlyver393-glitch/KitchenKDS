package com.kitchenkds.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import com.kitchenkds.core.log.RingLog
import com.kitchenkds.domain.model.AlertSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 来单提示音播放引擎（V5，替代旧的 SoundPool 版 SoundManager）。
 *
 * 为什么放弃 SoundPool：
 *   ① SoundPool 有 1MB 解码缓冲上限，用户导入的 MP3/M4A 稍大就 loading 失败；
 *   ② 它在部分机型上对 AAC/M4A 解码不兼容，表现为"设置页能听、装到别的手机就没声"；
 *   ③ SoundPool 的音频属性一旦建池就固定，无法按需在"闹钟通道/媒体通道"之间切换。
 * MediaPlayer 没有上述限制，代价是需要自己管理实例生命周期 —— 本类负责这件事。
 *
 * 三条硬要求：
 *   ① 强制高优先级通道：默认 USAGE_ALARM + CONTENT_TYPE_SONIFICATION，
 *      即使备用机处于静音/震动也能响；也支持切到 USAGE_MEDIA。
 *   ② 每次播放前安全释放上一个 MediaPlayer，避免声道被占满 / 内存泄漏。
 *   ③ 绝不哑火：音频文件丢失、解码失败、URI 失效，一律降级 ToneGenerator(STREAM_ALARM) 蜂鸣。
 */
class AlertSoundPlayer(private val context: Context) {

    /** 一次播放请求 = 音源 + 响几声 + 间隔 + 走哪个通道 */
    data class Spec(
        /** "0".."4" 或 "CUSTOM" */
        val mode: String,
        val times: Int,
        val intervalMs: Long,
        /** true = USAGE_ALARM（闹钟通道，静音也响）；false = USAGE_MEDIA */
        val useAlarmChannel: Boolean
    )

    private companion object {
        const val TAG = "AlertSound"
        const val MAX_TIMES = 10
        const val MAX_INTERVAL_MS = 10_000L
        const val FALLBACK_TONE_MS = 500
    }

    private sealed class Source {
        class Raw(val resId: Int) : Source()
        class LocalFile(val file: java.io.File) : Source()
        object Missing : Source()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var playJob: Job? = null

    /**
     * 播放一整轮提示音（响 times 次，中间间隔 intervalMs）。
     * 新订单连续到达时，上一次未播完的循环会被取消并立刻重新开始，避免两声叠在一起炸麦。
     */
    fun play(spec: Spec) {
        playJob?.cancel()
        releasePlayer()

        val times = spec.times.coerceIn(1, MAX_TIMES)
        val gap = spec.intervalMs.coerceIn(0L, MAX_INTERVAL_MS)
        RingLog.d(
            TAG,
            "play mode=" + spec.mode +
                " times=" + times +
                " gap=" + gap + "ms" +
                " channel=" + (if (spec.useAlarmChannel) "ALARM" else "MEDIA")
        )
        logAudioRoute(spec.useAlarmChannel)

        playJob = scope.launch {
            repeat(times) { index ->
                if (!isActive) return@launch
                playOnce(spec)
                if (index < times - 1 && gap > 0L) delay(gap)
            }
        }
    }

    /** 立刻停掉当前播放（页面退出 / 关闭音效时用） */
    fun stop() {
        playJob?.cancel()
        playJob = null
        releasePlayer()
    }

    fun release() {
        stop()
        synchronized(lock) {
            try {
                tone?.release()
            } catch (t: Throwable) {
                RingLog.w(TAG, "tone release: " + t.message)
            }
            tone = null
        }
    }

    // ───────────────── 单次播放 ─────────────────

    private fun playOnce(spec: Spec) {
        releasePlayer()
        val source = resolve(spec.mode)
        if (source is Source.Missing) {
            ringFallbackTone("source missing, mode=" + spec.mode)
            return
        }

        val mp = MediaPlayer()
        synchronized(lock) { player = mp }
        try {
            mp.setAudioAttributes(buildAttributes(spec.useAlarmChannel))
            mp.isLooping = false
            when (source) {
                is Source.Raw -> {
                    val afd = context.resources.openRawResourceFd(source.resId)
                    afd.use { fd ->
                        mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                }
                is Source.LocalFile -> mp.setDataSource(source.file.absolutePath)
                Source.Missing -> Unit
            }
            mp.setOnCompletionListener { finished -> safeRelease(finished) }
            mp.setOnErrorListener { broken, what, extra ->
                RingLog.e(TAG, "MediaPlayer onError what=" + what + " extra=" + extra)
                safeRelease(broken)
                ringFallbackTone("media error what=" + what)
                true
            }
            // 短音 + 本地文件：同步 prepare 让第一个音不延迟（异步还要多等一帧）
            mp.prepare()
            mp.start()
        } catch (t: Throwable) {
            RingLog.e(
                TAG,
                "play fail: " + t.javaClass.simpleName + " " + t.message + " -> fallback tone"
            )
            safeRelease(mp)
            ringFallbackTone("exception " + t.javaClass.simpleName)
        }
    }

    /**
     * 解析音源：CUSTOM 走私有目录文件，其余走 res/raw。
     *
     * 降级链（逐级兜底，绝不哑火）：
     *   自定义文件 → 第 1 个内置 raw → ToneGenerator 蜂鸣
     */
    private fun resolve(mode: String): Source {
        if (AlertSounds.isCustom(mode)) {
            val file = CustomSoundStore.file(context)
            if (file.exists() && file.length() > 0L) {
                RingLog.d(TAG, "source custom file " + file.absolutePath + " bytes=" + file.length())
                return Source.LocalFile(file)
            }
            RingLog.w(TAG, "custom sound file missing, fallback to builtin: " + file.absolutePath)
            return resolveBuiltin(AlertSounds.fallback())
        }
        return resolveBuiltin(AlertSounds.byMode(mode) ?: AlertSounds.fallback())
    }

    private fun resolveBuiltin(builtin: AlertSounds.Builtin): Source {
        val resId = context.resources.getIdentifier(builtin.rawName, "raw", context.packageName)
        return if (resId != 0) {
            Source.Raw(resId)
        } else {
            RingLog.e(TAG, "raw resource not found: " + builtin.rawName)
            Source.Missing
        }
    }

    /**
     * 音频属性：ALARM = 闹钟通道（静音/震动也响），MEDIA = 媒体通道。
     * contentType 固定 SONIFICATION，避免被系统当成"媒体播放"降优先级。
     */
    private fun buildAttributes(useAlarm: Boolean): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(if (useAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /** 兜底发声：永远不哑火 */
    private fun ringFallbackTone(reason: String) {
        try {
            val gen = synchronized(lock) {
                tone ?: ToneGenerator(AudioManager.STREAM_ALARM, 100).also { tone = it }
            }
            val started = gen.startTone(ToneGenerator.TONE_PROP_BEEP2, FALLBACK_TONE_MS)
            RingLog.w(
                TAG,
                "fallback tone " + (if (started) "ok" else "rejected") + " reason=" + reason
            )
        } catch (t: Throwable) {
            RingLog.e(TAG, "fallback tone crashed: " + t.message)
        }
    }

    private fun releasePlayer() {
        val current = synchronized(lock) {
            val p = player
            player = null
            p
        }
        if (current != null) safeRelease(current)
    }

    /** 只释放传进来的那一个实例，绝不误杀后来居上的新实例 */
    private fun safeRelease(mp: MediaPlayer) {
        synchronized(lock) { if (player === mp) player = null }
        try {
            mp.reset()
        } catch (t: Throwable) {
        }
        try {
            mp.release()
        } catch (t: Throwable) {
        }
    }

    /** 把当前音量 / 铃声模式打进日志：换机后"没声"第一件事就是看这三行 */
    private fun logAudioRoute(useAlarm: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val stream = if (useAlarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
            RingLog.d(
                TAG,
                "stream=" + stream +
                    " vol=" + am.getStreamVolume(stream) + "/" + am.getStreamMaxVolume(stream) +
                    " ringerMode=" + am.ringerMode
            )
        } catch (t: Throwable) {
            RingLog.w(TAG, "audio route probe fail: " + t.message)
        }
    }
}

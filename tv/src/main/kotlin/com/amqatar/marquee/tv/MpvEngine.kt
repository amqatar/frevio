package com.amqatar.marquee.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * libmpv as the second engine. HW mode decodes with MediaCodec straight to the surface
 * (vo=mediacodec_embed), which keeps HDR going to the TV. SW mode decodes with FFmpeg and renders
 * with OpenGL ES; that covers codecs the TV has no decoder for (VVC), but output is SDR.
 */
class MpvEngine(
    context: Context,
    private val url: String,
    private val hardware: Boolean,
    audio: MpvAudio,
) : TestPlayer, MPVLib.EventObserver, SurfaceHolder.Callback {

    private val state = MutableStateFlow(Diagnostics(engine = "mpv ${if (hardware) "HW" else "SW"} (audio: ${audio.label})"))
    override val diagnostics: StateFlow<Diagnostics> = state

    private val main = Handler(Looper.getMainLooper())
    private val mpv: MPVLib = MPVLib.create(context.applicationContext) ?: error("libmpv failed to load")
    private val vo = if (hardware) "mediacodec_embed" else "gpu"
    private var loaded = false
    private val props = mutableMapOf<String, Any>()

    init {
        mpv.setOptionString("config", "no")
        mpv.setOptionString("vo", vo)
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("hwdec", if (hardware) "mediacodec" else "no")
        mpv.setOptionString("ao", "audiotrack,opensles")
        // mpv defaults to "auto-safe", which can mean stereo on Android. Ask for what the sink reports.
        mpv.setOptionString("audio-channels", "auto")
        when (audio) {
            MpvAudio.DECODE -> Unit
            MpvAudio.PASSTHROUGH -> mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dts-hd,truehd")
            MpvAudio.AC3_TRANSCODE -> {
                mpv.setOptionString("af", "lavcac3enc")
                mpv.setOptionString("audio-spdif", "ac3")
            }
        }
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("idle", "yes")
        mpv.setOptionString("keep-open", "yes")
        mpv.init()

        mpv.addObserver(this)
        listOf(
            "time-pos" to FORMAT_DOUBLE, "duration" to FORMAT_DOUBLE, "pause" to FORMAT_FLAG,
            "video-codec" to FORMAT_STRING, "hwdec-current" to FORMAT_STRING, "video-params/gamma" to FORMAT_STRING,
            "video-params/primaries" to FORMAT_STRING, "width" to FORMAT_INT64, "height" to FORMAT_INT64,
            "container-fps" to FORMAT_DOUBLE, "frame-drop-count" to FORMAT_INT64, "decoder-frame-drop-count" to FORMAT_INT64,
            "audio-codec" to FORMAT_STRING, "audio-params/channel-count" to FORMAT_INT64,
            "audio-out-params/format" to FORMAT_STRING, "audio-out-params/channel-count" to FORMAT_INT64, "current-ao" to FORMAT_STRING,
            "eof-reached" to FORMAT_FLAG,
        ).forEach { (name, format) -> mpv.observeProperty(name, format) }
    }

    override fun attach(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(this)
        if (surfaceView.holder.surface?.isValid == true) surfaceCreated(surfaceView.holder)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mpv.attachSurface(holder.surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", vo)
        if (!loaded) {
            loaded = true
            mpv.command(arrayOf("loadfile", url))
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mpv.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mpv.setPropertyString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
    }

    override fun togglePause() = mpv.command(arrayOf("cycle", "pause"))

    override fun seekBy(seconds: Int) = mpv.command(arrayOf("seek", seconds.toString(), "relative"))

    override fun release() {
        mpv.removeObserver(this)
        mpv.destroy()
    }

    // --- mpv callbacks arrive on mpv's event thread; state is updated on the main thread. ---

    override fun eventProperty(property: String) = Unit
    override fun eventProperty(property: String, value: Long) = onProp(property, value)
    override fun eventProperty(property: String, value: Double) = onProp(property, value)
    override fun eventProperty(property: String, value: Boolean) = onProp(property, value)
    override fun eventProperty(property: String, value: String) = onProp(property, value)

    override fun event(eventId: Int) {
        main.post {
            when (eventId) {
                EVENT_FILE_LOADED -> state.update { it.copy(state = "Playing") }
                EVENT_END_FILE -> state.update { if (it.state == "Starting") it.copy(state = "Error", error = "mpv could not open the file") else it }
            }
        }
    }

    private fun onProp(name: String, value: Any) = main.post {
        props[name] = value
        refresh()
    }

    private fun refresh() {
        fun s(k: String) = props[k] as? String
        fun l(k: String) = props[k] as? Long
        fun d(k: String) = props[k] as? Double
        val hwdec = s("hwdec-current")
        val gamma = s("video-params/gamma")
        val fps = d("container-fps") ?: 0.0
        val outFormat = s("audio-out-params/format")
        val outCh = l("audio-out-params/channel-count")
        state.update {
            it.copy(
                state = when {
                    props["eof-reached"] == true -> "Ended"
                    props["pause"] == true -> "Paused"
                    it.state == "Starting" && props.containsKey("time-pos") -> "Playing"
                    it.state == "Paused" -> "Playing"
                    else -> it.state
                },
                positionMs = ((d("time-pos") ?: 0.0) * 1000).toLong(),
                durationMs = ((d("duration") ?: 0.0) * 1000).toLong(),
                videoDecoder = s("video-codec")?.let { c -> if (hwdec.isNullOrEmpty() || hwdec == "no") "$c (FFmpeg)" else "$c ($hwdec)" },
                videoHardware = hwdec?.let { h -> h.isNotEmpty() && h != "no" },
                videoFormat = listOfNotNull(
                    s("video-codec"),
                    l("width")?.let { w -> "${w}x${l("height")}" },
                    fps.takeIf { f -> f > 0 }?.let { f -> "%.3f fps".format(f) },
                ).joinToString(" "),
                contentFps = fps.toFloat(),
                hdr = when (gamma) {
                    "pq" -> if (hardware) "PQ (HDR10)" else "PQ, tone-mapped to SDR (SW render)"
                    "hlg" -> if (hardware) "HLG" else "HLG, tone-mapped to SDR (SW render)"
                    null -> it.hdr
                    else -> "SDR ($gamma)"
                },
                droppedFrames = (l("frame-drop-count") ?: 0) + (l("decoder-frame-drop-count") ?: 0),
                audioDecoder = s("audio-codec"),
                audioInput = listOfNotNull(s("audio-codec"), l("audio-params/channel-count")?.let { c -> "${c}ch" }).joinToString(" "),
                audioOutput = outFormat?.let { f ->
                    if (f.startsWith("spdif-")) "Bitstream: ${f.removePrefix("spdif-")}" else "Decoded: ${outCh ?: "?"}-ch $f"
                }?.plus(s("current-ao")?.let { ao -> " via $ao" } ?: ""),
            )
        }
    }

    private companion object {
        // libmpv client.h values.
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
    }
}

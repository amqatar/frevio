@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.frevio.player.tv

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Diagnostics(
    val engine: String,
    val state: String = "Starting",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val videoDecoder: String? = null,
    val videoHardware: Boolean? = null,
    val videoFormat: String? = null,
    val contentFps: Float = 0f,
    val hdr: String? = null,
    val droppedFrames: Long = 0,
    val audioDecoder: String? = null,
    val audioInput: String? = null,
    val audioOutput: String? = null,
    val displayMode: String? = null,
    val error: String? = null,
)

interface TestPlayer {
    val diagnostics: StateFlow<Diagnostics>
    fun attach(surfaceView: SurfaceView)
    fun togglePause()
    fun seekBy(seconds: Int)
    fun release()
}

class Media3Engine(context: Context, url: String, settings: TestSettings) : TestPlayer {
    private val state = MutableStateFlow(Diagnostics(engine = "Media3${if (settings.tunneling) " (tunneled)" else ""}"))
    override val diagnostics: StateFlow<Diagnostics> = state

    private val player: ExoPlayer

    init {
        // Platform decoders only: the TV's hardware decoders, then Android's software ones.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTunnelingEnabled(settings.tunneling))
        }
        player = ExoPlayer.Builder(context, renderers).setTrackSelector(trackSelector).build()
        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        player.videoChangeFrameRateStrategy =
            if (settings.frameRate == FrameRateMode.SEAMLESS) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        player.addAnalyticsListener(Listener())
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.update {
                    it.copy(
                        state = when (playbackState) {
                            Player.STATE_BUFFERING -> "Buffering"
                            Player.STATE_READY -> if (player.playWhenReady) "Playing" else "Paused"
                            Player.STATE_ENDED -> "Ended"
                            else -> "Idle"
                        },
                        durationMs = player.duration.coerceAtLeast(0),
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.update { it.copy(state = if (isPlaying) "Playing" else it.state.takeIf { s -> s != "Playing" } ?: "Paused") }
            }
        })
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    override fun attach(surfaceView: SurfaceView) = player.setVideoSurfaceView(surfaceView)

    fun tick() {
        state.update {
            it.copy(
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
                droppedFrames = player.videoDecoderCounters?.droppedBufferCount?.toLong() ?: it.droppedFrames,
            )
        }
    }

    override fun togglePause() {
        player.playWhenReady = !player.playWhenReady
        state.update { it.copy(state = if (player.playWhenReady) "Playing" else "Paused") }
    }

    override fun seekBy(seconds: Int) = player.seekTo((player.currentPosition + seconds * 1000L).coerceAtLeast(0))

    override fun release() = player.release()

    private inner class Listener : AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            state.update { it.copy(videoDecoder = decoderName, videoHardware = isHardwareCodec(decoderName)) }
        }

        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            state.update { it.copy(audioDecoder = decoderName) }
        }

        override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            state.update { it.copy(videoFormat = describeVideo(format), hdr = describeHdr(format), contentFps = format.frameRate.takeIf { f -> f > 0 } ?: 0f) }
        }

        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            state.update { it.copy(audioInput = "${format.sampleMimeType} ${format.codecs ?: ""} ${format.channelCount}ch ${format.sampleRate} Hz".trim()) }
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
            val channels = Integer.bitCount(audioTrackConfig.channelConfig)
            val pcm = audioTrackConfig.encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_8BIT)
            val out = if (pcm) "Decoded: $channels-ch ${media3Encoding(audioTrackConfig.encoding)}"
            else "Bitstream: ${media3Encoding(audioTrackConfig.encoding)}"
            val extras = listOfNotNull("tunneled".takeIf { audioTrackConfig.tunneling }, "offload".takeIf { audioTrackConfig.offload })
            state.update { it.copy(audioOutput = out + if (extras.isEmpty()) "" else " (${extras.joinToString()})") }
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            state.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
        }

        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            state.update { it.copy(state = "Error", error = "${error.errorCodeName}: ${error.cause?.message ?: error.message}") }
        }
    }

    companion object {
        /** Hardware means a MediaCodec the platform marks as accelerated. */
        fun isHardwareCodec(name: String): Boolean {
            if (Build.VERSION.SDK_INT >= 29) {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }?.let { return it.isHardwareAccelerated }
                return false
            }
            return !(name.startsWith("OMX.google.") || name.startsWith("c2.android.") || name.contains("ffmpeg", ignoreCase = true) || name.contains("dav1d", ignoreCase = true))
        }

        fun describeVideo(f: Format): String = buildString {
            append(f.sampleMimeType ?: "video")
            f.codecs?.let { append(" ").append(it) }
            if (f.width > 0) append(" ${f.width}x${f.height}")
            if (f.frameRate > 0) append(" %.3f fps".format(f.frameRate))
            if (f.bitrate > 0) append(" ${f.bitrate / 1000} kb/s")
        }

        fun describeHdr(f: Format): String {
            if (f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION || f.codecs?.startsWith("dv") == true) return "Dolby Vision (${f.codecs ?: "?"})"
            return when (f.colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> "PQ (HDR10/HDR10+)"
                C.COLOR_TRANSFER_HLG -> "HLG"
                else -> "SDR"
            }
        }

        fun media3Encoding(e: Int): String = when (e) {
            C.ENCODING_PCM_16BIT -> "PCM 16-bit"
            C.ENCODING_PCM_24BIT -> "PCM 24-bit"
            C.ENCODING_PCM_32BIT -> "PCM 32-bit"
            C.ENCODING_PCM_FLOAT -> "PCM float"
            C.ENCODING_AC3 -> "Dolby Digital (AC-3)"
            C.ENCODING_E_AC3 -> "Dolby Digital Plus (E-AC-3)"
            C.ENCODING_E_AC3_JOC -> "Dolby Digital Plus Atmos (E-AC-3 JOC)"
            C.ENCODING_AC4 -> "AC-4"
            C.ENCODING_DTS -> "DTS"
            C.ENCODING_DTS_HD -> "DTS-HD"
            C.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD"
            else -> "encoding $e"
        }
    }
}

package com.frevio.player.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `index.json` served next to the test files (written by tools/make-test-media.sh). */
@Serializable
data class TestMediaIndex(val version: Int = 1, val items: List<TestMediaItem> = emptyList())

@Serializable
data class TestMediaItem(
    val file: String,
    val title: String,
    val group: String = "Other",
    val video: String? = null,
    val audio: String? = null,
    /** What a pass looks like, shown before playback. */
    val expect: String = "",
)

@Serializable
data class DisplayInfo(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val supportedRefreshRates: List<Float>,
    val hdrTypes: List<String>,
)

@Serializable
data class AudioOutputInfo(
    val type: String,
    val name: String,
    val encodings: List<String>,
    val channelCounts: List<Int>,
)

@Serializable
data class DeviceReport(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val abis: List<String>,
    val is64BitProcess: Boolean,
    val ramMb: Long,
    val display: DisplayInfo,
    val audioOutputs: List<AudioOutputInfo>,
    /** Encoding name -> AudioTrack.isDirectPlaybackSupported, i.e. what can be bitstreamed now. */
    val directPlayback: Map<String, Boolean>,
    val maxPcmChannels: Int,
)

@Serializable
data class TestRun(
    val item: String,
    val engine: String,
    val startedAt: Long,
    val videoDecoder: String? = null,
    val videoHardware: Boolean? = null,
    val videoFormat: String? = null,
    val hdr: String? = null,
    val droppedFrames: Long = 0,
    val audioDecoder: String? = null,
    val audioOutput: String? = null,
    val displayMode: String? = null,
    val verdict: String = "unrated",
    val error: String? = null,
)

@Serializable
data class TestReport(val device: DeviceReport, val runs: List<TestRun> = emptyList())

val FrevioJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

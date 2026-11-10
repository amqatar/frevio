package com.frevio.player.tv

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.view.Display
import com.frevio.player.shared.AudioCodec
import com.frevio.player.shared.AudioOutputInfo
import com.frevio.player.shared.DeviceReport
import com.frevio.player.shared.DisplayInfo
import com.frevio.player.shared.DisplayMode
import com.frevio.player.shared.SinkCapabilities

/** Reads what the TV and the connected soundbar/receiver can do. Everything here is reported, not assumed. */
object DeviceProbe {

    private data class Probe(val name: String, val codec: AudioCodec?, val encoding: Int, val channelMask: Int)

    private fun probes(): List<Probe> = buildList {
        val m51 = AudioFormat.CHANNEL_OUT_5POINT1
        val m71 = AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        add(Probe("AC-3", AudioCodec.AC3, AudioFormat.ENCODING_AC3, m51))
        add(Probe("E-AC-3", AudioCodec.EAC3, AudioFormat.ENCODING_E_AC3, m51))
        if (Build.VERSION.SDK_INT >= 28) {
            add(Probe("E-AC-3 JOC (Atmos)", AudioCodec.EAC3_JOC, AudioFormat.ENCODING_E_AC3_JOC, m51))
            add(Probe("AC-4", AudioCodec.AC4, AudioFormat.ENCODING_AC4, m51))
        }
        add(Probe("TrueHD", AudioCodec.TRUEHD, AudioFormat.ENCODING_DOLBY_TRUEHD, m71))
        if (Build.VERSION.SDK_INT >= 30) add(Probe("Dolby MAT", null, AudioFormat.ENCODING_DOLBY_MAT, m71))
        add(Probe("DTS", AudioCodec.DTS, AudioFormat.ENCODING_DTS, m51))
        add(Probe("DTS-HD", AudioCodec.DTS_HD_MA, AudioFormat.ENCODING_DTS_HD, m71))
        if (Build.VERSION.SDK_INT >= 34) {
            add(Probe("DTS-HD MA", AudioCodec.DTS_HD_MA, AudioFormat.ENCODING_DTS_HD_MA, m71))
            add(Probe("DTS:X (UHD P1)", AudioCodec.DTS_X, AudioFormat.ENCODING_DTS_UHD_P1, m71))
        }
    }

    private val movieAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    /** Encoding name -> can it be bitstreamed to the current output right now. Needs Android 10+. */
    fun directPlayback(): Map<String, Boolean> {
        if (Build.VERSION.SDK_INT < 29) return emptyMap()
        return probes().associate { p ->
            val format = AudioFormat.Builder().setEncoding(p.encoding).setSampleRate(48_000).setChannelMask(p.channelMask).build()
            p.name to runCatching { AudioTrack.isDirectPlaybackSupported(format, movieAttributes) }.getOrDefault(false)
        }
    }

    fun audioOutputs(context: Context): List<AudioOutputInfo> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
            AudioOutputInfo(
                type = deviceType(d.type),
                name = d.productName?.toString().orEmpty(),
                encodings = d.encodings.map(::encodingName).distinct(),
                channelCounts = d.channelCounts.toList().sorted(),
            )
        }
    }

    fun maxPcmChannels(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val hdmiTypes = buildSet {
            add(AudioDeviceInfo.TYPE_HDMI)
            add(AudioDeviceInfo.TYPE_HDMI_ARC)
            if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_HDMI_EARC)
        }
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in hdmiTypes }
            .flatMap { it.channelCounts.toList() }
            .maxOrNull()
            ?: 2
    }

    fun sinkCapabilities(context: Context): SinkCapabilities {
        val supported = directPlayback().filterValues { it }.keys
        val codecs = probes().filter { it.name in supported }.mapNotNull { it.codec }.toSet()
        return SinkCapabilities(codecs, maxPcmChannels(context))
    }

    fun displayModes(display: Display): List<DisplayMode> =
        display.supportedModes.map { DisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }

    fun currentMode(display: Display): DisplayMode =
        display.mode.let { DisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }

    @Suppress("DEPRECATION")
    fun display(activity: Activity): Display =
        if (Build.VERSION.SDK_INT >= 30) activity.display!! else activity.windowManager.defaultDisplay

    @Suppress("DEPRECATION")
    private fun hdrTypes(display: Display): List<String> {
        val types = if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes else display.hdrCapabilities?.supportedHdrTypes
        return (types ?: IntArray(0)).map {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> "type $it"
            }
        }
    }

    fun report(activity: Activity): DeviceReport {
        val display = display(activity)
        val mode = currentMode(display)
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return DeviceReport(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            is64BitProcess = Process.is64Bit(),
            ramMb = mem.totalMem / (1024 * 1024),
            display = DisplayInfo(
                width = mode.width,
                height = mode.height,
                refreshRate = mode.refreshRate,
                supportedRefreshRates = displayModes(display).filter { it.width == mode.width && it.height == mode.height }
                    .map { it.refreshRate }.distinct().sorted(),
                hdrTypes = hdrTypes(display),
            ),
            audioOutputs = audioOutputs(activity),
            directPlayback = directPlayback(),
            maxPcmChannels = maxPcmChannels(activity),
        )
    }

    fun deviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TV speakers"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        29 -> "HDMI eARC" // AudioDeviceInfo.TYPE_HDMI_EARC, API 31
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Optical/digital line"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphones"
        else -> "type $type"
    }

    fun encodingName(e: Int): String = when (e) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
        AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
        AudioFormat.ENCODING_AC3 -> "AC-3"
        AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
        18 -> "E-AC-3 JOC"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
        AudioFormat.ENCODING_IEC61937 -> "IEC 61937"
        AudioFormat.ENCODING_DOLBY_TRUEHD -> "TrueHD"
        17 -> "AC-4"
        19 -> "Dolby MAT"
        else -> "encoding $e"
    }
}

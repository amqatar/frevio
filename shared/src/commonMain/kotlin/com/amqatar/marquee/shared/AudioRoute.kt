package com.amqatar.marquee.shared

/** Audio formats the player distinguishes when deciding how to get sound to the soundbar or receiver. */
enum class AudioCodec(val objectBased: Boolean = false, val lossless: Boolean = false) {
    AC3,
    EAC3,
    EAC3_JOC(objectBased = true),
    TRUEHD(lossless = true),
    TRUEHD_ATMOS(objectBased = true, lossless = true),
    DTS,
    DTS_HD_MA(lossless = true),
    DTS_X(objectBased = true, lossless = true),
    AC4,
    AAC,
    OPUS,
    FLAC(lossless = true),
    PCM(lossless = true),
    OTHER,
}

/** How a track reaches the sink, best first. */
enum class AudioPath {
    /** Compressed bitstream passed through untouched (keeps Atmos / DTS:X objects). */
    BITSTREAM,
    /** Decoded on the TV, sent as multichannel PCM. Keeps the channels, loses objects. */
    PCM_MULTICHANNEL,
    /** Decoded, then re-encoded to Dolby Digital 5.1 for sinks without multichannel PCM. */
    AC3_TRANSCODE,
    /** Decoded and downmixed to two channels. Last resort. */
    STEREO,
}

/** What the connected sink accepts, as Android reports it over HDMI ARC/eARC. */
data class SinkCapabilities(
    val bitstream: Set<AudioCodec>,
    val maxPcmChannels: Int,
) {
    /**
     * Object formats ride on a base codec: a sink that takes plain E-AC-3 still accepts an
     * E-AC-3 JOC stream (it plays the 5.1/7.1 bed). Same for TrueHD Atmos on a TrueHD sink.
     * DTS:X needs a DTS-HD capable sink.
     */
    fun accepts(codec: AudioCodec): Boolean = codec in bitstream || when (codec) {
        AudioCodec.EAC3_JOC -> AudioCodec.EAC3 in bitstream
        AudioCodec.TRUEHD_ATMOS -> AudioCodec.TRUEHD in bitstream
        AudioCodec.DTS_X -> AudioCodec.DTS_HD_MA in bitstream
        else -> false
    }
}

data class AudioTrackInfo(val id: String, val codec: AudioCodec, val channels: Int, val language: String? = null)

data class AudioRoute(val track: AudioTrackInfo, val path: AudioPath, val outputChannels: Int, val reason: String)

/** Decides the output path for one track. */
fun routeAudio(track: AudioTrackInfo, sink: SinkCapabilities, allowTranscode: Boolean = true): AudioRoute {
    val ch = track.channels.coerceAtLeast(1)
    if (sink.accepts(track.codec)) {
        return AudioRoute(track, AudioPath.BITSTREAM, ch, "sink accepts ${track.codec} bitstream")
    }
    if (ch <= 2) {
        return AudioRoute(track, AudioPath.PCM_MULTICHANNEL, ch, "stereo or mono source, PCM is lossless here")
    }
    val wanted = ch.coerceAtMost(8)
    if (sink.maxPcmChannels >= wanted) {
        return AudioRoute(track, AudioPath.PCM_MULTICHANNEL, wanted, "sink takes $wanted-channel PCM")
    }
    if (allowTranscode && AudioCodec.AC3 in sink.bitstream) {
        return AudioRoute(track, AudioPath.AC3_TRANSCODE, 6, "sink lacks ${track.codec} and $wanted-ch PCM; re-encoding to Dolby Digital 5.1")
    }
    if (sink.maxPcmChannels > 2) {
        return AudioRoute(track, AudioPath.PCM_MULTICHANNEL, sink.maxPcmChannels, "downmixed to the sink's ${sink.maxPcmChannels}-ch PCM limit")
    }
    return AudioRoute(track, AudioPath.STEREO, 2, "sink accepts only stereo PCM")
}

/**
 * Picks the best track among those in one language: highest path first, then object audio,
 * then lossless, then channel count. A TrueHD Atmos file with an E-AC-3 JOC companion track
 * picks the companion on a Dolby Digital Plus-only soundbar, so Atmos survives.
 */
fun pickAudioTrack(tracks: List<AudioTrackInfo>, sink: SinkCapabilities, allowTranscode: Boolean = true): AudioRoute? =
    tracks.map { routeAudio(it, sink, allowTranscode) }.maxWithOrNull(
        compareBy<AudioRoute>(
            { -it.path.ordinal },
            { if (it.path == AudioPath.BITSTREAM && it.track.codec.objectBased) 1 else 0 },
            { if (it.path != AudioPath.STEREO && it.track.codec.lossless) 1 else 0 },
            { it.outputChannels },
        ),
    )

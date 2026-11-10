package com.amqatar.marquee.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioRouteTest {
    private val dolbyOnlyBar = SinkCapabilities(setOf(AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.EAC3_JOC), maxPcmChannels = 2)
    private val fullBar = SinkCapabilities(
        setOf(AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.TRUEHD, AudioCodec.DTS, AudioCodec.DTS_HD_MA),
        maxPcmChannels = 8,
    )

    @Test fun bitstreamsWhatTheSinkTakes() {
        val r = routeAudio(AudioTrackInfo("1", AudioCodec.EAC3_JOC, 6), dolbyOnlyBar)
        assertEquals(AudioPath.BITSTREAM, r.path)
    }

    @Test fun objectFormatsRideOnTheirBaseCodec() {
        assertEquals(AudioPath.BITSTREAM, routeAudio(AudioTrackInfo("1", AudioCodec.TRUEHD_ATMOS, 8), fullBar).path)
        assertEquals(AudioPath.BITSTREAM, routeAudio(AudioTrackInfo("1", AudioCodec.DTS_X, 8), fullBar).path)
    }

    @Test fun dtsOnDolbyOnlyBarWithoutMultichannelPcmIsTranscoded() {
        val r = routeAudio(AudioTrackInfo("1", AudioCodec.DTS_HD_MA, 8), dolbyOnlyBar)
        assertEquals(AudioPath.AC3_TRANSCODE, r.path)
        assertEquals(6, r.outputChannels)
    }

    @Test fun multichannelPcmPreferredOverTranscode() {
        val sink = SinkCapabilities(setOf(AudioCodec.AC3), maxPcmChannels = 8)
        assertEquals(AudioPath.PCM_MULTICHANNEL, routeAudio(AudioTrackInfo("1", AudioCodec.FLAC, 8), sink).path)
    }

    @Test fun stereoSourceNeverTranscodes() {
        assertEquals(AudioPath.PCM_MULTICHANNEL, routeAudio(AudioTrackInfo("1", AudioCodec.AAC, 2), dolbyOnlyBar).path)
    }

    @Test fun transcodeDisabledFallsToStereo() {
        val r = routeAudio(AudioTrackInfo("1", AudioCodec.DTS, 6), dolbyOnlyBar, allowTranscode = false)
        assertEquals(AudioPath.STEREO, r.path)
    }

    @Test fun picksCompanionTrackSoAtmosSurvives() {
        val tracks = listOf(
            AudioTrackInfo("truehd", AudioCodec.TRUEHD_ATMOS, 8),
            AudioTrackInfo("ddp", AudioCodec.EAC3_JOC, 6),
            AudioTrackInfo("ac3", AudioCodec.AC3, 6),
        )
        assertEquals("ddp", pickAudioTrack(tracks, dolbyOnlyBar)?.track?.id)
        assertEquals("truehd", pickAudioTrack(tracks, fullBar)?.track?.id)
    }
}

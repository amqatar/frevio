package com.amqatar.marquee.tv

import android.content.Context

enum class Engine(val label: String) {
    MEDIA3("Media3"),
    MPV_HW("mpv HW"),
    MPV_SW("mpv SW"),
}

enum class FrameRateMode(val label: String) {
    OFF("Off"),
    SEAMLESS("Seamless only"),
    SWITCH("Switch display mode"),
}

enum class MpvAudio(val label: String) {
    DECODE("Decode"),
    PASSTHROUGH("Passthrough"),
    AC3_TRANSCODE("To Dolby Digital 5.1"),
}

data class TestSettings(
    val baseUrl: String = "",
    val tunneling: Boolean = false,
    val frameRate: FrameRateMode = FrameRateMode.SWITCH,
    val mpvAudio: MpvAudio = MpvAudio.PASSTHROUGH,
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("baseUrl", baseUrl)
            .putBoolean("tunneling", tunneling)
            .putString("frameRate", frameRate.name)
            .putString("mpvAudio", mpvAudio.name)
            .apply()
    }

    companion object {
        private const val PREFS = "player-test"

        fun load(context: Context): TestSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun <E : Enum<E>> enumOr(values: Array<E>, key: String, fallback: E) =
                values.firstOrNull { it.name == p.getString(key, null) } ?: fallback
            return TestSettings(
                baseUrl = p.getString("baseUrl", "").orEmpty(),
                tunneling = p.getBoolean("tunneling", false),
                frameRate = enumOr(FrameRateMode.entries.toTypedArray(), "frameRate", FrameRateMode.SWITCH),
                mpvAudio = enumOr(MpvAudio.entries.toTypedArray(), "mpvAudio", MpvAudio.PASSTHROUGH),
            )
        }
    }
}

fun <E : Enum<E>> E.next(values: List<E>): E = values[(values.indexOf(this) + 1) % values.size]

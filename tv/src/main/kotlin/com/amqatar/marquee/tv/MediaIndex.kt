package com.amqatar.marquee.tv

import com.amqatar.marquee.shared.MarqueeJson
import com.amqatar.marquee.shared.TestMediaIndex
import com.amqatar.marquee.shared.TestMediaItem
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Loads the test list from a plain HTTP server on the LAN (e.g. `python3 -m http.server` run in
 * the test-media folder). Uses `index.json` when present, otherwise the server's directory listing.
 */
object MediaIndex {
    private val mediaExt = Regex("""\.(mkv|mp4|m4v|mov|webm|ts|m2ts|mka|m4a|mp3|flac|wav|ac3|eac3|ec3|dts|thd|mlp|opus|ogg|m3u8|mpd)$""", RegexOption.IGNORE_CASE)

    fun normalise(base: String): String {
        var b = base.trim()
        if (b.isEmpty()) return b
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "http://$b"
        return if (b.endsWith("/")) b else "$b/"
    }

    fun resolve(base: String, file: String): String = URI(normalise(base)).resolve(file.replace(" ", "%20")).toString()

    fun load(base: String): List<TestMediaItem> {
        val root = normalise(base)
        fetch(root + "index.json")?.let { json ->
            return MarqueeJson.decodeFromString(TestMediaIndex.serializer(), json).items
        }
        val html = fetch(root) ?: error("Nothing answered at $root")
        return Regex("""href="([^"?#]+)"""").findAll(html)
            .map { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }
            .filter { mediaExt.containsMatchIn(it) }
            .distinct()
            .map { TestMediaItem(file = it, title = it.substringAfterLast('/'), group = "Files") }
            .toList()
    }

    private fun fetch(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 10000
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }
}

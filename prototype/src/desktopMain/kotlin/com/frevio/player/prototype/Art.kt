package com.frevio.player.prototype

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale

/** Artwork bundled at build time by tools/fetch-open-movie-art.py (absent = placeholders). */
object Art {
    data class Credit(val id: String, val file: String, val author: String, val license: String, val page: String)

    private val cache = HashMap<String, ImageBitmap?>()

    fun get(name: String?): ImageBitmap? {
        if (name == null) return null
        return cache.getOrPut(name) {
            runCatching {
                Art::class.java.getResourceAsStream("/art/$name")?.use { s ->
                    org.jetbrains.skia.Image.makeFromEncoded(s.readBytes()).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    fun has(name: String?): Boolean = get(name) != null

    val credits: List<Credit> by lazy {
        runCatching {
            Art::class.java.getResourceAsStream("/art/credits.txt")?.bufferedReader()?.readLines().orEmpty()
                .mapNotNull { line -> line.split('\t').takeIf { it.size >= 5 }?.let { Credit(it[0], it[1], it[2], it[3], it[4]) } }
        }.getOrDefault(emptyList())
    }
}

@Composable
fun ArtImage(name: String?, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val bitmap = Art.get(name) ?: return
    Image(bitmap, null, modifier, contentScale = ContentScale.Crop, alpha = alpha)
}

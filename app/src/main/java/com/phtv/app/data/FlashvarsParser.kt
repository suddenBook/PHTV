package com.phtv.app.data

import android.util.Log
import com.phtv.app.core.model.StreamSource
import com.phtv.app.core.model.VideoStreams
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Extracts the `flashvars_<id>` object embedded in a watch page and turns its `mediaDefinitions`
 * into a sorted list of playable HLS sources. URLs are signed and short-lived — resolve fresh
 * immediately before playback.
 */
object FlashvarsParser {
    private const val TAG = "PHTV-FV"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(html: String): VideoStreams {
        val raw = extractObject(html)
        if (raw == null) {
            Log.e(TAG, "flashvars object NOT found (html ${html.length} chars)")
            throw IOException("flashvars object not found on page")
        }
        val fv = json.decodeFromString(Flashvars.serializer(), raw)
        val sources = fv.mediaDefinitions
            .filter { it.format.equals("hls", ignoreCase = true) && it.videoUrl.isNotBlank() }
            .mapNotNull { def ->
                val height = def.heightOrNull() ?: return@mapNotNull null
                StreamSource(qualityLabel = "${height}p", height = height, url = def.videoUrl)
            }
            .distinctBy { it.height }
            .sortedByDescending { it.height }
        Log.d(TAG, "mediaDefs=${fv.mediaDefinitions.size} hlsSources=${sources.size} qualities=${sources.map { it.height }}")
        return VideoStreams(
            title = fv.video_title.orEmpty(),
            durationSeconds = fv.video_duration ?: 0,
            posterUrl = fv.image_url.orEmpty(),
            sources = sources,
        )
    }

    /** Brace-balanced extraction so nested objects / strings containing braces don't truncate us. */
    private fun extractObject(html: String): String? {
        val anchor = Regex("""var\s+flashvars_\d+\s*=\s*""").find(html) ?: return null
        val start = html.indexOf('{', anchor.range.last)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < html.length) {
            val ch = html[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return html.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }
}

@Serializable
private data class Flashvars(
    val video_title: String? = null,
    val video_duration: Int? = null,
    val image_url: String? = null,
    val mediaDefinitions: List<MediaDef> = emptyList(),
)

@Serializable
private data class MediaDef(
    val format: String = "",
    val videoUrl: String = "",
    val quality: JsonElement? = null,
) {
    fun heightOrNull(): Int? = when (val q = quality) {
        is JsonPrimitive -> q.contentOrNull?.toIntOrNull()
        is JsonArray -> q.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.maxOrNull()
        else -> null
    }
}

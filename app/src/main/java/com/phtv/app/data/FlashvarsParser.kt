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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Extracts playable sources for a watch page.
 *  - [parse] reads the inline `flashvars_<id>` object: per-quality HLS masters + the `get_media` URL.
 *  - [parseGetMedia] reads the JSON from that `get_media` endpoint: more HLS + progressive MP4 variants.
 * URLs are signed and short-lived, so resolve fresh immediately before playback.
 */
object FlashvarsParser {
    private const val TAG = "PHTV-FV"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class Parsed(val streams: VideoStreams, val getMediaUrl: String?)

    fun parse(html: String): Parsed {
        val raw = extractObject(html)
        if (raw == null) {
            Log.e(TAG, "flashvars object NOT found (html ${html.length} chars)")
            throw IOException("flashvars object not found on page")
        }
        val fv = json.decodeFromString(Flashvars.serializer(), raw)
        val hls = fv.mediaDefinitions
            .filter { it.format.equals("hls", ignoreCase = true) && it.videoUrl.isNotBlank() }
            .mapNotNull { def -> def.heightOrNull()?.let { h -> StreamSource("${h}p", h, def.videoUrl, isHls = true) } }
            .distinctBy { it.height }
            .sortedByDescending { it.height }
        val getMediaUrl = fv.mediaDefinitions.firstOrNull { it.videoUrl.contains("get_media") }?.videoUrl
        Log.d(TAG, "flashvars hls=${hls.size} getMedia=${getMediaUrl != null}")
        return Parsed(
            VideoStreams(
                title = fv.video_title.orEmpty(),
                durationSeconds = fv.video_duration ?: 0,
                posterUrl = fv.image_url.orEmpty(),
                sources = hls,
            ),
            getMediaUrl,
        )
    }

    /** Parse the JSON array returned by the `get_media` endpoint into HLS + MP4 sources. */
    fun parseGetMedia(body: String): List<StreamSource> {
        val arr = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        val out = mutableListOf<StreamSource>()
        for (el in arr) {
            val obj = runCatching { el.jsonObject }.getOrNull() ?: continue
            val url = obj["videoUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (url.isBlank() || url.contains("get_media")) continue
            val format = obj["format"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val height = obj["quality"]?.heightOrNull() ?: continue
            val isHls = format.equals("hls", ignoreCase = true) || url.contains(".m3u8")
            out += StreamSource("${height}p", height, url, isHls)
        }
        Log.d(TAG, "get_media sources=${out.size}")
        return out
    }

    private fun JsonElement.heightOrNull(): Int? = when (this) {
        is JsonPrimitive -> contentOrNull?.toIntOrNull()
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.maxOrNull()
        else -> null
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

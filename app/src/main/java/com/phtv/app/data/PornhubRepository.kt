package com.phtv.app.data

import android.util.Log
import com.phtv.app.core.model.Categories
import com.phtv.app.core.model.Category
import com.phtv.app.core.model.Orientation
import com.phtv.app.core.model.SortOrder
import com.phtv.app.core.model.Video
import com.phtv.app.core.model.VideoStreams
import com.phtv.app.core.network.PhHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Single entry point for all PornHub data access. Methods are main-safe (switch to IO internally). */
class PornhubRepository {

    suspend fun home(orientation: Orientation, page: Int = 1, sort: SortOrder = SortOrder.HOT): List<Video> =
        listing(orientation.homePath(sort.param, page))

    suspend fun category(orientation: Orientation, id: String, page: Int = 1, sort: SortOrder = SortOrder.HOT): List<Video> =
        listing(orientation.categoryPath(id, sort.param, page))

    suspend fun search(orientation: Orientation, query: String, page: Int = 1): List<Video> =
        listing(orientation.searchPath(URLEncoder.encode(query, "UTF-8"), page))

    /** Category taxonomy for an orientation (straight uses the bundled list; others are scraped live). */
    suspend fun categories(orientation: Orientation): List<Category> = withContext(Dispatchers.IO) {
        if (orientation == Orientation.STRAIGHT) return@withContext Categories.ALL
        val path = orientation.categoriesPath ?: return@withContext emptyList()
        val parsed = HtmlParser.parseCategories(PhHttp.getText(PhHttp.BASE + path, useCache = true))
        parsed.ifEmpty { if (orientation == Orientation.STRAIGHT) Categories.ALL else parsed }
    }

    /** Resolve fresh playback URLs right before playing; PornHub stream URLs are signed and expire. */
    suspend fun resolveStreams(viewkey: String): VideoStreams = withContext(Dispatchers.IO) {
        try {
            val html = PhHttp.getText("${PhHttp.BASE}/view_video.php?viewkey=$viewkey")
            val parsed = FlashvarsParser.parse(html)
            var sources = parsed.streams.sources
            // Augment with the get_media endpoint (fresh URLs + progressive MP4 fallbacks).
            parsed.getMediaUrl?.let { url ->
                runCatching { FlashvarsParser.parseGetMedia(PhHttp.getText(url, xhr = true)) }
                    .onSuccess { extra -> if (extra.isNotEmpty()) sources = (sources + extra).distinctBy { it.url } }
                    .onFailure { Log.w(TAG, "get_media failed: ${it.message}") }
            }
            // Prefer HLS (adaptive) first, then progressive MP4; highest quality first within each.
            sources = sources.sortedWith(compareByDescending<com.phtv.app.core.model.StreamSource> { it.isHls }.thenByDescending { it.height })
            Log.d(TAG, "resolve $viewkey -> ${sources.size} sources best=${sources.firstOrNull()?.qualityLabel}")
            parsed.streams.copy(sources = sources)
        } catch (t: Throwable) {
            Log.e(TAG, "resolve $viewkey FAILED: ${t.message}", t)
            throw t
        }
    }

    private suspend fun listing(path: String): List<Video> = withContext(Dispatchers.IO) {
        val url = PhHttp.BASE + path
        try {
            val videos = HtmlParser.parseVideoList(PhHttp.getText(url, useCache = true))
            Log.d(TAG, "listing $path -> ${videos.size} videos")
            videos
        } catch (t: Throwable) {
            Log.e(TAG, "listing $path FAILED: ${t.message}", t)
            throw t
        }
    }

    private companion object {
        const val TAG = "PHTV-REPO"
    }
}

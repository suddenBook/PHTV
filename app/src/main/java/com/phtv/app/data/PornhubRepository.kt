package com.phtv.app.data

import android.util.Log
import com.phtv.app.core.model.SortOrder
import com.phtv.app.core.model.Video
import com.phtv.app.core.model.VideoStreams
import com.phtv.app.core.network.PhHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Single entry point for all PornHub data access. Methods are main-safe (switch to IO internally). */
class PornhubRepository {

    suspend fun home(page: Int = 1, sort: SortOrder = SortOrder.HOT): List<Video> =
        listing("/video?o=${sort.param}&page=$page")

    suspend fun category(id: String, page: Int = 1, sort: SortOrder = SortOrder.HOT): List<Video> =
        listing("/video?c=$id&o=${sort.param}&page=$page")

    suspend fun search(query: String, page: Int = 1): List<Video> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return listing("/video/search?search=$encoded&page=$page")
    }

    /** Resolve fresh playback URLs right before playing; PornHub stream URLs are signed and expire. */
    suspend fun resolveStreams(viewkey: String): VideoStreams = withContext(Dispatchers.IO) {
        try {
            val html = PhHttp.getText("${PhHttp.BASE}/view_video.php?viewkey=$viewkey")
            val streams = FlashvarsParser.parse(html)
            Log.d(TAG, "resolve $viewkey -> ${streams.sources.size} sources best=${streams.best?.qualityLabel}")
            streams
        } catch (t: Throwable) {
            Log.e(TAG, "resolve $viewkey FAILED: ${t.message}", t)
            throw t
        }
    }

    private suspend fun listing(path: String): List<Video> = withContext(Dispatchers.IO) {
        val url = PhHttp.BASE + path
        try {
            val html = PhHttp.getText(url)
            val videos = HtmlParser.parseVideoList(html)
            Log.d(TAG, "listing $path -> ${videos.size} videos (html ${html.length} chars)")
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

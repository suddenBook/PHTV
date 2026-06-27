package com.phtv.app.data

import android.util.Log
import com.phtv.app.core.model.Video
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses PornHub listing pages (home / category / search) into [Video] cards. */
object HtmlParser {
    fun parseVideoList(html: String): List<Video> {
        val cards = Jsoup.parse(html).select("li.pcVideoListItem")
        val videos = cards.mapNotNull(::parseCard)
        Log.d("PHTV-PARSE", "cards=${cards.size} parsed=${videos.size}")
        return videos
    }

    private fun parseCard(el: Element): Video? {
        val link = el.selectFirst("a[href*=viewkey]") ?: return null
        val viewkey = Regex("viewkey=([a-zA-Z0-9]+)").find(link.attr("href"))
            ?.groupValues?.get(1) ?: return null

        val img = el.selectFirst("img")
        // `src` is always a real URL on PornHub cards; data-mediumthumb/data-image are higher quality.
        val thumb = sequenceOf("data-mediumthumb", "data-image", "src", "data-thumb_url", "data-src")
            .map { attr -> img?.attr(attr).orEmpty() }
            .firstOrNull { it.startsWith("http") }
            .orEmpty()

        // Title can live in the visible .title, the img alt, or data-title depending on card type.
        val title = listOf(
            el.selectFirst(".title a")?.text().orEmpty(),
            img?.attr("alt").orEmpty(),
            img?.attr("data-title").orEmpty(),
            link.attr("title"),
        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (title.isBlank()) return null

        val views = (el.selectFirst(".views var") ?: el.selectFirst(".views"))
            ?.text()?.trim().orEmpty()

        return Video(
            viewkey = viewkey,
            title = title,
            thumbUrl = thumb,
            previewUrl = img?.attr("data-mediabook").orEmpty(),
            durationText = el.selectFirst(".duration")?.text()?.trim().orEmpty(),
            viewsText = views,
            ratingText = el.selectFirst(".value")?.text()?.trim().orEmpty(),
            uploader = el.selectFirst(".usernameWrap a, .userInfoBlock a")?.text()?.trim().orEmpty(),
        )
    }
}

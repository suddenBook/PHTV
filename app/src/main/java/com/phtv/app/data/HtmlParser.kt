package com.phtv.app.data

import android.util.Log
import com.phtv.app.core.model.Category
import com.phtv.app.core.model.Video
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses PornHub listing & category pages into models. */
object HtmlParser {
    private val viewkeyRegex = Regex("viewkey=([a-zA-Z0-9]+)")
    private val categoryIdRegex = Regex("[?&]c=(\\d+)")
    private val trailingCount = Regex("\\s*[\\d,]+(\\s*Videos)?$", RegexOption.IGNORE_CASE)

    fun parseVideoList(html: String): List<Video> {
        val cards = Jsoup.parse(html).select("li.pcVideoListItem")
        val videos = cards.mapNotNull(::parseCard)
        Log.d("PHTV-PARSE", "cards=${cards.size} parsed=${videos.size}")
        return videos
    }

    /** Parses a /categories page (any orientation) into id→name [Category] list. */
    fun parseCategories(html: String): List<Category> {
        val map = LinkedHashMap<String, String>()
        Jsoup.parse(html).select("a[href]").forEach { a ->
            val id = categoryIdRegex.find(a.attr("href"))?.groupValues?.get(1) ?: return@forEach
            var name = a.text().ifBlank { a.attr("data-category") }.trim().replace(trailingCount, "").trim()
            if (name.length in 1..40 && id !in map) map[id] = name
        }
        Log.d("PHTV-PARSE", "categories parsed=${map.size}")
        return map.map { (id, name) -> Category(id, name) }
    }

    private fun parseCard(el: Element): Video? {
        val link = el.selectFirst("a[href*=viewkey]") ?: return null
        val viewkey = viewkeyRegex.find(link.attr("href"))?.groupValues?.get(1) ?: return null

        val img = el.selectFirst("img")
        val thumb = sequenceOf("data-mediumthumb", "data-image", "src", "data-thumb_url", "data-src")
            .map { attr -> img?.attr(attr).orEmpty() }
            .firstOrNull { it.startsWith("http") }
            .orEmpty()

        val title = listOf(
            el.selectFirst(".title a")?.text().orEmpty(),
            img?.attr("alt").orEmpty(),
            img?.attr("data-title").orEmpty(),
            link.attr("title"),
        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (title.isBlank()) return null

        val views = (el.selectFirst(".views var") ?: el.selectFirst(".views"))?.text()?.trim().orEmpty()

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

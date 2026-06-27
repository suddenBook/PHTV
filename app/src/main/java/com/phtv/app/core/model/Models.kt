package com.phtv.app.core.model

/** A video as shown in a browse/search grid (parsed from listing HTML). */
data class Video(
    val viewkey: String,
    val title: String,
    val thumbUrl: String,
    val previewUrl: String = "",
    val durationText: String = "",
    val viewsText: String = "",
    val ratingText: String = "",
    val uploader: String = "",
)

/** A single playable quality variant of a video. */
data class StreamSource(
    val qualityLabel: String,
    val height: Int,
    val url: String,
)

/** Resolved playback info for one video (from flashvars on the watch page). */
data class VideoStreams(
    val title: String,
    val durationSeconds: Int,
    val posterUrl: String,
    /** Sorted highest-quality first. */
    val sources: List<StreamSource>,
) {
    val best: StreamSource? get() = sources.firstOrNull()
}

/** Sort orders supported by PornHub listing pages (the `o=` query param). */
enum class SortOrder(val param: String, val label: String) {
    HOT("ht", "Hottest"),
    MOST_VIEWED("mv", "Most Viewed"),
    TOP_RATED("tr", "Top Rated"),
    NEWEST("cm", "Newest"),
}

/**
 * Sexual-orientation segment. Each maps to a different PornHub URL space, and (the watch page aside)
 * listings, categories and search all live under that segment's prefix. Verified 2026-06-28:
 *  - straight: /video, /categories
 *  - gay:      /gay/video, /gay/categories
 *  - lesbian:  /lesbian/video, /lesbian/categories
 *  - trans:    /transgender (query-param based), no /video sub-path
 */
enum class Orientation(
    val label: String,
    val glyph: String,
    private val listBase: String,
    private val searchBase: String,
    val categoriesPath: String?,
) {
    STRAIGHT("Straight", "⚤", "/video", "/video/search", "/categories"),
    GAY("Gay", "⚣", "/gay/video", "/gay/video/search", "/gay/categories"),
    LESBIAN("Lesbian", "⚢", "/lesbian/video", "/lesbian/video/search", "/lesbian/categories"),
    TRANS("Trans", "⚧", "/transgender", "/transgender/video/search", "/transgender/categories");

    fun homePath(sortParam: String, page: Int) = "$listBase?o=$sortParam&page=$page"
    fun categoryPath(id: String, sortParam: String, page: Int) = "$listBase?c=$id&o=$sortParam&page=$page"
    fun searchPath(encodedQuery: String, page: Int) = "$searchBase?search=$encodedQuery&page=$page"
}

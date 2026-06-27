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

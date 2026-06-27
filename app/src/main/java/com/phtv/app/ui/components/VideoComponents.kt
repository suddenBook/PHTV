@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.phtv.app.core.model.Video
import kotlinx.coroutines.launch

/** A focusable video card: thumbnail with duration overlay + title. */
@Composable
fun VideoCard(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = video.thumbUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/**
 * A paginated, focusable grid of videos. [feedKey] scopes the internal state so switching feeds
 * (home → category → search) resets cleanly. [fetchPage] loads page N (1-based).
 */
@Composable
fun VideoFeed(
    feedKey: Any,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 5,
    fetchPage: suspend (Int) -> List<Video>,
) {
    val items = remember(feedKey) { mutableStateListOf<Video>() }
    val seen = remember(feedKey) { mutableSetOf<String>() }
    var page by remember(feedKey) { mutableIntStateOf(0) }
    var loading by remember(feedKey) { mutableStateOf(false) }
    var endReached by remember(feedKey) { mutableStateOf(false) }
    var error by remember(feedKey) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fetch by rememberUpdatedState(fetchPage)

    fun loadNext() {
        if (loading || endReached) return
        loading = true
        scope.launch {
            try {
                val next = fetch(page + 1)
                val fresh = next.filter { seen.add(it.viewkey) }
                if (fresh.isEmpty()) endReached = true else { items.addAll(fresh); page += 1 }
                error = null
            } catch (t: Throwable) {
                error = t.message ?: "Failed to load"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(feedKey) { if (items.isEmpty()) loadNext() }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (last >= 0 && last >= items.size - 6) loadNext() }
    }

    Box(modifier.fillMaxSize()) {
        when {
            items.isEmpty() && loading -> Text("Loading…", Modifier.align(Alignment.Center))
            items.isEmpty() && error != null -> Text("Error: $error", Modifier.align(Alignment.Center))
            items.isEmpty() -> Text("Nothing here yet", Modifier.align(Alignment.Center))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = items, key = { it.viewkey }) { video ->
                    VideoCard(video = video, onClick = { onPlay(video.viewkey) })
                }
            }
        }
    }
}

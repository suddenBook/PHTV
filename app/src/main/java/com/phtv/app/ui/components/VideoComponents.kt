@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.phtv.app.core.model.Video
import kotlinx.coroutines.launch

private val Accent = Color(0xFFFF9000)

/** A focusable video card: large thumbnail with duration overlay + readable 2-line title. */
@Composable
fun VideoCard(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF2A2A2A))) {
            AsyncImage(
                model = video.thumbUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/**
 * A paginated, focusable grid of videos. [feedKey] scopes internal state so switching feeds resets
 * cleanly. Loading/empty/error states are focusable so D-pad focus never falls out to the sidebar,
 * and focus hands off to the first card once results arrive.
 */
@Composable
fun VideoFeed(
    feedKey: Any,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    fetchPage: suspend (Int) -> List<Video>,
) {
    val items = remember(feedKey) { mutableStateListOf<Video>() }
    val seen = remember(feedKey) { mutableSetOf<String>() }
    var page by remember(feedKey) { mutableIntStateOf(0) }
    var loading by remember(feedKey) { mutableStateOf(false) }
    var endReached by remember(feedKey) { mutableStateOf(false) }
    var error by remember(feedKey) { mutableStateOf<String?>(null) }
    var feedHasFocus by remember(feedKey) { mutableStateOf(false) }
    val firstItem = remember(feedKey) { FocusRequester() }
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

    fun reload() {
        items.clear(); seen.clear(); page = 0; endReached = false; error = null
        loadNext()
    }

    LaunchedEffect(feedKey) { if (items.isEmpty()) loadNext() }
    // Hand focus to the first card once results arrive (only if the feed already holds focus).
    LaunchedEffect(items.isEmpty()) {
        if (items.isNotEmpty() && feedHasFocus) runCatching { firstItem.requestFocus() }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, items.size, endReached) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (last >= 0 && last >= items.size - 6) loadNext() }
    }

    Box(modifier.fillMaxSize().onFocusChanged { feedHasFocus = it.hasFocus }) {
        when {
            items.isEmpty() && loading -> StatusBox(spinner = true, text = "Loading videos…")
            items.isEmpty() && error != null -> RetryBox(message = error!!, onRetry = { reload() })
            items.isEmpty() -> StatusBox(spinner = false, text = "No videos here")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(28.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items = items, key = { _, v -> v.viewkey }) { index, video ->
                    VideoCard(
                        video = video,
                        onClick = { onPlay(video.viewkey) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                    )
                }
                if (loading || error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            if (error != null) {
                                Text("Couldn't load more — scroll up to retry", color = Color.White.copy(alpha = 0.7f))
                            } else {
                                CircularProgressIndicator(color = Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Focusable so the content region keeps D-pad focus while loading (never escapes to the sidebar). */
@Composable
private fun StatusBox(spinner: Boolean, text: String) {
    Column(
        Modifier.fillMaxSize().focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (spinner) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
        }
        Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RetryBox(message: String, onRetry: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Couldn't load", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text("Retry") }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

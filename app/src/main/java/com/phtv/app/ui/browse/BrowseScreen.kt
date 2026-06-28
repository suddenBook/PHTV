@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.browse

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.phtv.app.core.model.Category
import com.phtv.app.core.model.Orientation
import com.phtv.app.core.model.SortOrder
import com.phtv.app.data.PornhubRepository
import com.phtv.app.ui.components.VideoFeed
import com.phtv.app.ui.player.PlayerScreen

private val RAIL_COLLAPSED = 56.dp
private val RAIL_EXPANDED = 248.dp
private val Accent = Color(0xFFFF9000)

private enum class Section(val label: String, val glyph: String) {
    HOME("Home", "H"), CATEGORIES("Categories", "C"), SEARCH("Search", "S")
}

/**
 * Browse shell. A collapsible overlay rail with an orientation switch sits over the content.
 * Focus stays in the content by default; the rail expands only when the user navigates LEFT into it,
 * and collapses again when a section is chosen or Back is pressed.
 */
@Composable
fun BrowseScreen(initialViewkey: String? = null) {
    var orientationIndex by rememberSaveable { mutableIntStateOf(0) }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val orientation = Orientation.entries[orientationIndex]
    val section = Section.entries[sectionIndex]

    var railFocused by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(if (railFocused) RAIL_EXPANDED else RAIL_COLLAPSED, label = "rail")
    val contentFocus = remember { FocusRequester() }
    val repo = remember { PornhubRepository() }
    var focusTrigger by remember { mutableIntStateOf(0) }
    // The player is an overlay on top of this (preserved) screen, so returning is instant and keeps place.
    var playing by rememberSaveable { mutableStateOf(initialViewkey) }
    var showExit by remember { mutableStateOf(false) }
    val onPlay: (String) -> Unit = { playing = it }
    val context = LocalContext.current

    // Move focus into content on launch, on return from the player, and after picking a section.
    LaunchedEffect(focusTrigger, sectionIndex) { runCatching { contentFocus.requestFocus() } }
    // Lowest-priority Back at the browse root → confirm before leaving. Handlers composed later or in
    // children take precedence while enabled (rail collapse, category back, the player, the dialog).
    BackHandler(enabled = playing == null && !railFocused && !showExit) { showExit = true }
    // While the rail is open, the first Back collapses it (returns focus to content) instead of exiting.
    BackHandler(enabled = railFocused) { runCatching { contentFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = RAIL_COLLAPSED)
                .focusRequester(contentFocus)
                .focusGroup(),
        ) {
            when (section) {
                Section.HOME -> HomeSection(repo, orientation, onPlay)
                Section.CATEGORIES -> CategoriesSection(repo, orientation, onPlay, requestContentFocus = { focusTrigger++ })
                Section.SEARCH -> SearchSection(repo, orientation, onPlay)
            }
        }
        if (railFocused) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }
        NavRail(
            expanded = railFocused,
            orientation = orientation,
            onCycleOrientation = { orientationIndex = (orientationIndex + 1) % Orientation.entries.size },
            selected = section,
            onSelectSection = { sectionIndex = it.ordinal; focusTrigger++ },
            modifier = Modifier
                .fillMaxHeight()
                .width(railWidth)
                .onFocusChanged { railFocused = it.hasFocus },
        )

        // Fullscreen player overlay — browse stays composed beneath it (no reload on Back).
        playing?.let { vk ->
            PlayerScreen(viewkey = vk, onBack = { playing = null; focusTrigger++ })
        }

        // Exit confirmation — topmost overlay so its Back/focus take precedence.
        if (showExit) {
            ExitConfirm(
                appName = "PH TV",
                onConfirm = { (context as? Activity)?.finish() },
                onDismiss = { showExit = false },
            )
        }
    }
}

@Composable
private fun NavRail(
    expanded: Boolean,
    orientation: Orientation,
    onCycleOrientation: () -> Unit,
    selected: Section,
    onSelectSection: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(Color(0xFF1A1A1A)).padding(vertical = 24.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (expanded) "PH TV" else "PH",
            style = MaterialTheme.typography.titleMedium,
            color = Accent,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        // Orientation switch — cycles Straight → Gay → Lesbian → Trans (stays on the rail).
        RailItem(
            glyph = orientation.glyph,
            label = if (expanded) "Orientation: ${orientation.label}" else orientation.label,
            contentColor = Accent,
            expanded = expanded,
            onClick = onCycleOrientation,
        )
        Spacer(Modifier.height(16.dp))
        Section.entries.forEach { item ->
            RailItem(
                glyph = item.glyph,
                label = item.label,
                contentColor = if (item == selected) Accent else Color.White,
                expanded = expanded,
                onClick = { onSelectSection(item) },
            )
        }
    }
}

@Composable
private fun RailItem(
    glyph: String,
    label: String,
    contentColor: Color,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(glyph, style = MaterialTheme.typography.titleMedium, color = contentColor)
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = contentColor, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeSection(repo: PornhubRepository, orientation: Orientation, onPlay: (String) -> Unit) {
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    val sort = SortOrder.entries[sortIndex]
    Column(Modifier.fillMaxSize()) {
        SortBar(selected = sort, onSelect = { sortIndex = it.ordinal })
        VideoFeed(
            feedKey = "home:${orientation.name}:${sort.param}",
            onPlay = onPlay,
            modifier = Modifier.weight(1f),
        ) { repo.home(orientation, it, sort) }
    }
}

@Composable
private fun SortBar(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, top = 18.dp, end = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sort:", color = Color.White.copy(alpha = 0.7f))
        SortOrder.entries.forEach { order ->
            Surface(onClick = { onSelect(order) }) {
                Text(
                    order.label,
                    color = if (order == selected) Accent else Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoriesSection(
    repo: PornhubRepository,
    orientation: Orientation,
    onPlay: (String) -> Unit,
    requestContentFocus: () -> Unit,
) {
    var selectedId by rememberSaveable(orientation) { mutableStateOf<String?>(null) }
    var cats by remember(orientation) { mutableStateOf<List<Category>?>(null) }
    var catError by remember(orientation) { mutableStateOf<String?>(null) }
    var reloadKey by remember(orientation) { mutableIntStateOf(0) }

    LaunchedEffect(orientation, reloadKey) {
        cats = null; catError = null
        try {
            cats = repo.categories(orientation)
        } catch (t: Throwable) {
            catError = t.message ?: "Failed to load categories"
        }
    }

    val selected = cats?.find { it.id == selectedId }
    BackHandler(enabled = selectedId != null) { selectedId = null; requestContentFocus() }

    when {
        selectedId != null && selected != null -> {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "‹  ${selected.name}",
                    color = Accent,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 4.dp),
                )
                VideoFeed(
                    feedKey = "cat:${orientation.name}:${selected.id}",
                    onPlay = onPlay,
                    modifier = Modifier.weight(1f),
                ) { repo.category(orientation, selected.id, it) }
            }
        }
        cats == null && catError == null -> CenterLoading("Loading categories…")
        catError != null -> CenterRetry(catError!!) { reloadKey++ }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = cats.orEmpty(), key = { it.id }) { category ->
                Surface(onClick = { selectedId = category.id; requestContentFocus() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(repo: PornhubRepository, orientation: Orientation, onPlay: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable(orientation) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                )
            }
            Surface(onClick = { if (query.isNotBlank()) submitted = query.trim() }) {
                Text("Search", modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        val q = submitted
        if (q == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type a query and press Search", color = Color.White.copy(alpha = 0.7f))
            }
        } else {
            VideoFeed(feedKey = "search:${orientation.name}:$q", onPlay = onPlay, modifier = Modifier.weight(1f)) {
                repo.search(orientation, q, it)
            }
        }
    }
}

@Composable
private fun CenterLoading(text: String) {
    Column(
        Modifier.fillMaxSize().focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CenterRetry(message: String, onRetry: () -> Unit) {
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

/** Full-screen "are you sure you want to leave?" overlay. Focus starts on Cancel; Back dismisses it. */
@Composable
private fun ExitConfirm(appName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(440.dp).background(Color(0xFF1E1E1E)).padding(28.dp)) {
            Text("Exit $appName?", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Press Exit to leave, or Cancel to keep browsing.", color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onConfirm) { Text("Exit") }
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus)) { Text("Cancel") }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.browse

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.phtv.app.core.model.Categories
import com.phtv.app.core.model.Category
import com.phtv.app.data.PornhubRepository
import com.phtv.app.ui.components.VideoFeed

private enum class Section(val label: String) {
    HOME("Home"), CATEGORIES("Categories"), SEARCH("Search")
}

/** The main browse shell: a left nav rail + the active section's content. */
@Composable
fun BrowseScreen(onPlay: (String) -> Unit) {
    var section by remember { mutableStateOf(Section.HOME) }
    val repo = remember { PornhubRepository() }

    Row(Modifier.fillMaxSize()) {
        NavRail(selected = section, onSelect = { section = it })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                Section.HOME -> VideoFeed(feedKey = "home", onPlay = onPlay) { repo.home(it) }
                Section.CATEGORIES -> CategoriesSection(repo = repo, onPlay = onPlay)
                Section.SEARCH -> SearchSection(repo = repo, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun NavRail(selected: Section, onSelect: (Section) -> Unit) {
    val firstItem = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(210.dp)
            .background(Color(0xFF141414))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "PH TV",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFFF9000),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
        )
        Section.entries.forEachIndexed { index, item ->
            Surface(
                onClick = { onSelect(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(firstItem) else Modifier),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
}

@Composable
private fun CategoriesSection(repo: PornhubRepository, onPlay: (String) -> Unit) {
    var selected by remember { mutableStateOf<Category?>(null) }
    val current = selected
    if (current == null) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = Categories.ALL, key = { it.id }) { category ->
                Surface(onClick = { selected = category }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Surface(onClick = { selected = null }, modifier = Modifier.padding(12.dp)) {
                Text("‹  ${current.name}", modifier = Modifier.padding(12.dp))
            }
            VideoFeed(
                feedKey = "cat:${current.id}",
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            ) { repo.category(current.id, it) }
        }
    }
}

@Composable
private fun SearchSection(repo: PornhubRepository, onPlay: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Surface(onClick = { if (query.isNotBlank()) submitted = query.trim() }) {
                Text("Search", modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        val q = submitted
        if (q == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type a query and press Search")
            }
        } else {
            VideoFeed(feedKey = "search:$q", onPlay = onPlay, modifier = Modifier.weight(1f)) {
                repo.search(q, it)
            }
        }
    }
}

package com.phtv.app.ui

import androidx.compose.runtime.Composable
import com.phtv.app.ui.browse.BrowseScreen

/**
 * App entry composable. The player lives as an overlay inside [BrowseScreen] (not a separate nav
 * destination), so browse state/scroll/focus are preserved while a video plays.
 */
@Composable
fun PhtvNavHost(startViewkey: String? = null) {
    BrowseScreen(initialViewkey = startViewkey)
}

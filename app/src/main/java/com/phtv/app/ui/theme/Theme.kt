package com.phtv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * App theme. TV apps are dark-first (rendered on large displays in dim rooms),
 * so we use a dark color scheme as the baseline.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhtvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}

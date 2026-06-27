package com.phtv.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phtv.app.ui.browse.BrowseScreen
import com.phtv.app.ui.player.PlayerScreen

/** Top-level navigation: the browse shell, and a fullscreen player addressed by viewkey. */
@Composable
fun PhtvNavHost(startViewkey: String? = null, onExitApp: () -> Unit = {}) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (startViewkey != null) "player/$startViewkey" else "browse",
    ) {
        composable("browse") {
            BrowseScreen(onPlay = { viewkey -> nav.navigate("player/$viewkey") })
        }
        composable(
            route = "player/{viewkey}",
            arguments = listOf(navArgument("viewkey") { type = NavType.StringType }),
        ) { entry ->
            PlayerScreen(
                viewkey = entry.arguments?.getString("viewkey").orEmpty(),
                onBack = { if (!nav.popBackStack()) onExitApp() },
            )
        }
    }
}

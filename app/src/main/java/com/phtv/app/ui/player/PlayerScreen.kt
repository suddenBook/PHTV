@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.player

import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.phtv.app.core.model.StreamSource
import com.phtv.app.core.network.PhHttp
import com.phtv.app.data.PornhubRepository

private const val TAG = "PHTV-PLAYER"
private const val SEEK_MS = 10_000L
private val Accent = Color(0xFFFF9000)

/**
 * Fullscreen Media3 player.
 *  - Highest quality first; silently steps down on error.
 *  - Controls stay hidden; LEFT/RIGHT seek instantly without showing controls.
 *  - Back shows controls first; a second Back exits.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewkey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PornhubRepository() }
    var sources by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var controllerVisible by remember { mutableStateOf(false) }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(PhHttp.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "${PhHttp.BASE}/"))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }

    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = true
            controllerAutoShow = false // don't pop controls on key presses / playback
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { vis -> controllerVisible = vis == View.VISIBLE },
            )
            // LEFT/RIGHT seek instantly while controls are hidden; otherwise let the controller handle them.
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || isControllerFullyVisible) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0)); true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        player.seekTo(player.currentPosition + SEEK_MS); true
                    }
                    else -> false
                }
            }
        }
    }

    fun play(index: Int) {
        val source = sources.getOrNull(index) ?: return
        Log.d(TAG, "play index=$index quality=${source.qualityLabel}")
        player.setMediaItem(
            MediaItem.Builder().setUri(source.url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
        )
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                Log.e(TAG, "error=${e.errorCodeName} at index=$currentIndex", e)
                if (currentIndex + 1 < sources.size) {
                    currentIndex += 1
                    Log.w(TAG, "falling back to lower quality -> index=$currentIndex")
                    play(currentIndex)
                } else {
                    error = "Playback failed (${e.errorCodeName})"
                    playerView.useController = false
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "state=$state (2=BUFFERING,3=READY,4=ENDED)")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(viewkey) {
        try {
            Log.d(TAG, "resolve start viewkey=$viewkey")
            val streams = repo.resolveStreams(viewkey)
            sources = streams.sources
            if (sources.isEmpty()) error = "No playable stream found" else { currentIndex = 0; play(0) }
        } catch (t: Throwable) {
            Log.e(TAG, "resolve/play failed for $viewkey", t)
            error = t.message ?: "Failed to load video"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { runCatching { playerView.requestFocus() } }

    // First Back reveals controls; a second Back (controls visible) exits.
    BackHandler {
        if (error == null && !controllerVisible) playerView.showController() else onBack()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        if (loading) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(16.dp))
                Text("Loading video…", color = Color.White)
            }
        }

        error?.let { message ->
            Column(
                Modifier.align(Alignment.Center).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚠  $message", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Press Back to return", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

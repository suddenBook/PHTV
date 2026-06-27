@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.Text
import com.phtv.app.core.network.PhHttp
import com.phtv.app.data.PornhubRepository

private const val TAG = "PHTV-PLAYER"

/** Fullscreen Media3 player. Resolves fresh HLS URLs for [viewkey], then plays the best quality. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewkey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PornhubRepository() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(PhHttp.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "${PhHttp.BASE}/"))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error0: PlaybackException) {
                Log.e(TAG, "onPlayerError code=${error0.errorCodeName} msg=${error0.message}", error0)
                error = "Playback error: ${error0.errorCodeName}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "playbackState=$playbackState (2=BUFFERING,3=READY,4=ENDED)")
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
            val url = streams.best?.url
            Log.d(TAG, "resolved sources=${streams.sources.size} best=${streams.best?.qualityLabel} url=${url?.take(90)}")
            if (url == null) {
                error = "No playable stream found"
            } else {
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                player.setMediaItem(item)
                player.prepare()
                player.playWhenReady = true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "resolve/play failed for $viewkey", t)
            error = t.message ?: "Failed to load video"
        } finally {
            loading = false
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            loading -> Text("Loading…", color = Color.White, modifier = Modifier.align(Alignment.Center))
            error != null -> Text(
                "Error: $error",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

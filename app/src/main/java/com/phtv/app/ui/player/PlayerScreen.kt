@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.phtv.app.ui.player

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.phtv.app.R
import com.phtv.app.core.model.StreamSource
import com.phtv.app.core.network.PhHttp
import com.phtv.app.data.PornhubRepository

private const val TAG = "PHTV-PLAYER"
private const val SEEK_MS = 10_000L
private val Accent = Color(0xFFFF9000)

/**
 * Fullscreen Media3 player. D-pad is handled in Compose (PlayerView is made non-focusable so it can't
 * steal keys to pop its controller):
 *  - LEFT/RIGHT seek ±10s instantly, no controls shown.
 *  - OK toggles play/pause.
 *  - Back reveals the controls (progress bar); a second Back exits.
 * Highest quality first, silently stepping down (HLS → MP4) on error.
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
    val keyFocus = remember { FocusRequester() }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(PhHttp.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "${PhHttp.BASE}/"))
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }

    val playerView = remember {
        // Inflated from XML so the surface is a TextureView (see player_view.xml) — this prevents the
        // transient "stuck stretched" glitch on portrait videos that a SurfaceView can exhibit.
        (LayoutInflater.from(context).inflate(R.layout.player_view, null) as PlayerView).apply {
            this.player = player
            controllerAutoShow = false
            controllerShowTimeoutMs = 4000 // auto-hide controls 4s after they're revealed
            // Pillarbox portrait videos instead of stretching them to fill a 16:9 surface.
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            // We own the D-pad in Compose, so stop the view (and its controls) from consuming keys.
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { vis -> controllerVisible = vis == View.VISIBLE },
            )
        }
    }

    fun play(index: Int) {
        val s = sources.getOrNull(index) ?: return
        Log.d(TAG, "play index=$index quality=${s.qualityLabel} hls=${s.isHls}")
        val item = MediaItem.Builder().setUri(s.url)
        if (s.isHls) item.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(item.build())
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                Log.e(TAG, "error=${e.errorCodeName} at index=$currentIndex", e)
                if (currentIndex + 1 < sources.size) {
                    currentIndex += 1
                    Log.w(TAG, "falling back -> index=$currentIndex")
                    play(currentIndex)
                } else {
                    error = "Can't play this video — it may be geo-restricted, premium, or unavailable."
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

    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }

    // Back dismisses the controls if they're showing; otherwise it leaves the player.
    BackHandler {
        when {
            error != null -> onBack()
            controllerVisible -> playerView.hideController()
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyFocus)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight -> {
                        player.seekTo(player.currentPosition + SEEK_MS); true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        player.playWhenReady = !player.playWhenReady
                        playerView.showController(); true
                    }
                    Key.DirectionUp, Key.DirectionDown -> { playerView.showController(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
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

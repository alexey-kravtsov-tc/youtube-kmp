import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

actual fun getPlatformName(): String = "Android"

actual fun log(message: String) {
    android.util.Log.d("YT_OWN_WAY", message)
}

@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        log("AndroidPlayer: Initializing ExoPlayer for URL: $url")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    log("AndroidPlayer: ExoPlayer ERROR: ${error.message} (ErrorCode: ${error.errorCode})")
                    onError(error.message ?: "Unknown playback error")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    log("AndroidPlayer: Playback state changed to: $stateName")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    log("AndroidPlayer: isPlaying changed to: $isPlaying")
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            log("AndroidPlayer: Releasing ExoPlayer")
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
            }
        },
        modifier = modifier
    )
}

@Composable fun MainView() = App()

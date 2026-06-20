import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController

actual fun getPlatformName(): String = "iOS"

actual fun log(message: String) {
    println("[YT_OWN_WAY] $message")
}

@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, onError: (String) -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Video player not implemented for iOS yet\nURL: $url")
    }
}

fun MainViewController() = ComposeUIViewController { App() }

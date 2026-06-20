import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

actual fun getPlatformName(): String = "Desktop"

actual fun log(message: String) {
    println("[YT_OWN_WAY] $message")
}

@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, onError: (String) -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Video player not implemented for Desktop yet\nURL: $url")
    }
}

@Composable fun MainView() = App()

@Preview
@Composable
fun AppPreview() {
    App()
}
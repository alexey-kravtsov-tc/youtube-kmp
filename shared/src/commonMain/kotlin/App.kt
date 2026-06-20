
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch

enum class Screen { WIZARD_YOUTUBE, WIZARD_FIREBASE, WIZARD_GEMINI, WIZARD_SUCCESS, MAIN, SETTINGS, VIDEO_DETAILS }

expect fun getPlatformName(): String
expect fun log(message: String)

@Composable
expect fun VideoPlayer(url: String, modifier: Modifier, onError: (String) -> Unit = {})

@Composable
fun App() {
    MaterialTheme {
        val settings = remember { Settings() }
        var currentScreen by remember {
            mutableStateOf(
                if (settings.getString("yt_key", "").isEmpty()) Screen.WIZARD_YOUTUBE else Screen.MAIN
            )
        }

        var ytKey by remember { mutableStateOf(settings.getString("yt_key", "")) }
        var fbStorage by remember { mutableStateOf(settings.getString("fb_storage", "")) }
        var fbKey by remember { mutableStateOf(settings.getString("fb_key", "")) }
        var geminiKey by remember { mutableStateOf(settings.getString("gemini_key", "")) }

        val pipedClient = remember { PipedClient(settings) }
        val scope = rememberCoroutineScope()
        var searchQuery by remember { mutableStateOf("") }
        var videos by remember { mutableStateOf(emptyList<PipedVideo>()) }
        var streamInfo by remember { mutableStateOf<PipedStreamResponse?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Surface(modifier = Modifier.fillMaxSize()) {
            val discoveryState = pipedClient.discoveryState
            if (discoveryState != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(discoveryState)
                }
            } else {
                when (currentScreen) {
                    Screen.WIZARD_YOUTUBE -> WizardStep(
                        title = "Step 1: YouTube API",
                        description = "Get YouTube Data API v3 key from Google Cloud Console.",
                        link = "https://console.cloud.google.com/",
                        value1 = ytKey, label1 = "YouTube API Key", onValue1Change = { ytKey = it },
                        onNext = { currentScreen = onYoutubeNext() }
                    )
                    Screen.WIZARD_FIREBASE -> WizardStep(
                        title = "Step 2: Firebase",
                        description = "Provide Firebase Storage URL and API Key.",
                        link = "https://console.firebase.google.com/",
                        value1 = fbStorage, label1 = "Storage URL", onValue1Change = { fbStorage = it },
                        value2 = fbKey, label2 = "API Key", onValue2Change = { fbKey = it },
                        onNext = { currentScreen = onFirebaseNext() }
                    )
                    Screen.WIZARD_GEMINI -> WizardStep(
                        title = "Step 3: Gemini API",
                        description = "Get Gemini API key from Google AI Studio.",
                        link = "https://aistudio.google.com/",
                        value1 = geminiKey, label1 = "Gemini API Key", onValue1Change = { geminiKey = it },
                        onNext = {
                            currentScreen = onGeminiNext(settings, ytKey, fbStorage, fbKey, geminiKey)
                        }
                    )
                    Screen.WIZARD_SUCCESS -> {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Success! Keys Saved.")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { currentScreen = Screen.MAIN }) { Text("Go to Main Screen") }
                        }
                    }
                    Screen.MAIN -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("YouTube Own Way") },
                                    actions = {
                                        IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = { Text("Search or Catalog Query") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = {
                                        scope.launch {
                                            videos = fetchCatalog(pipedClient, searchQuery)
                                        }
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("Catalog:", style = MaterialTheme.typography.h6)
                                errorMessage?.let {
                                    Text(it, color = Color.Red, style = MaterialTheme.typography.caption)
                                    Spacer(Modifier.height(8.dp))
                                }
                                LazyColumn {
                                    items(videos) { video ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                                                log("App: Video clicked: ${video.title} (${video.videoId})")
                                                scope.launch {
                                                    try {
                                                        val result = openStream(pipedClient, video)
                                                        if (result != null) {
                                                            log("App: Stream opened successfully for ${video.videoId}")
                                                            streamInfo = result.second
                                                            currentScreen = Screen.VIDEO_DETAILS
                                                        }
                                                    } catch (e: Exception) {
                                                        log("App: Failed to open stream for ${video.videoId}: ${e.message}")
                                                        val failedUrl = pipedClient.baseUrl
                                                        errorMessage = "Server $failedUrl failed to fetch stream. Finding new server..."
                                                        // pipedClient.getStream already blacklisted the URL if it was a server error
                                                        videos = fetchCatalog(pipedClient, searchQuery)
                                                        errorMessage = "Switched to new server: ${pipedClient.baseUrl}"
                                                    }
                                                }
                                            },
                                            elevation = 4.dp
                                        ) {
                                            Column {
                                                KamelImage(
                                                    resource = asyncPainterResource(video.thumbnail),
                                                    contentDescription = video.title,
                                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                                    onLoading = { progress ->
                                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            CircularProgressIndicator(progress)
                                                        }
                                                    }
                                                )
                                                Column(Modifier.padding(16.dp)) {
                                                    Text(video.title, style = MaterialTheme.typography.subtitle1)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(video.uploaderName, style = MaterialTheme.typography.caption)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Screen.VIDEO_DETAILS -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text(streamInfo?.title ?: "Video Details") },
                                    navigationIcon = {
                                        IconButton(onClick = { currentScreen = Screen.MAIN }) {
                                            Text("Back")
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
                                if (streamInfo != null) {
                                    val streamUrl = streamInfo?.hls ?: streamInfo?.videoStreams?.firstOrNull { !it.videoOnly!! }?.url
                                    if (streamUrl != null) {
                                        log("App: Playing stream: $streamUrl")
                                        VideoPlayer(
                                            url = streamUrl,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                            onError = { error ->
                                                log("App: VideoPlayer ERROR: $error")
                                                scope.launch {
                                                    val failedUrl = pipedClient.baseUrl
                                                    log("App: Blacklisting server $failedUrl due to playback error.")
                                                    pipedClient.blacklistCurrent()
                                                    errorMessage = "Server $failedUrl failed to play stream. Finding new server..."
                                                    currentScreen = Screen.MAIN
                                                    log("App: Refreshing catalog from new server...")
                                                    videos = fetchCatalog(pipedClient, searchQuery)
                                                    log("App: Switched to new server: ${pipedClient.baseUrl}")
                                                    errorMessage = "Switched to new server: ${pipedClient.baseUrl}"
                                                }
                                            }
                                        )
                                    } else {
                                        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).padding(16.dp), contentAlignment = Alignment.Center) {
                                            Text("No stream available")
                                        }
                                    }

                                    Column(Modifier.padding(16.dp)) {
                                        Text(streamInfo?.title ?: "", style = MaterialTheme.typography.h6)
                                        Spacer(Modifier.height(4.dp))
                                        Text(streamInfo?.uploader ?: "", style = MaterialTheme.typography.subtitle1)
                                        Spacer(Modifier.height(16.dp))
                                        Text(streamInfo?.description ?: "", style = MaterialTheme.typography.body2)
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                    Screen.SETTINGS -> {
                        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Settings", style = MaterialTheme.typography.h5)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(value = ytKey, onValueChange = { ytKey = it }, label = { Text("YouTube API Key") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = fbStorage, onValueChange = { fbStorage = it }, label = { Text("Firebase Storage URL") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = fbKey, onValueChange = { fbKey = it }, label = { Text("Firebase API Key") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = geminiKey, onValueChange = { geminiKey = it }, label = { Text("Gemini API Key") })
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                saveSettings(settings, ytKey, fbStorage, fbKey, geminiKey)
                                currentScreen = Screen.MAIN
                            }) { Text("Save & Return") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WizardStep(
    title: String, description: String, link: String,
    value1: String, label1: String, onValue1Change: (String) -> Unit,
    value2: String? = null, label2: String? = null, onValue2Change: ((String) -> Unit)? = null,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))
        Text(description)
        Text(link, color = Color.Blue)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = value1, onValueChange = onValue1Change, label = { Text(label1) })
        if (value2 != null && label2 != null && onValue2Change != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = value2, onValueChange = onValue2Change, label = { Text(label2) })
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext) { Text("Next") }
    }
}

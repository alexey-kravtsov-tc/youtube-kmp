

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings

enum class Screen { WIZARD_YOUTUBE, WIZARD_FIREBASE, WIZARD_GEMINI, WIZARD_SUCCESS, MAIN, SETTINGS }

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

        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                Screen.WIZARD_YOUTUBE -> WizardStep(
                    title = "Step 1: YouTube API",
                    description = "Get YouTube Data API v3 key from Google Cloud Console.",
                    link = "https://console.cloud.google.com/",
                    value1 = ytKey, label1 = "YouTube API Key", onValue1Change = { ytKey = it },
                    onNext = { currentScreen = Screen.WIZARD_FIREBASE }
                )
                Screen.WIZARD_FIREBASE -> WizardStep(
                    title = "Step 2: Firebase",
                    description = "Provide Firebase Storage URL and API Key.",
                    link = "https://console.firebase.google.com/",
                    value1 = fbStorage, label1 = "Storage URL", onValue1Change = { fbStorage = it },
                    value2 = fbKey, label2 = "API Key", onValue2Change = { fbKey = it },
                    onNext = { currentScreen = Screen.WIZARD_GEMINI }
                )
                Screen.WIZARD_GEMINI -> WizardStep(
                    title = "Step 3: Gemini API",
                    description = "Get Gemini API key from Google AI Studio.",
                    link = "https://aistudio.google.com/",
                    value1 = geminiKey, label1 = "Gemini API Key", onValue1Change = { geminiKey = it },
                    onNext = {
                        settings.putString("yt_key", ytKey)
                        settings.putString("fb_storage", fbStorage)
                        settings.putString("fb_key", fbKey)
                        settings.putString("gemini_key", geminiKey)
                        currentScreen = Screen.WIZARD_SUCCESS
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
                        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                            Text("Main Screen (Blank)")
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
                            settings.putString("yt_key", ytKey)
                            settings.putString("fb_storage", fbStorage)
                            settings.putString("fb_key", fbKey)
                            settings.putString("gemini_key", geminiKey)
                            currentScreen = Screen.MAIN
                        }) { Text("Save & Return") }
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

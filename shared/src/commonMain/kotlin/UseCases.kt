import com.russhwolf.settings.Settings

fun saveSettings(
    settings: Settings,
    ytKey: String,
    fbStorage: String,
    fbKey: String,
    geminiKey: String
) {
    settings.putString("yt_key", ytKey)
    settings.putString("fb_storage", fbStorage)
    settings.putString("fb_key", fbKey)
    settings.putString("gemini_key", geminiKey)
}

fun onYoutubeNext(): Screen = Screen.WIZARD_FIREBASE

fun onFirebaseNext(): Screen = Screen.WIZARD_GEMINI

fun onGeminiNext(
    settings: Settings,
    ytKey: String,
    fbStorage: String,
    fbKey: String,
    geminiKey: String
): Screen {
    saveSettings(settings, ytKey, fbStorage, fbKey, geminiKey)
    return Screen.WIZARD_SUCCESS
}

suspend fun fetchCatalog(pipedClient: PipedClient, query: String): List<PipedVideo> {
    return if (query.isNotBlank()) {
        pipedClient.search(query)
    } else {
        pipedClient.getTrending()
    }
}

suspend fun openStream(pipedClient: PipedClient, video: PipedVideo): Pair<String, PipedStreamResponse>? {
    val videoId = video.videoId
    val stream = pipedClient.getStream(videoId)
    return videoId to stream
}

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.network.sockets.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings

@Serializable
data class PipedInstance(
    val name: String,
    val api_url: String,
    val uptime_24h: Double? = null
)

@Serializable
data class PipedVideo(
    val url: String,
    val title: String = "",
    val thumbnail: String = "",
    val uploaderName: String = "",
    val duration: Int = 0,
    val views: Long = 0,
    val uploadedDate: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val isShort: Boolean? = false,
    val type: String? = null
) {
    val videoId: String get() = url.split("=").last()
}

@Serializable
data class PipedSearchResponse(
    val items: List<PipedVideo> = emptyList(),
    val nextpage: String? = null
)

@Serializable
data class PipedStreamResponse(
    val title: String? = null,
    val description: String? = null,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int? = 0,
    val views: Long? = 0,
    val uploadDate: String? = null,
    val hls: String? = null,
    val dash: String? = null,
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream> = emptyList()
)

@Serializable
data class VideoStream(
    val url: String,
    val format: String,
    val quality: String,
    val mimeType: String? = null,
    val videoOnly: Boolean? = false,
    val bitrate: Int? = 0,
    val width: Int? = 0,
    val height: Int? = 0,
    val fps: Int? = 0
)

@Serializable
data class AudioStream(
    val url: String,
    val format: String,
    val bitrate: Int,
    val mimeType: String? = null,
    val language: String? = null
)

class PipedClient(private val settings: Settings = Settings()) {
    var discoveryState by mutableStateOf<String?>(null)
        private set

    var baseUrl by mutableStateOf<String?>(settings.getString("piped_api_url", "").ifBlank { null })
        private set

    private val blacklistedUrls = mutableSetOf<String>()
    private val fallbackInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.private.coffee",
        "https://piped-api.lunar.icu",
        "https://pipedapi.drgns.space",
        "https://pipedapi.astre.me",
        "https://piped-api.garudalinux.org",
        "https://api-piped.mha.fi",
        "https://pipedapi.official-halal.org",
        "https://pipedapi.bus-hit.me",
        "https://pipedapi.synced.cloud"
    )

    private val client = HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
            retryIf { _, response ->
                response.status.value == 429
            }
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
    }

    private suspend fun getBaseUrl(): String {
        val url = baseUrl ?: discoverInstances()
        log("PipedClient: Using Base URL: $url")
        return url
    }

    private suspend fun getAllInstances(): List<String> {
        log("PipedClient: Fetching instances from kavin.rocks...")
        val discovered = try {
            val response = client.get("https://piped-instances.kavin.rocks/")
            val instances: List<PipedInstance> = response.body()
            log("PipedClient: Discovered ${instances.size} instances.")
            instances.sortedByDescending { it.uptime_24h ?: 0.0 }.map { it.api_url }
        } catch (e: Exception) {
            log("PipedClient: Error fetching instances: ${e.message}")
            emptyList()
        }
        val all = (discovered + fallbackInstances).distinct()
        log("PipedClient: Total instances to check (including fallbacks): ${all.size}")
        return all
    }

    private suspend fun discoverInstances(): String {
        discoveryState = "Finding a working Piped instance..."
        log("PipedClient: Starting discovery process...")
        val allUrls = getAllInstances()
        
        for (url in allUrls) {
            if (url in blacklistedUrls) {
                log("PipedClient: Skipping blacklisted URL: $url")
                continue
            }
            log("PipedClient: Checking health of $url...")
            if (checkHealth(url)) {
                log("PipedClient: Found healthy instance: $url")
                baseUrl = url
                settings.putString("piped_api_url", url)
                discoveryState = null
                return url
            } else {
                log("PipedClient: Instance $url is NOT healthy.")
            }
        }
        
        val error = "No healthy Piped instances found"
        log("PipedClient: FATAL: $error")
        discoveryState = error
        throw Exception(error)
    }

    private suspend fun checkHealth(url: String): Boolean {
        return try {
            val response = client.get("$url/suggestions") {
                parameter("query", "test")
            }
            val isHealthy = response.status.value == 200
            log("PipedClient: Health check for $url returned status ${response.status.value}. Healthy: $isHealthy")
            isHealthy
        } catch (e: Exception) {
            log("PipedClient: Health check failed for $url: ${e.message}")
            false
        }
    }

    private suspend fun <T> withRetryDiscovery(name: String, allowRetry: Boolean = true, block: suspend (String) -> T): T {
        log("PipedClient: Executing request '$name'...")
        var currentUrl = getBaseUrl()
        val attemptedUrls = mutableSetOf<String>()

        while (true) {
            try {
                log("PipedClient: Attempting '$name' on $currentUrl")
                val result = block(currentUrl)
                log("PipedClient: Request '$name' SUCCESS on $currentUrl")
                return result
            } catch (e: Exception) {
                log("PipedClient: Request '$name' FAILED on $currentUrl: ${e.message}")
                val errorText = if (e is ServerResponseException) {
                    try { 
                        val text = e.response.bodyAsText()
                        log("PipedClient: Server error body: $text")
                        text 
                    } catch (_: Exception) { "" }
                } else ""

                val isBotBlocked = errorText.contains("SignInConfirmNotBotException") ||
                        errorText.contains("Sign in to confirm that you're not a bot") ||
                        (e is ServerResponseException && e.response.status.value == 500)

                if (isBotBlocked) {
                    log("PipedClient: Bot detection detected on $currentUrl. Blacklisting...")
                    blacklistedUrls.add(currentUrl)
                } else if (e is HttpRequestTimeoutException || e is ConnectTimeoutException || e is SocketTimeoutException) {
                    log("PipedClient: Timeout on $currentUrl. Blacklisting...")
                    blacklistedUrls.add(currentUrl)
                } else {
                    log("PipedClient: General failure on $currentUrl. Not blacklisting yet unless it's critical.")
                }
                
                attemptedUrls.add(currentUrl)
                baseUrl = null
                settings.remove("piped_api_url")

                if (!allowRetry) {
                    log("PipedClient: Retry disabled for '$name'. Throwing exception.")
                    throw e
                }

                log("PipedClient: Looking for next healthy instance...")
                val allUrls = getAllInstances()
                val nextUrl = allUrls.firstOrNull { it !in blacklistedUrls && it !in attemptedUrls }
                
                if (nextUrl != null) {
                    currentUrl = nextUrl
                    log("PipedClient: Switching to $currentUrl for retry of '$name'...")
                    discoveryState = "Switching to $currentUrl..."
                } else {
                    log("PipedClient: EXHAUSTED all instances for '$name'.")
                    discoveryState = "No working Piped instances found after trying ${attemptedUrls.size} mirrors."
                    throw e
                }
            }
        }
    }

    fun blacklistCurrent() {
        baseUrl?.let {
            log("PipedClient: Manually blacklisting current URL: $it")
            blacklistedUrls.add(it)
            baseUrl = null
            settings.remove("piped_api_url")
        }
    }

    suspend fun getSuggestions(query: String): List<String> = withRetryDiscovery("getSuggestions") { url ->
        client.get("$url/suggestions") {
            parameter("query", query)
        }.body()
    }

    suspend fun search(query: String): List<PipedVideo> = withRetryDiscovery("search") { url ->
        log("PipedClient: Searching for '$query'...")
        val response: PipedSearchResponse = client.get("$url/search") {
            parameter("q", query)
            parameter("filter", "videos")
        }.body()
        log("PipedClient: Search returned ${response.items.size} items.")
        response.items
    }

    suspend fun getTrending(region: String = "US"): List<PipedVideo> = withRetryDiscovery("getTrending") { url ->
        log("PipedClient: Fetching trending for region $region...")
        val items: List<PipedVideo> = client.get("$url/trending") {
            parameter("region", region)
        }.body()
        log("PipedClient: Trending returned ${items.size} items.")
        items
    }

    suspend fun getStream(videoId: String): PipedStreamResponse = withRetryDiscovery("getStream", allowRetry = false) { url ->
        log("PipedClient: Fetching stream for videoId: $videoId")
        val response: PipedStreamResponse = client.get("$url/streams/$videoId").body()
        log("PipedClient: Stream info fetched. HLS: ${response.hls != null}, DASH: ${response.dash != null}")
        response
    }
}

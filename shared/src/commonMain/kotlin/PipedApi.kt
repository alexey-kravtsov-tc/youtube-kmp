import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
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

    private var _baseUrl: String? = settings.getString("piped_api_url", "").ifBlank { null }

    private suspend fun getBaseUrl(): String {
        return _baseUrl ?: discoverInstances()
    }

    private suspend fun discoverInstances(): String {
        discoveryState = "Finding a working Piped instance..."
        try {
            val instances: List<PipedInstance> = client.get("https://piped-instances.kavin.rocks/").body()
            val sortedInstances = instances.sortedByDescending { it.uptime_24h ?: 0.0 }
            
            for (instance in sortedInstances) {
                if (checkHealth(instance.api_url)) {
                    val url = instance.api_url
                    _baseUrl = url
                    settings.putString("piped_api_url", url)
                    discoveryState = null
                    return url
                }
            }
            val error = "No healthy Piped instances found"
            discoveryState = error
            throw Exception(error)
        } catch (e: Exception) {
            discoveryState = "Error discovering instances: ${e.message}"
            throw e
        }
    }

    private suspend fun checkHealth(url: String): Boolean {
        return try {
            val response = client.get("$url/suggestions") {
                parameter("query", "test")
            }
            response.status.value == 200
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun <T> withRetryDiscovery(block: suspend (String) -> T): T {
        var url = try { getBaseUrl() } catch (e: Exception) { throw e }
        return try {
            block(url)
        } catch (_: Exception) {
            // If it failed, maybe the instance is down.
            _baseUrl = null
            settings.remove("piped_api_url")
            url = discoverInstances()
            block(url)
        }
    }

    suspend fun getSuggestions(query: String): List<String> = withRetryDiscovery { url ->
        client.get("$url/suggestions") {
            parameter("query", query)
        }.body()
    }

    suspend fun search(query: String): List<PipedVideo> = withRetryDiscovery { url ->
        val response: PipedSearchResponse = client.get("$url/search") {
            parameter("q", query)
            parameter("filter", "videos")
        }.body()
        response.items
    }

    suspend fun getStream(videoId: String): PipedStreamResponse = withRetryDiscovery { url ->
        client.get("$url/streams/$videoId").body()
    }
}

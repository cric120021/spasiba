package app.spasiba.nativeapp2.data.api

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class TenorGifItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val gifUrl: String
)

object TenorGifApi {
    private const val TAG = "TenorGifApi"
    private const val API_KEY = "LIVDSRZULEXM" // Tenor public client API key
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun searchGifs(query: String = ""): List<TenorGifItem> = withContext(Dispatchers.IO) {
        val endpoint = if (query.trim().isBlank()) {
            "https://g.tenor.com/v1/trending?key=$API_KEY&limit=30"
        } else {
            "https://g.tenor.com/v1/search?q=${URLEncoder.encode(query.trim(), "UTF-8")}&key=$API_KEY&limit=30"
        }

        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "SpasibaNative2/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val body = response.body?.string() ?: return@runCatching emptyList()
                val json = JsonParser.parseString(body).asJsonObject
                val results = json.getAsJsonArray("results") ?: return@runCatching emptyList()

                val gifItems = mutableListOf<TenorGifItem>()
                for (elem in results) {
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: ""
                    val title = obj.get("title")?.asString ?: "GIF"
                    val mediaArray = obj.getAsJsonArray("media")
                    if (mediaArray == null || mediaArray.size() == 0) continue

                    val mediaObj = mediaArray[0].asJsonObject
                    val gifMedia = mediaObj.getAsJsonObject("gif") ?: mediaObj.getAsJsonObject("mediumgif") ?: mediaObj.getAsJsonObject("tinygif")
                    val nanoMedia = mediaObj.getAsJsonObject("nanogif") ?: mediaObj.getAsJsonObject("tinygif") ?: gifMedia

                    val gifUrl = gifMedia?.get("url")?.asString ?: continue
                    val previewUrl = nanoMedia?.get("url")?.asString ?: gifUrl

                    gifItems.add(TenorGifItem(id = id, title = title, previewUrl = previewUrl, gifUrl = gifUrl))
                }
                Log.d(TAG, "Fetched ${gifItems.size} GIFs for query='$query'")
                gifItems
            }
        }.getOrElse {
            Log.e(TAG, "Failed to fetch Tenor GIFs for query='$query': ${it.message}")
            emptyList()
        }
    }
}

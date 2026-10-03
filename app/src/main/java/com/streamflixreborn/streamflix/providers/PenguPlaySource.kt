package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.streamflixreborn.streamflix.BuildConfig
import com.streamflixreborn.streamflix.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Native Stremio-addon stream bridge for PenguPlay.
 *
 * NinjaBox never opens the addon as a web app. It asks the addon's /stream
 * resource for the current IMDb movie/episode and converts returned direct
 * HTTP streams into the existing native server/player model.
 */
object PenguPlaySource {
    private const val TAG = "PenguPlaySource"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun getServers(videoType: Video.Type): List<Video.Server> = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.PENGUPLAY_MANIFEST_URL.trim()
        if (manifestUrl.isBlank() || manifestUrl == "null") return@withContext emptyList()

        val requestIdentity = when (videoType) {
            is Video.Type.Movie -> {
                val imdbId = videoType.imdbId?.takeIf { it.startsWith("tt") }
                    ?: return@withContext emptyList()
                "movie" to imdbId
            }

            is Video.Type.Episode -> {
                val imdbId = videoType.tvShow.imdbId?.takeIf { it.startsWith("tt") }
                    ?: return@withContext emptyList()
                "series" to "$imdbId:${videoType.season.number}:${videoType.number}"
            }
        }

        val addonBase = manifestUrl
            .substringBeforeLast("/manifest.json", manifestUrl)
            .trimEnd('/')

        if (addonBase == manifestUrl) {
            Log.w(TAG, "PenguPlay manifest URL is not in the expected /manifest.json form")
            return@withContext emptyList()
        }

        val endpoint = "$addonBase/stream/${requestIdentity.first}/${requestIdentity.second}.json"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "NinjaBox/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "PenguPlay stream lookup returned HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string().orEmpty()
                val streams = runCatching { JSONObject(body).optJSONArray("streams") }.getOrNull()
                    ?: return@withContext emptyList()

                buildList {
                    for (index in 0 until streams.length()) {
                        val item = streams.optJSONObject(index) ?: continue
                        val directUrl = item.optString("url").trim()
                        if (!directUrl.startsWith("http://") && !directUrl.startsWith("https://")) {
                            // NinjaBox intentionally keeps playback native; browser/externalUrl
                            // results are not exposed to the user.
                            continue
                        }

                        val requestHeaders = linkedMapOf<String, String>()
                        val headerObject = item
                            .optJSONObject("behaviorHints")
                            ?.optJSONObject("proxyHeaders")
                            ?.optJSONObject("request")

                        headerObject?.keys()?.forEach { key ->
                            val value = headerObject.optString(key).trim()
                            if (value.isNotEmpty()) requestHeaders[key] = value
                        }

                        val subtitles = buildList {
                            val subtitleArray = item.optJSONArray("subtitles")
                            if (subtitleArray != null) {
                                for (subtitleIndex in 0 until subtitleArray.length()) {
                                    val subtitle = subtitleArray.optJSONObject(subtitleIndex) ?: continue
                                    val file = subtitle.optString("url").trim()
                                    if (file.isEmpty()) continue
                                    val label = subtitle.optString("lang").trim()
                                        .ifEmpty { subtitle.optString("id").trim() }
                                        .ifEmpty { "Subtitle" }
                                    add(Video.Subtitle(label = label, file = file))
                                }
                            }
                        }

                        val displayName = item.optString("name").trim()
                            .ifEmpty { item.optString("title").lineSequence().firstOrNull()?.trim().orEmpty() }
                            .ifEmpty { item.optString("description").lineSequence().firstOrNull()?.trim().orEmpty() }
                            .ifEmpty { "Stream ${index + 1}" }

                        val server = Video.Server(
                            id = "pengu:${requestIdentity.first}:${requestIdentity.second}:$index",
                            name = "PenguPlay • $displayName",
                            src = directUrl,
                        )

                        server.video = Video(
                            source = directUrl,
                            subtitles = subtitles,
                            headers = requestHeaders.takeIf { it.isNotEmpty() },
                        )

                        add(server)
                    }
                }
            }
        } catch (e: Exception) {
            // Never log the configured manifest URL because it can contain a private token.
            Log.w(TAG, "PenguPlay stream lookup failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }
}

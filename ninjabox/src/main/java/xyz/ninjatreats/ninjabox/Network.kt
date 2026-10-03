package xyz.ninjatreats.ninjabox

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CatalogItem(
    val id: Int,
    val type: String,
    val title: String,
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String = "",
    val rating: Double = 0.0,
    val imdbId: String? = null,
)

data class SeasonInfo(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val poster: String? = null,
)

data class EpisodeInfo(
    val number: Int,
    val name: String,
    val overview: String,
    val still: String? = null,
    val airDate: String = "",
)

data class MediaDetail(
    val item: CatalogItem,
    val genres: List<String>,
    val cast: List<String>,
    val seasons: List<SeasonInfo> = emptyList(),
)

data class SubtitleTrack(val label: String, val url: String)

data class PlayableStream(
    val provider: String,
    val url: String,
    val quality: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack>,
)

class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("ninjabox_auth", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", created).apply()
            return created
        }

    val token: String get() = prefs.getString("token", "").orEmpty()
    val username: String get() = prefs.getString("username", "").orEmpty()

    fun saveSession(token: String, username: String) {
        prefs.edit().putString("token", token).putString("username", username).apply()
    }

    fun clearSession() {
        prefs.edit().remove("token").apply()
    }

    companion object {
        fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }
            .take(120)
    }
}

class FavoriteStore(context: Context) {
    private val prefs = context.getSharedPreferences("ninjabox_favorites", Context.MODE_PRIVATE)

    fun all(): List<CatalogItem> {
        val raw = prefs.getString("items", "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(
                    CatalogItem(
                        id = o.optInt("id"),
                        type = o.optString("type"),
                        title = o.optString("title"),
                        overview = o.optString("overview"),
                        poster = o.optString("poster").takeIf { it.isNotBlank() },
                        backdrop = o.optString("backdrop").takeIf { it.isNotBlank() },
                        year = o.optString("year"),
                        rating = o.optDouble("rating"),
                        imdbId = o.optString("imdbId").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    fun contains(item: CatalogItem): Boolean = all().any { it.id == item.id && it.type == item.type }

    fun toggle(item: CatalogItem): Boolean {
        val items = all().toMutableList()
        val index = items.indexOfFirst { it.id == item.id && it.type == item.type }
        val nowFavorite = if (index >= 0) {
            items.removeAt(index)
            false
        } else {
            items.add(0, item)
            true
        }
        val array = JSONArray()
        items.forEach { x ->
            array.put(
                JSONObject()
                    .put("id", x.id)
                    .put("type", x.type)
                    .put("title", x.title)
                    .put("overview", x.overview)
                    .put("poster", x.poster ?: "")
                    .put("backdrop", x.backdrop ?: "")
                    .put("year", x.year)
                    .put("rating", x.rating)
                    .put("imdbId", x.imdbId ?: "")
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
        return nowFavorite
    }
}

object NinjaApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun endpoint(path: String): String = BuildConfig.PANEL_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')

    suspend fun login(username: String, password: String, deviceId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject()
                .put("username", username)
                .put("password", password)
                .put("device_id", deviceId)
                .put("device_name", AuthStore.deviceName())

            val request = Request.Builder()
                .url(endpoint("api/app/login.php"))
                .header("Accept", "application/json")
                .header("User-Agent", "NinjaBox/${BuildConfig.VERSION_NAME}")
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val obj = runCatching { JSONObject(body) }.getOrNull()
                if (!response.isSuccessful || obj?.optBoolean("success") != true) {
                    throw IllegalStateException(obj?.optString("message").takeUnless { it.isNullOrBlank() } ?: "Login failed")
                }
                obj.optString("token").takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Panel did not return a token")
            }
        }
    }

    suspend fun validate(token: String, deviceId: String): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        runCatching {
            val request = Request.Builder()
                .url(endpoint("api/app/status.php"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .header("X-NinjaBox-Device", deviceId)
                .header("User-Agent", "NinjaBox/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val obj = JSONObject(response.body?.string().orEmpty())
                obj.optBoolean("success") && obj.optBoolean("active")
            }
        }.getOrDefault(false)
    }

    suspend fun logout(token: String, deviceId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint("api/app/logout.php"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .header("X-NinjaBox-Device", deviceId)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        }
        Unit
    }

    suspend fun resolveStreams(
        token: String,
        deviceId: String,
        item: CatalogItem,
        season: Int = 0,
        episode: Int = 0,
    ): Result<List<PlayableStream>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = endpoint("api/ninjabox/streams.php").toHttpUrl().newBuilder()
                .addQueryParameter("type", if (item.type == "tv") "series" else "movie")
                .addQueryParameter("tmdb_id", item.id.toString())
                .addQueryParameter("imdb_id", item.imdbId.orEmpty())
                .addQueryParameter("title", item.title)
                .addQueryParameter("year", item.year)
                .addQueryParameter("season", season.toString())
                .addQueryParameter("episode", episode.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .header("X-NinjaBox-Device", deviceId)
                .header("User-Agent", "NinjaBox/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val obj = JSONObject(response.body?.string().orEmpty())
                if (!response.isSuccessful || !obj.optBoolean("success")) {
                    throw IllegalStateException(obj.optString("message").ifBlank { "No sources available" })
                }
                val streams = obj.optJSONArray("streams") ?: JSONArray()
                buildList {
                    for (i in 0 until streams.length()) {
                        val s = streams.optJSONObject(i) ?: continue
                        val streamUrl = s.optString("url")
                        if (!streamUrl.startsWith("http")) continue

                        val headers = linkedMapOf<String, String>()
                        s.optJSONObject("headers")?.let { h ->
                            h.keys().forEach { key ->
                                val value = h.optString(key)
                                if (value.isNotBlank()) headers[key] = value
                            }
                        }

                        val subtitles = buildList {
                            val a = s.optJSONArray("subtitles") ?: JSONArray()
                            for (j in 0 until a.length()) {
                                val sub = a.optJSONObject(j) ?: continue
                                val u = sub.optString("url")
                                if (u.isNotBlank()) {
                                    add(SubtitleTrack(sub.optString("lang").ifBlank { "Subtitle" }, u))
                                }
                            }
                        }

                        add(
                            PlayableStream(
                                provider = s.optString("provider").ifBlank { "Source ${i + 1}" },
                                url = streamUrl,
                                quality = s.optString("quality"),
                                headers = headers,
                                subtitles = subtitles,
                            )
                        )
                    }
                }
            }
        }
    }
}

object TmdbApi {
    private const val ROOT = "https://api.themoviedb.org/3"
    private const val IMAGE = "https://image.tmdb.org/t/p/w500"
    private const val BACKDROP = "https://image.tmdb.org/t/p/original"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        if (BuildConfig.TMDB_API_KEY.isBlank()) throw IllegalStateException("TMDB API key is not configured")
        val builder = (ROOT + path).toHttpUrl().newBuilder()
            .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
            .addQueryParameter("language", "en-GB")
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }

        val request = Request.Builder().url(builder.build()).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("TMDB returned HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun image(path: String?, backdrop: Boolean = false): String? =
        path?.takeIf { it.isNotBlank() }?.let { (if (backdrop) BACKDROP else IMAGE) + it }

    private fun item(o: JSONObject, forcedType: String? = null): CatalogItem? {
        val mediaType = forcedType ?: o.optString("media_type")
        val type = when (mediaType) {
            "movie" -> "movie"
            "tv" -> "tv"
            else -> return null
        }
        val title = if (type == "movie") o.optString("title") else o.optString("name")
        if (title.isBlank()) return null
        val date = if (type == "movie") o.optString("release_date") else o.optString("first_air_date")
        return CatalogItem(
            id = o.optInt("id"),
            type = type,
            title = title,
            overview = o.optString("overview"),
            poster = image(o.optString("poster_path")),
            backdrop = image(o.optString("backdrop_path"), true),
            year = date.take(4),
            rating = o.optDouble("vote_average", 0.0),
        )
    }

    private suspend fun list(path: String, type: String? = null): List<CatalogItem> {
        val a = get(path).optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until a.length()) item(a.optJSONObject(i) ?: continue, type)?.let(::add)
        }
    }

    suspend fun trendingAll(): List<CatalogItem> = list("/trending/all/day")
    suspend fun trendingMovies(): List<CatalogItem> = list("/trending/movie/day", "movie")
    suspend fun trendingTv(): List<CatalogItem> = list("/trending/tv/day", "tv")
    suspend fun popularMovies(): List<CatalogItem> = list("/movie/popular", "movie")
    suspend fun popularTv(): List<CatalogItem> = list("/tv/popular", "tv")
    suspend fun topMovies(): List<CatalogItem> = list("/movie/top_rated", "movie")
    suspend fun topTv(): List<CatalogItem> = list("/tv/top_rated", "tv")
    suspend fun nowPlaying(): List<CatalogItem> = list("/movie/now_playing", "movie")
    suspend fun airingToday(): List<CatalogItem> = list("/tv/airing_today", "tv")

    suspend fun search(query: String): List<CatalogItem> {
        if (query.isBlank()) return emptyList()
        val a = get("/search/multi", mapOf("query" to query, "include_adult" to "false"))
            .optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until a.length()) item(a.optJSONObject(i) ?: continue)?.let(::add)
        }
    }

    suspend fun detail(base: CatalogItem): MediaDetail {
        val path = if (base.type == "movie") "/movie/${base.id}" else "/tv/${base.id}"
        val obj = get(path, mapOf("append_to_response" to "external_ids,credits,recommendations"))

        val date = if (base.type == "movie") obj.optString("release_date") else obj.optString("first_air_date")
        val full = base.copy(
            title = if (base.type == "movie") obj.optString("title", base.title) else obj.optString("name", base.title),
            overview = obj.optString("overview", base.overview),
            poster = image(obj.optString("poster_path")) ?: base.poster,
            backdrop = image(obj.optString("backdrop_path"), true) ?: base.backdrop,
            year = date.take(4).ifBlank { base.year },
            rating = obj.optDouble("vote_average", base.rating),
            imdbId = obj.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() },
        )

        val genres = buildList {
            val a = obj.optJSONArray("genres") ?: JSONArray()
            for (i in 0 until a.length()) {
                val name = a.optJSONObject(i)?.optString("name").orEmpty()
                if (name.isNotBlank()) add(name)
            }
        }

        val cast = buildList {
            val a = obj.optJSONObject("credits")?.optJSONArray("cast") ?: JSONArray()
            for (i in 0 until minOf(a.length(), 12)) {
                val name = a.optJSONObject(i)?.optString("name").orEmpty()
                if (name.isNotBlank()) add(name)
            }
        }

        val seasons = if (base.type == "tv") buildList {
            val a = obj.optJSONArray("seasons") ?: JSONArray()
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                val number = s.optInt("season_number")
                if (number < 0) continue
                add(
                    SeasonInfo(
                        number = number,
                        name = s.optString("name").ifBlank { "Season $number" },
                        episodeCount = s.optInt("episode_count"),
                        poster = image(s.optString("poster_path")),
                    )
                )
            }
        } else emptyList()

        return MediaDetail(full, genres, cast, seasons)
    }

    suspend fun episodes(tvId: Int, season: Int): List<EpisodeInfo> {
        val a = get("/tv/$tvId/season/$season").optJSONArray("episodes") ?: JSONArray()
        return buildList {
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                add(
                    EpisodeInfo(
                        number = e.optInt("episode_number"),
                        name = e.optString("name").ifBlank { "Episode ${e.optInt("episode_number")}" },
                        overview = e.optString("overview"),
                        still = image(e.optString("still_path")),
                        airDate = e.optString("air_date"),
                    )
                )
            }
        }
    }
}

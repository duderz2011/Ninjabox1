package xyz.ninjatreats.ninjabox

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HEADERS = "headers"
        const val EXTRA_SUBTITLES = "subtitles"
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }

        val headers = linkedMapOf<String, String>()
        runCatching {
            val obj = JSONObject(intent.getStringExtra(EXTRA_HEADERS).orEmpty())
            obj.keys().forEach { key ->
                val value = obj.optString(key)
                if (value.isNotBlank()) headers[key] = value
            }
        }

        val dataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("NinjaBox/" + BuildConfig.VERSION_NAME)

        if (headers.isNotEmpty()) {
            dataSource.setDefaultRequestProperties(headers)
        }

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()

        val subtitles = mutableListOf<MediaItem.SubtitleConfiguration>()
        runCatching {
            val array = JSONArray(intent.getStringExtra(EXTRA_SUBTITLES).orEmpty())
            for (i in 0 until array.length()) {
                val sub = array.optJSONObject(i) ?: continue
                val subUrl = sub.optString("url")
                if (subUrl.isBlank()) continue
                val lower = subUrl.lowercase()
                val mime = when {
                    lower.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
                    lower.contains(".ass") || lower.contains(".ssa") -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.TEXT_VTT
                }
                subtitles.add(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                        .setLabel(sub.optString("label").ifBlank { "Subtitle" })
                        .setMimeType(mime)
                        .build()
                )
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(subtitles)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(intent.getStringExtra(EXTRA_TITLE).orEmpty())
                    .build()
            )
            .build()

        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true
        player = exo

        setContentView(
            PlayerView(this).apply {
                useController = true
                controllerAutoShow = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                this.player = exo
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        )
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

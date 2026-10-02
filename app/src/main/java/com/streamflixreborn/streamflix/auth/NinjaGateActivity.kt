package com.streamflixreborn.streamflix.auth

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.streamflixreborn.streamflix.BuildConfig
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.activities.main.MainMobileActivity
import com.streamflixreborn.streamflix.activities.main.MainTvActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class NinjaGateActivity : FragmentActivity() {

    companion object {
        private const val LOGIN_URL =
            "https://ninjatreats.xyz/project/clipbox/panel/api/app/login.php"
        private const val STATUS_URL =
            "https://ninjatreats.xyz/project/clipbox/panel/api/app/status.php"

        private const val PREFS = "ninjabox_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USERNAME = "username"
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var login: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 9, 11)
        window.navigationBarColor = Color.rgb(9, 9, 11)

        buildUi()

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        username.setText(prefs.getString(KEY_USERNAME, "").orEmpty())

        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        if (token.isNotBlank()) {
            status.text = "Checking saved access…"
            validateToken(token)
        } else {
            username.requestFocus()
        }
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun deviceId(): String {
        val current = prefs().getString(KEY_DEVICE_ID, "").orEmpty()
        if (current.isNotBlank()) return current

        val created = UUID.randomUUID().toString()
        prefs().edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }
            .take(120)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun text(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            setTextColor(Color.WHITE)
            textSize = size
            gravity = Gravity.CENTER
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(9, 9, 11))
        }

        val width = (resources.displayMetrics.widthPixels - dp(48))
            .coerceAtMost(dp(680))
            .coerceAtLeast(dp(280))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(38), dp(34), dp(38), dp(34))
            background = rounded(Color.rgb(28, 28, 31), 20)
        }

        root.addView(
            card,
            LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            adjustViewBounds = true
        }
        card.addView(logo, LinearLayout.LayoutParams(dp(86), dp(86)))

        card.addView(text("NINJABOX", 31f, true))

        val subtitle = text("NinjaTreats Access", 18f).apply {
            setTextColor(Color.rgb(190, 190, 195))
        }
        card.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(26)) }
        )

        username = EditText(this).apply {
            hint = "Username"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(150, 150, 155))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(18), 0, dp(18), 0)
            background = rounded(Color.rgb(42, 42, 46), 9, Color.rgb(80, 80, 86))
        }
        card.addView(
            username,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
        )

        password = EditText(this).apply {
            hint = "Password"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(150, 150, 155))
            setSingleLine(true)
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(18), 0, dp(18), 0)
            background = rounded(Color.rgb(42, 42, 46), 9, Color.rgb(80, 80, 86))
        }
        card.addView(
            password,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
                setMargins(0, dp(14), 0, 0)
            }
        )

        login = Button(this).apply {
            text = "OPEN NINJABOX"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            background = rounded(Color.rgb(212, 10, 47), 10)
            setOnClickListener {
                val u = username.text.toString().trim()
                val p = password.text.toString()
                if (u.isBlank() || p.isBlank()) {
                    status.text = "Enter your username and password."
                } else {
                    authenticate(u, p)
                }
            }
        }
        card.addView(
            login,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
                setMargins(0, dp(22), 0, 0)
            }
        )

        progress = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(
            progress,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(0, dp(18), 0, 0)
            }
        )

        status = text("", 15f).apply {
            setTextColor(Color.rgb(210, 210, 215))
        }
        card.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) }
        )

        setContentView(root)
    }

    private fun setBusy(busy: Boolean, message: String) {
        login.isEnabled = !busy
        username.isEnabled = !busy
        password.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        status.text = message
    }

    private fun validateToken(token: String) {
        setBusy(true, "Checking saved access…")

        Thread {
            val request = Request.Builder()
                .url(STATUS_URL)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .header("X-NinjaBox-Device", deviceId())
                .header("User-Agent", "NinjaBox/" + BuildConfig.VERSION_NAME)
                .get()
                .build()

            val allowed = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        false
                    } else {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        json.optBoolean("success", false) &&
                            json.optBoolean("active", false)
                    }
                }
            } catch (_: Exception) {
                null
            }

            runOnUiThread {
                when (allowed) {
                    true -> {
                        NinjaGateSession.grant()
                        openMainApp()
                    }
                    false -> {
                        prefs().edit().remove(KEY_TOKEN).apply()
                        setBusy(false, "Saved access has expired. Sign in again.")
                        password.requestFocus()
                    }
                    null -> {
                        setBusy(false, "Cannot contact the NinjaTreats panel.")
                    }
                }
            }
        }.start()
    }

    private fun authenticate(user: String, pass: String) {
        setBusy(true, "Checking access…")

        Thread {
            val payload = JSONObject()
                .put("username", user)
                .put("password", pass)
                .put("device_id", deviceId())
                .put("device_name", deviceName())
                .toString()

            val request = Request.Builder()
                .url(LOGIN_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "NinjaBox/" + BuildConfig.VERSION_NAME)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val result = try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()

                    if (response.isSuccessful &&
                        json?.optBoolean("success", false) == true
                    ) {
                        AuthResult(
                            allowed = true,
                            token = json.optString("token"),
                            message = "Access approved."
                        )
                    } else {
                        AuthResult(
                            allowed = false,
                            token = "",
                            message = json?.optString("message")
                                ?.takeIf { it.isNotBlank() }
                                ?: "Login failed."
                        )
                    }
                }
            } catch (_: Exception) {
                AuthResult(false, "", "Cannot contact the NinjaTreats panel.")
            }

            runOnUiThread {
                if (result.allowed && result.token.isNotBlank()) {
                    prefs().edit()
                        .putString(KEY_TOKEN, result.token)
                        .putString(KEY_USERNAME, user)
                        .apply()

                    password.setText("")
                    NinjaGateSession.grant()
                    setBusy(false, "Access approved. Opening NinjaBox…")
                    openMainApp()
                } else {
                    setBusy(false, result.message)
                }
            }
        }.start()
    }

    private fun openMainApp() {
        val target = when (BuildConfig.APP_LAYOUT) {
            "tv" -> MainTvActivity::class.java
            "mobile" -> MainMobileActivity::class.java
            else -> if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                MainTvActivity::class.java
            } else {
                MainMobileActivity::class.java
            }
        }

        startActivity(
            Intent(this, target).apply {
                this@NinjaGateActivity.intent?.data?.let { data = it }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private data class AuthResult(
        val allowed: Boolean,
        val token: String,
        val message: String,
    )
}

package com.streamflixreborn.streamflix.auth

import android.app.Activity
import android.content.Intent

object NinjaGateSession {
    @Volatile
    var authorized: Boolean = false
        private set

    fun grant() {
        authorized = true
    }

    fun revoke() {
        authorized = false
    }

    fun ensureAuthorized(activity: Activity): Boolean {
        if (authorized) return true

        val gate = Intent(activity, NinjaGateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            data = activity.intent?.data
        }
        activity.startActivity(gate)
        activity.finish()
        return false
    }
}

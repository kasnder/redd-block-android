package net.kollnig.reddblockandroid.util

import android.content.SharedPreferences

lateinit var prefs: SharedPreferences

val isPrefsInitialized: Boolean
    get() = ::prefs.isInitialized

var hasConsented: Boolean
    get() = prefs.getBoolean("has_consented", false)
    set(value) {
        prefs.edit().putBoolean("has_consented", value).apply()
    }

package com.example.suncareclinic.data.local

import android.content.Context

class TokenStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "suncare_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}

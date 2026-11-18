package com.aminfathi.nava.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class Preferences(val language: String = "", val model: String = "", val modelValidated: Boolean = false, val lock: Boolean = false, val theme: String = "system", val welcomed: Boolean = false, val serverUrl: String = "", val portalEmail: String = "")

class PreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences("nava_preferences", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val flow = state.asStateFlow()
    private fun read() = Preferences(
        preferences.getString("language", "").orEmpty(),
        preferences.getString("model", "").orEmpty(),
        preferences.getBoolean("validated", false),
        preferences.getBoolean("lock", false),
        preferences.getString("theme", "system") ?: "system",
        preferences.getBoolean("welcomed", false),
        preferences.getString("server", "").orEmpty(),
        preferences.getString("portalEmail", "").orEmpty()
    )
    private suspend fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) = withContext(Dispatchers.IO) {
        check(preferences.edit().apply(block).commit())
        state.value = read()
    }
    suspend fun language(value: String) = edit { putString("language", value) }
    suspend fun model(value: String, valid: Boolean = false) = edit { putString("model", value); putBoolean("validated", valid) }
    suspend fun lock(value: Boolean) = edit { putBoolean("lock", value) }
    suspend fun theme(value: String) = edit { putString("theme", value) }
    suspend fun welcome() = edit { putBoolean("welcomed", true) }
    suspend fun server(url: String, email: String) = edit { putString("server", url.trim()); putString("portalEmail", email.trim()) }
}

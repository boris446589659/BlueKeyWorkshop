package com.keyflux

import android.content.Context
import android.content.SharedPreferences

/** Storage owned by the standalone BlueKey Workshop app and exposed read-only to Gboard. */
internal class ModulePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun readAll(): HashMap<String, Any> = read(preferences)

    fun put(key: String, value: Any): Boolean = putAll(mapOf(key to value))

    fun putAll(values: Map<String, Any>): Boolean {
        val committed = write(preferences, values, onlyIfMissing = false)
        if (committed) {
            appContext.contentResolver.notifyChange(SettingsProvider.CONTENT_URI, null)
        }
        return committed
    }

    companion object {
        const val FILE_NAME = "keyflux_shared_prefs"

        fun read(preferences: SharedPreferences): HashMap<String, Any> {
            val result = HashMap<String, Any>()
            for ((key, stored) in preferences.all) {
                if (!ProviderAccessPolicy.isExposedPreference(key) || stored == null) continue
                result[key] = decode(preferences, key, stored)
            }
            return result
        }

        fun write(
            preferences: SharedPreferences,
            values: Map<String, Any>,
            onlyIfMissing: Boolean
        ): Boolean {
            val accepted = values.filter { (key, value) ->
                ProviderAccessPolicy.isExposedPreference(key) &&
                    isSupportedValue(value) &&
                    (!onlyIfMissing || !preferences.contains(key))
            }
            if (accepted.isEmpty()) return true

            return preferences.edit().run {
                for ((key, value) in accepted) {
                    putString(key, CryptoHelper.encrypt(value.toString()))
                    putString(key + "_type", valueType(value))
                }
                commit()
            }
        }

        private fun decode(preferences: SharedPreferences, key: String, stored: Any): Any {
            if (stored !is String) return stored
            val value = CryptoHelper.decrypt(stored)
            val type = preferences.getString(key + "_type", null) ?: when (key) {
                "keyflux_clip_days", "keyflux_clip_size" -> "int"
                else -> "boolean"
            }
            return when (type) {
                "boolean" -> value.toBoolean()
                "int" -> value.toIntOrNull() ?: 0
                "long" -> value.toLongOrNull() ?: 0L
                "float" -> value.toFloatOrNull() ?: 0f
                else -> value
            }
        }

        private fun isSupportedValue(value: Any): Boolean =
            value is Boolean || value is Int || value is Long ||
                value is Float || value is String

        private fun valueType(value: Any): String = when (value) {
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            else -> "string"
        }
    }
}

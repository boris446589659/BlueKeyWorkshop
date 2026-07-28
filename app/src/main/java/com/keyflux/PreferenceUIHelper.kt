package com.keyflux

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XposedHelpers

/**
 * Reflection bridge for Gboard's shaded AndroidX Preference implementation.
 *
 * Gboard 17.8.3 keeps the AndroidX classes but obfuscates their method names.
 * The aliases below are verified against that build. Avoid signature-only
 * discovery here: invoking an arbitrary setter can mutate dependencies,
 * listeners, ordering, or visibility on Gboard's own preferences.
 */
internal class PreferenceUIHelper(private val plugin: PluginEntry) {

    private fun log(message: String) = plugin.log(message)

    fun getPreferenceScreen(fragment: Any, classLoader: ClassLoader): Any? {
        try {
            return XposedHelpers.callMethod(fragment, "getPreferenceScreen")
        } catch (_: Throwable) {
        }
        try {
            return XposedHelpers.callMethod(fragment, "n")
        } catch (error: Throwable) {
            log("getPreferenceScreen failed: ${error.message}")
            return null
        }
    }

    fun getFragmentContext(fragment: Any): Context? {
        try {
            return XposedHelpers.callMethod(fragment, "getContext") as? Context
        } catch (_: Throwable) {
        }
        try {
            return XposedHelpers.callMethod(fragment, "w") as? Context
        } catch (_: Throwable) {
        }
        try {
            return XposedHelpers.callMethod(fragment, "x") as? Context
        } catch (error: Throwable) {
            log("getFragmentContext failed: ${error.message}")
            return null
        }
    }

    fun findPreference(group: Any, key: String): Any? {
        try {
            return XposedHelpers.callMethod(group, "findPreference", key)
        } catch (_: Throwable) {
        }
        try {
            return XposedHelpers.callMethod(group, "l", key as CharSequence)
        } catch (_: Throwable) {
        }

        val count = getPreferenceCount(group)
        for (index in 0 until count) {
            val child = getPreference(group, index) ?: continue
            if (getPreferenceKey(child) == key) return child
        }
        return null
    }

    fun addPreference(group: Any, preference: Any): Boolean {
        try {
            XposedHelpers.callMethod(group, "addPreference", preference)
            return true
        } catch (_: Throwable) {
        }
        return try {
            XposedHelpers.callMethod(group, "an", preference)
            true
        } catch (error: Throwable) {
            log("addPreference failed: ${error.message}")
            false
        }
    }

    fun getPreferenceCount(group: Any): Int {
        try {
            return XposedHelpers.callMethod(group, "getPreferenceCount") as Int
        } catch (_: Throwable) {
        }
        return try {
            XposedHelpers.callMethod(group, "k") as Int
        } catch (_: Throwable) {
            0
        }
    }

    fun getPreference(group: Any, index: Int): Any? {
        try {
            return XposedHelpers.callMethod(group, "getPreference", index)
        } catch (_: Throwable) {
        }
        return try {
            XposedHelpers.callMethod(group, "o", index)
        } catch (_: Throwable) {
            null
        }
    }

    fun getPreferenceKey(preference: Any): String? {
        try {
            return XposedHelpers.callMethod(preference, "getKey") as? String
        } catch (_: Throwable) {
        }
        try {
            return XposedHelpers.getObjectField(preference, "r") as? String
        } catch (_: Throwable) {
        }
        return try {
            XposedHelpers.getObjectField(preference, "mKey") as? String
        } catch (_: Throwable) {
            null
        }
    }

    fun setPreferenceKey(preference: Any, key: String) {
        try {
            XposedHelpers.callMethod(preference, "setKey", key)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.callMethod(preference, "P", key)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.setObjectField(preference, "r", key)
        } catch (error: Throwable) {
            log("setPreferenceKey failed for $key: ${error.message}")
        }
    }

    fun setPreferenceTitle(preference: Any, title: CharSequence) {
        try {
            XposedHelpers.callMethod(preference, "setTitle", title)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.callMethod(preference, "V", title)
        } catch (error: Throwable) {
            log("setPreferenceTitle failed: ${error.message}")
        }
    }

    fun setPreferenceSummary(preference: Any, summary: CharSequence) {
        try {
            XposedHelpers.callMethod(preference, "setSummary", summary)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.callMethod(preference, "n", summary)
        } catch (error: Throwable) {
            log("setPreferenceSummary failed: ${error.message}")
        }
    }

    fun setPreferenceIntent(preference: Any, intent: Intent) {
        try {
            XposedHelpers.callMethod(preference, "setIntent", intent)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.setObjectField(preference, "s", intent)
        } catch (error: Throwable) {
            log("setPreferenceIntent failed: ${error.message}")
        }
    }

    fun setPreferencePersistent(preference: Any, persistent: Boolean) {
        try {
            XposedHelpers.callMethod(preference, "setPersistent", persistent)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.setBooleanField(preference, "w", persistent)
        } catch (error: Throwable) {
            log("setPreferencePersistent failed: ${error.message}")
        }
    }

    fun setPreferenceChecked(preference: Any, checked: Boolean) {
        try {
            XposedHelpers.callMethod(preference, "setChecked", checked)
            if (getPreferenceChecked(preference) == checked) return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.callMethod(preference, "k", checked)
            if (getPreferenceChecked(preference) == checked) return
        } catch (_: Throwable) {
        }

        // Gboard obfuscates TwoStatePreference#setChecked. Fall back to its
        // checked field only after both callable paths failed verification.
        var type: Class<*>? = preference.javaClass
        while (type != null) {
            if (type.name == "androidx.preference.TwoStatePreference") {
                try {
                    type.getDeclaredField("a").apply { isAccessible = true }
                        .setBoolean(preference, checked)
                    runCatching { XposedHelpers.callMethod(preference, "d") }
                    if (getPreferenceChecked(preference) == checked) return
                } catch (_: Throwable) {
                }
            }
            type = type.superclass
        }
        log("setPreferenceChecked failed for ${preference.javaClass.name}")
    }

    private fun getPreferenceChecked(preference: Any): Boolean? {
        for (methodName in listOf("isChecked", "am")) {
            try {
                return XposedHelpers.callMethod(preference, methodName) as? Boolean
            } catch (_: Throwable) {
            }
        }
        return null
    }

    fun setIconSpaceReserved(preference: Any, reserved: Boolean) {
        try {
            XposedHelpers.callMethod(preference, "setIconSpaceReserved", reserved)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.callMethod(preference, "O", reserved)
        } catch (error: Throwable) {
            log("setIconSpaceReserved failed: ${error.message}")
        }
    }

    fun setOnPreferenceChangeListener(preference: Any, listener: Any) {
        try {
            XposedHelpers.callMethod(preference, "setOnPreferenceChangeListener", listener)
            return
        } catch (_: Throwable) {
        }
        if (setListenerField(preference, listOf("n", "mOnChangeListener"), listener)) return
        log("setOnPreferenceChangeListener failed")
    }

    fun setOnPreferenceClickListener(preference: Any, listener: Any) {
        try {
            XposedHelpers.callMethod(preference, "setOnPreferenceClickListener", listener)
            return
        } catch (_: Throwable) {
        }
        if (setListenerField(preference, listOf("o", "mOnClickListener"), listener)) return
        log("setOnPreferenceClickListener failed")
    }

    private fun setListenerField(preference: Any, names: List<String>, listener: Any): Boolean {
        for (name in names) {
            try {
                val field = XposedHelpers.findField(preference.javaClass, name)
                if (!field.type.isInstance(listener)) continue
                field.set(preference, listener)
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    fun setPreferenceEnabled(preference: Any, enabled: Boolean) {
        try {
            XposedHelpers.callMethod(preference, "setEnabled", enabled)
            return
        } catch (_: Throwable) {
        }
        try {
            XposedHelpers.callMethod(preference, "L", enabled)
        } catch (error: Throwable) {
            log("setPreferenceEnabled failed: ${error.message}")
        }
    }
}

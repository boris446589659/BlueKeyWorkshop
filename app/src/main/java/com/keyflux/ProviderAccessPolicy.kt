package com.keyflux

internal object ProviderAccessPolicy {
    const val MODULE_PACKAGE = "com.keyflux"
    const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"

    fun isAllowedCaller(packages: Array<out String>?): Boolean =
        packages?.any { it == MODULE_PACKAGE || it == GBOARD_PACKAGE } == true

    fun isExposedPreference(key: String): Boolean =
        key.startsWith("keyflux_") && !key.endsWith("_type")

    fun isSupportedBundleValue(value: Any?): Boolean =
        value is Boolean || value is Int || value is Long ||
            value is Float || value is String
}

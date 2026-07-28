package com.keyflux

/** Controls the minimum severity written to the LSPosed/Xposed log. */
internal enum class LogLevel(
    val storedValue: String,
    private val priority: Int,
    val label: String
) {
    DEBUG("debug", 10, "D"),
    INFO("info", 20, "I"),
    WARN("warn", 30, "W"),
    ERROR("error", 40, "E");

    fun includes(messageLevel: LogLevel): Boolean = messageLevel.priority >= priority

    companion object {
        /**
         * `keyflux_log_switch` was the pre-level configuration. Preserve its
         * behavior until the user explicitly chooses a new level.
         */
        fun fromStored(value: Any?, legacyDebugEnabled: Boolean): LogLevel {
            val rawValue = value as? String
            return values().firstOrNull { it.storedValue.equals(rawValue, ignoreCase = true) }
                ?: if (legacyDebugEnabled) DEBUG else INFO
        }
    }
}

package com.keyflux

internal enum class ThemeMode(val keyPart: String) {
    LIGHT("light"),
    DARK("dark")
}

internal enum class ThemeRole(val keyPart: String) {
    BACKGROUND("background"),
    KEY_SURFACE("key_surface"),
    KEY_PRESSED("key_pressed"),
    PRIMARY("primary"),
    SECONDARY("secondary"),
    ACCENT("accent")
}

internal data class ThemePalette(
    val background: Int,
    val keySurface: Int,
    val keyPressed: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int
) {
    fun color(role: ThemeRole): Int = when (role) {
        ThemeRole.BACKGROUND -> background
        ThemeRole.KEY_SURFACE -> keySurface
        ThemeRole.KEY_PRESSED -> keyPressed
        ThemeRole.PRIMARY -> primary
        ThemeRole.SECONDARY -> secondary
        ThemeRole.ACCENT -> accent
    }

    fun withColor(role: ThemeRole, color: Int): ThemePalette = when (role) {
        ThemeRole.BACKGROUND -> copy(background = color)
        ThemeRole.KEY_SURFACE -> copy(keySurface = color)
        ThemeRole.KEY_PRESSED -> copy(keyPressed = color)
        ThemeRole.PRIMARY -> copy(primary = color)
        ThemeRole.SECONDARY -> copy(secondary = color)
        ThemeRole.ACCENT -> copy(accent = color)
    }

    fun toPreferences(mode: ThemeMode): Map<String, Any> = ThemeRole.values().associate {
        preferenceKey(mode, it) to color(it)
    }

    fun containsRgb(color: Int): Boolean {
        val rgb = color and 0x00FFFFFF
        return ThemeRole.values().any { color(it) and 0x00FFFFFF == rgb }
    }

    companion object {
        const val ENABLED_KEY = "keyflux_enable_custom_theme"

        val LIGHT_DEFAULT = ThemePalette(
            background = 0xFFF8F9FA.toInt(),
            keySurface = 0xFFFFFFFF.toInt(),
            keyPressed = 0xFFE8EAED.toInt(),
            primary = 0xFF202124.toInt(),
            secondary = 0xFF5F6368.toInt(),
            accent = 0xFF1A73E8.toInt()
        )

        val DARK_DEFAULT = ThemePalette(
            background = 0xFF202124.toInt(),
            keySurface = 0xFF303134.toInt(),
            keyPressed = 0xFF3C4043.toInt(),
            primary = 0xFFE8EAED.toInt(),
            secondary = 0xFFBDC1C6.toInt(),
            accent = 0xFF8AB4F8.toInt()
        )

        fun preferenceKey(mode: ThemeMode, role: ThemeRole): String =
            "keyflux_theme_${mode.keyPart}_${role.keyPart}"

        fun fromPreferences(prefs: Map<String, Any>, mode: ThemeMode): ThemePalette {
            val defaults = default(mode)
            fun value(role: ThemeRole): Int =
                (prefs[preferenceKey(mode, role)] as? Number)?.toInt() ?: defaults.color(role)

            return ThemePalette(
                background = value(ThemeRole.BACKGROUND),
                keySurface = value(ThemeRole.KEY_SURFACE),
                keyPressed = value(ThemeRole.KEY_PRESSED),
                primary = value(ThemeRole.PRIMARY),
                secondary = value(ThemeRole.SECONDARY),
                accent = value(ThemeRole.ACCENT)
            )
        }

        fun default(mode: ThemeMode): ThemePalette = when (mode) {
            ThemeMode.LIGHT -> LIGHT_DEFAULT
            ThemeMode.DARK -> DARK_DEFAULT
        }

        fun preset(mode: ThemeMode, index: Int): ThemePalette = when (index) {
            1 -> if (mode == ThemeMode.DARK) {
                ThemePalette(
                    0xFF000000.toInt(), 0xFF171717.toInt(), 0xFF292929.toInt(),
                    0xFFFFFFFF.toInt(), 0xFFB8B8B8.toInt(), 0xFF80CBC4.toInt()
                )
            } else {
                ThemePalette(
                    0xFFF4F7F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDE7E6.toInt(),
                    0xFF17201F.toInt(), 0xFF52605E.toInt(), 0xFF00796B.toInt()
                )
            }
            2 -> if (mode == ThemeMode.DARK) {
                ThemePalette(
                    0xFF101820.toInt(), 0xFF1D2B36.toInt(), 0xFF294052.toInt(),
                    0xFFE7F1F8.toInt(), 0xFFAEC4D2.toInt(), 0xFF5AB3F0.toInt()
                )
            } else {
                ThemePalette(
                    0xFFF3F8FC.toInt(), 0xFFFFFFFF.toInt(), 0xFFD7E6F1.toInt(),
                    0xFF162733.toInt(), 0xFF536B7A.toInt(), 0xFF00639B.toInt()
                )
            }
            3 -> if (mode == ThemeMode.DARK) {
                ThemePalette(
                    0xFF21161B.toInt(), 0xFF34232B.toInt(), 0xFF4A303B.toInt(),
                    0xFFFFEDF2.toInt(), 0xFFD9B9C3.toInt(), 0xFFFF9FA4.toInt()
                )
            } else {
                ThemePalette(
                    0xFFFFF7F9.toInt(), 0xFFFFFFFF.toInt(), 0xFFF5DDE4.toInt(),
                    0xFF3A252C.toInt(), 0xFF765A64.toInt(), 0xFFB84F6A.toInt()
                )
            }
            else -> default(mode)
        }

        fun parseColor(raw: String): Int? {
            var value = raw.trim()
            if (value.startsWith("#")) value = value.substring(1)
            if (value.startsWith("0x", ignoreCase = true)) value = value.substring(2)
            if (value.length != 6 && value.length != 8) return null
            val parsed = value.toLongOrNull(16) ?: return null
            return if (value.length == 6) {
                (parsed or 0xFF000000L).toInt()
            } else {
                parsed.toInt()
            }
        }

        fun formatColor(color: Int): String = if ((color ushr 24) == 0xFF) {
            String.format("#%06X", color and 0x00FFFFFF)
        } else {
            String.format("#%08X", color)
        }

        fun applyConfiguredAlpha(original: Int, configured: Int): Int {
            val originalAlpha = original ushr 24
            val configuredAlpha = configured ushr 24
            val alpha = (originalAlpha * configuredAlpha + 127) / 255
            return (alpha shl 24) or (configured and 0x00FFFFFF)
        }

        fun contrastColor(background: Int): Int {
            val red = (background ushr 16) and 0xFF
            val green = (background ushr 8) and 0xFF
            val blue = background and 0xFF
            val luminance = red * 299 + green * 587 + blue * 114
            return if (luminance >= 150_000) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        }

        fun classifyResourceColor(color: Int, dark: Boolean): ThemeRole? {
            val rgb = color and 0x00FFFFFF
            val accentColors = setOf(
                0x4285F4, 0x1A73E8, 0x0B57D0, 0x0842A0, 0x8AB4F8,
                0xA8C7FA, 0x7CACF8, 0x00639B, 0x5AB3F0, 0x7FCFFF,
                // Android 16 Material dynamic accent shades used by recent Gboard builds.
                0xE8DEF8, 0xD0BCFF, 0x6750A4, 0x4F378B, 0x21005D,
                0xCCC2DC, 0x625B71, 0x7D5260, 0xE9DDF5
            )
            if (rgb in accentColors) return ThemeRole.ACCENT

            return if (dark) {
                when (rgb) {
                    0x202124, 0x131314, 0x1F1F1F, 0x1C1B1F, 0x171717,
                    0x18191A, 0x1B1B1B, 0x0E0E0E, 0x181C1F, 0x1E1F20 ->
                        ThemeRole.BACKGROUND
                    0x2C2C2C, 0x303030, 0x303134, 0x282A2D, 0x282A2C,
                    0x333537, 0x37393B, 0x3C4043, 0x424242, 0x444746 ->
                        ThemeRole.KEY_SURFACE
                    0xFFFFFF, 0xF2F2F2, 0xE8EAED, 0xE3E3E3, 0xDFE3E7,
                    0xE1E3E1 -> ThemeRole.PRIMARY
                    0xBDC1C6, 0xC4C7C5, 0xAAB0B5, 0x9AA0A6, 0x8E918F,
                    0x999999, 0x747775 -> ThemeRole.SECONDARY
                    else -> null
                }
            } else {
                when (rgb) {
                    0xF8F9FA, 0xF1F3F4, 0xF2F2F2, 0xFAF9F5, 0xF7F7F7,
                    0xECEFF1 -> ThemeRole.BACKGROUND
                    0xFFFFFF, 0xE8EAED, 0xE3E3E3, 0xDFE3E7, 0xE1E3E1,
                    0xD6D7D7 -> ThemeRole.KEY_SURFACE
                    0x202124, 0x1F1F1F, 0x1C1B1F, 0x21272B, 0x263238,
                    0x313539, 0x6B538C -> ThemeRole.PRIMARY
                    0x5F6368, 0x635F57, 0x747775, 0x777777, 0x8E918F,
                    0x999999 -> ThemeRole.SECONDARY
                    else -> null
                }
            }
        }
    }
}

package com.keyflux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTests {
    @Test
    fun `defaults expose separate light and dark palettes`() {
        assertEquals(0xFFF8F9FA.toInt(), ThemePalette.LIGHT_DEFAULT.background)
        assertEquals(0xFF202124.toInt(), ThemePalette.DARK_DEFAULT.background)
        assertTrue(ThemePalette.LIGHT_DEFAULT.keySurface != ThemePalette.LIGHT_DEFAULT.background)
        assertTrue(ThemePalette.DARK_DEFAULT.keySurface != ThemePalette.DARK_DEFAULT.background)
    }

    @Test
    fun `preferences round trip keeps every role`() {
        val source = ThemePalette.preset(ThemeMode.DARK, 2)
        val map = source.toPreferences(ThemeMode.DARK)
        assertEquals(source, ThemePalette.fromPreferences(map, ThemeMode.DARK))
    }

    @Test
    fun `parse color accepts rgb and argb`() {
        assertEquals(0xFF12AB34.toInt(), ThemePalette.parseColor("#12ab34"))
        assertEquals(0x8012AB34.toInt(), ThemePalette.parseColor("0x8012AB34"))
        assertNull(ThemePalette.parseColor("#12345"))
    }

    @Test
    fun `configured alpha preserves state alpha`() {
        assertEquals(
            0x80123456.toInt(),
            ThemePalette.applyConfiguredAlpha(0x80FFFFFF.toInt(), 0xFF123456.toInt())
        )
    }

    @Test
    fun `resource classifier identifies gboard semantic colors`() {
        assertEquals(ThemeRole.BACKGROUND, ThemePalette.classifyResourceColor(0xFF202124.toInt(), true))
        assertEquals(ThemeRole.KEY_SURFACE, ThemePalette.classifyResourceColor(0xFF303030.toInt(), true))
        assertEquals(ThemeRole.PRIMARY, ThemePalette.classifyResourceColor(0xFF202124.toInt(), false))
        assertEquals(ThemeRole.ACCENT, ThemePalette.classifyResourceColor(0xFF1A73E8.toInt(), false))
        assertEquals(ThemeRole.ACCENT, ThemePalette.classifyResourceColor(0xFFE8DEF8.toInt(), false))
        assertEquals(ThemeRole.ACCENT, ThemePalette.classifyResourceColor(0xFFE9DDF5.toInt(), false))
        assertEquals(ThemeRole.PRIMARY, ThemePalette.classifyResourceColor(0xFF6B538C.toInt(), false))
    }
}

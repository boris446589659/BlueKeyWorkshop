package com.keyflux

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesMergeTests {

    @Test
    fun `local values override stale provider values`() {
        val merged = PreferencesManager.mergePreferenceMaps(
            local = mapOf("switch" to true),
            provider = mapOf("switch" to false)
        )

        assertEquals(true, merged["switch"])
    }

    @Test
    fun `provider values fill keys missing from local storage`() {
        val merged = PreferencesManager.mergePreferenceMaps(
            local = mapOf("local" to true),
            provider = mapOf("legacy" to 10)
        )

        assertEquals(true, merged["local"])
        assertEquals(10, merged["legacy"])
    }
}

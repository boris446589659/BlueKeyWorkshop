package com.keyflux

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesMergeTests {

    @Test
    fun `standalone app values override legacy local values`() {
        val merged = PreferencesManager.mergePreferenceMaps(
            local = mapOf("switch" to true),
            provider = mapOf("switch" to false)
        )

        assertEquals(false, merged["switch"])
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

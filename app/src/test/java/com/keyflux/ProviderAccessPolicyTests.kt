package com.keyflux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAccessPolicyTests {
    @Test fun `only module and Gboard callers are allowed`() {
        assertTrue(ProviderAccessPolicy.isAllowedCaller(arrayOf(ProviderAccessPolicy.MODULE_PACKAGE)))
        assertTrue(ProviderAccessPolicy.isAllowedCaller(arrayOf("shared.uid", ProviderAccessPolicy.GBOARD_PACKAGE)))
        assertFalse(ProviderAccessPolicy.isAllowedCaller(arrayOf("com.example.untrusted")))
        assertFalse(ProviderAccessPolicy.isAllowedCaller(null))
    }

    @Test fun `only public module preference values are exposed`() {
        assertTrue(ProviderAccessPolicy.isExposedPreference("keyflux_enable_ai"))
        assertFalse(ProviderAccessPolicy.isExposedPreference("keyflux_enable_ai_type"))
        assertFalse(ProviderAccessPolicy.isExposedPreference("unrelated_value"))
    }

    @Test fun `migration accepts only supported primitive values`() {
        assertTrue(ProviderAccessPolicy.isSupportedBundleValue(true))
        assertTrue(ProviderAccessPolicy.isSupportedBundleValue("debug"))
        assertFalse(ProviderAccessPolicy.isSupportedBundleValue(null))
        assertFalse(ProviderAccessPolicy.isSupportedBundleValue(byteArrayOf(1)))
    }
}

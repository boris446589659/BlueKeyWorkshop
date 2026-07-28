package com.keyflux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogLevelTests {
    @Test fun `stored log levels resolve by value`() {
        assertEquals(LogLevel.DEBUG, LogLevel.fromStored("debug", false))
        assertEquals(LogLevel.INFO, LogLevel.fromStored("INFO", false))
        assertEquals(LogLevel.WARN, LogLevel.fromStored("warn", false))
        assertEquals(LogLevel.ERROR, LogLevel.fromStored("error", false))
    }

    @Test fun `legacy enabled log switch keeps debug behavior`() {
        assertEquals(LogLevel.DEBUG, LogLevel.fromStored(null, true))
    }

    @Test fun `invalid or missing level defaults to info`() {
        assertEquals(LogLevel.INFO, LogLevel.fromStored(null, false))
        assertEquals(LogLevel.INFO, LogLevel.fromStored("verbose", false))
    }

    @Test fun `level threshold filters lower severity messages`() {
        assertTrue(LogLevel.DEBUG.includes(LogLevel.DEBUG))
        assertTrue(LogLevel.INFO.includes(LogLevel.ERROR))
        assertFalse(LogLevel.WARN.includes(LogLevel.INFO))
        assertFalse(LogLevel.ERROR.includes(LogLevel.WARN))
    }
}

package com.keyflux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseLearningPolicyTests {
    @Test fun `extracts short Chinese terms without storing sentences or identifiers`() {
        assertEquals(listOf("\u4F60\u597D"), ChineseLearningPolicy.extractTerms("\u4F60\u597D "))
        assertEquals(
            listOf("\u4E2D\u6587\u8F93\u5165", "\u8054\u60F3"),
            ChineseLearningPolicy.extractTerms("\u4E2D\u6587\u8F93\u5165, emoji 123, \u8054\u60F3")
        )
        assertTrue(ChineseLearningPolicy.extractTerms("123456").isEmpty())
        assertTrue(ChineseLearningPolicy.extractTerms("\u8FD9\u662F\u4E00\u6BB5\u8D85\u8FC7\u516B\u4E2A\u5B57\u7684\u8FDE\u7EED\u53E5\u5B50").isEmpty())
    }

    @Test fun `ignores single Han characters and caps terms per commit`() {
        assertTrue(ChineseLearningPolicy.extractTerms("\u4F60").isEmpty())
        assertEquals(
            4,
            ChineseLearningPolicy.extractTerms("\u4F60\u597D,\u4E2D\u6587,\u8BCD\u9891,\u8054\u60F3,\u8868\u60C5").size
        )
    }

    @Test fun `sync thresholds are sparse and frequency is bounded`() {
        assertFalse(ChineseLearningPolicy.shouldSyncToDictionary(2))
        assertTrue(ChineseLearningPolicy.shouldSyncToDictionary(3))
        assertTrue(ChineseLearningPolicy.shouldSyncToDictionary(13))
        assertFalse(ChineseLearningPolicy.shouldSyncToDictionary(14))
        assertEquals(60, ChineseLearningPolicy.dictionaryFrequency(1))
        assertEquals(250, ChineseLearningPolicy.dictionaryFrequency(1000))
    }

    @Test fun `retention favors frequency then recency`() {
        val now = 10L * 86_400_000L
        val frequentOld = ChineseLearningPolicy.retentionScore(5, 0L, now)
        val recentRare = ChineseLearningPolicy.retentionScore(1, now, now)
        assertTrue(frequentOld > recentRare)
    }

    @Test fun `builds bounded context phrases from adjacent Chinese commits`() {
        assertEquals("\u4F60\u597D", ChineseLearningPolicy.contextToken("\u4F60\u597D"))
        assertEquals("\u4F60\u597D\u4E16\u754C", ChineseLearningPolicy.contextPhrase("\u4F60\u597D", "\u4E16\u754C"))
        assertEquals("\u4F60\u597D", ChineseLearningPolicy.contextPhrase("\u4F60", "\u597D"))
        assertEquals(null, ChineseLearningPolicy.contextPhrase(null, "\u4F60\u597D"))
        assertEquals(null, ChineseLearningPolicy.contextToken("\u8FD9\u662F\u4E00\u6BB5\u8FC7\u957F\u7684\u8FDE\u7EED\u6587\u672C"))
    }

    @Test fun `context token rejects boundaries and mixed commits`() {
        assertEquals(null, ChineseLearningPolicy.contextToken("\u4F60\u597D\u3002"))
        assertEquals(null, ChineseLearningPolicy.contextToken("\u4F60\u597D "))
        assertEquals(null, ChineseLearningPolicy.contextToken("abc\u4F60\u597D"))
        assertEquals(null, ChineseLearningPolicy.contextToken("\u4F60\u597D\uFF0C\u4E16\u754C"))
    }

    @Test fun `dictionary locale follows simplified and traditional subtypes`() {
        assertEquals("zh-CN", ChineseLearningPolicy.dictionaryLocaleTag("zh-Hans-CN"))
        assertEquals("zh-TW", ChineseLearningPolicy.dictionaryLocaleTag("zh-Hant"))
        assertEquals("zh-HK", ChineseLearningPolicy.dictionaryLocaleTag("zh-Hant-HK"))
        assertEquals("zh-MO", ChineseLearningPolicy.dictionaryLocaleTag("", "zh_MO"))
        assertEquals("zh-CN", ChineseLearningPolicy.dictionaryLocaleTag("en-US"))
    }

    @Test fun `only ordinary text fields are eligible for learning`() {
        assertTrue(ChineseLearningPolicy.isEligibleInputField(0x00000001, 0))
        assertFalse(ChineseLearningPolicy.isEligibleInputField(0x00000081, 0))
        assertFalse(ChineseLearningPolicy.isEligibleInputField(0x00000002, 0))
        assertFalse(ChineseLearningPolicy.isEligibleInputField(0x00080001, 0))
        assertFalse(ChineseLearningPolicy.isEligibleInputField(0x00000001, 0x01000000))
    }
}

package com.keyflux

import java.util.Locale

internal object ChineseLearningPolicy {
    const val MAX_TERMS = 256
    const val MAX_PROMOTED_TERMS = 64
    const val MAX_COMMITTED_TEXT_LENGTH = 64

    private val hanRun = Regex("\\p{IsHan}+")
    private val syncCounts = setOf(3, 5, 8, 13, 21, 34, 55, 89)

    fun extractTerms(committedText: CharSequence): List<String> {
        val text = committedText.toString().trim()
        if (text.isEmpty() || text.length > MAX_COMMITTED_TEXT_LENGTH) return emptyList()

        return hanRun.findAll(text)
            .map { it.value }
            .filter { it.length in 2..8 }
            .distinct()
            .take(4)
            .toList()
    }

    fun contextToken(committedText: CharSequence): String? {
        val text = committedText.toString()
        if (text.isEmpty() || text.length > MAX_COMMITTED_TEXT_LENGTH) return null
        return hanRun.matchEntire(text)
            ?.value
            ?.takeIf { it.length in 1..4 }
    }

    fun contextPhrase(previous: String?, current: String?): String? {
        if (previous.isNullOrEmpty() || current.isNullOrEmpty()) return null
        val phrase = previous + current
        return phrase.takeIf { it.length in 2..8 && it != previous && it != current }
    }

    fun dictionaryLocaleTag(languageTag: String?, legacyLocale: String? = null): String {
        val rawLocale = languageTag?.takeIf { it.isNotBlank() }
            ?: legacyLocale?.takeIf { it.isNotBlank() }
            ?: return "zh-CN"
        val subtags = rawLocale
            .replace('_', '-')
            .lowercase(Locale.ROOT)
            .split('-')
            .filterTo(HashSet()) { it.isNotBlank() }
        if ("zh" !in subtags) return "zh-CN"
        return when {
            "hk" in subtags -> "zh-HK"
            "mo" in subtags -> "zh-MO"
            "tw" in subtags || "hant" in subtags -> "zh-TW"
            else -> "zh-CN"
        }
    }

    fun isEligibleInputField(inputType: Int, imeOptions: Int): Boolean {
        if (imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        if (inputType and TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false

        val inputClass = inputType and TYPE_MASK_CLASS
        val variation = inputType and TYPE_MASK_VARIATION
        if (inputClass == TYPE_CLASS_TEXT && variation in TEXT_PASSWORD_VARIATIONS) return false
        if (inputClass == TYPE_CLASS_NUMBER && variation == TYPE_NUMBER_VARIATION_PASSWORD) return false
        return inputClass == TYPE_CLASS_TEXT
    }

    fun shouldSyncToDictionary(count: Int): Boolean = count in syncCounts

    fun dictionaryFrequency(count: Int): Int =
        (56 + count.coerceAtMost(97) * 2).coerceIn(60, 250)

    fun retentionScore(count: Int, lastUsedAt: Long, now: Long): Long {
        val ageDays = ((now - lastUsedAt).coerceAtLeast(0L) / DAY_MILLIS).coerceAtMost(365L)
        val recency = 365L - ageDays
        return count.coerceAtLeast(1).toLong() * 1_000L + recency
    }

    private const val DAY_MILLIS = 86_400_000L
    private const val TYPE_MASK_CLASS = 0x0000000f
    private const val TYPE_MASK_VARIATION = 0x00000ff0
    private const val TYPE_CLASS_TEXT = 0x00000001
    private const val TYPE_CLASS_NUMBER = 0x00000002
    private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    private const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
    private const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x00080000
    private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000
    private val TEXT_PASSWORD_VARIATIONS = setOf(
        TYPE_TEXT_VARIATION_PASSWORD,
        TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        TYPE_TEXT_VARIATION_WEB_PASSWORD
    )
}

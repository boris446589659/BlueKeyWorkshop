package com.keyflux

import android.content.ContentValues
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.provider.UserDictionary
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Observes text committed by Gboard and promotes repeated Chinese terms to the
 * system user dictionary. All modeling and provider I/O stays off the IME thread.
 */
internal object ChineseLearningHooker {
    private const val MODEL_FILE = "keyflux_chinese_model"
    private const val MODEL_KEY = "term_stats_v1"
    private const val CONTEXT_WINDOW_MILLIS = 30_000L
    private const val DUPLICATE_WINDOW_MILLIS = 80L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeyFlux-ChineseLearning").apply { isDaemon = true }
    }
    private val hookedCommitMethods = Collections.synchronizedSet(HashSet<String>())
    private val sessionLock = Any()

    @Volatile private var editorEligible = false
    @Volatile private var applicationContext: Context? = null
    @Volatile private var dictionaryLocale: Locale = Locale.SIMPLIFIED_CHINESE
    @Volatile private var model: LocalModel? = null
    private var recentChineseToken: String? = null
    private var recentTokenAt = 0L
    private var lastCommitText = ""
    private var lastCommitAt = 0L

    fun hook(plugin: PluginEntry, classLoader: ClassLoader) {
        val serviceClasses = LinkedHashSet<Class<*>>()
        serviceClasses.add(InputMethodService::class.java)
        runCatching {
            serviceClasses.add(
                XposedHelpers.findClass("com.android.inputmethod.latin.LatinIME", classLoader)
            )
        }.onFailure { plugin.logWarning("LatinIME class not found for Chinese learning: ${it.message}") }

        var hooks = 0
        for (serviceClass in serviceClasses) {
            val method = runCatching {
                serviceClass.getDeclaredMethod(
                    "onStartInput",
                    EditorInfo::class.java,
                    Boolean::class.javaPrimitiveType
                ).apply { isAccessible = true }
            }.getOrNull() ?: continue

            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val editorInfo = param.args.getOrNull(0) as? EditorInfo
                    synchronized(sessionLock) {
                        editorEligible = editorInfo != null && ChineseLearningPolicy.isEligibleInputField(
                            editorInfo.inputType,
                            editorInfo.imeOptions
                        )
                        recentChineseToken = null
                        recentTokenAt = 0L
                        lastCommitText = ""
                        lastCommitAt = 0L
                    }
                }

                @Suppress("DEPRECATION")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val service = param.thisObject as? InputMethodService ?: return
                    applicationContext = service.applicationContext ?: service
                    val inputMethodManager = service.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? InputMethodManager
                    val subtype = runCatching {
                        inputMethodManager?.currentInputMethodSubtype
                    }.getOrNull()
                    dictionaryLocale = Locale.forLanguageTag(
                        ChineseLearningPolicy.dictionaryLocaleTag(
                            subtype?.languageTag,
                            subtype?.locale
                        )
                    )
                    val connection = runCatching { service.currentInputConnection }.getOrNull() ?: return
                    hookCommitMethods(plugin, connection.javaClass)
                }
            })
            hooks++
        }

        check(hooks > 0) { "No compatible InputMethodService#onStartInput method found" }
        plugin.logAlways("Chinese learning observer installed on $hooks input service method(s)")
    }

    private fun hookCommitMethods(plugin: PluginEntry, connectionClass: Class<*>) {
        val methods = LinkedHashSet<Method>()
        var type: Class<*>? = connectionClass
        while (type != null && type != Any::class.java) {
            type.declaredMethods
                .filterTo(methods) { method ->
                    method.name == "commitText" &&
                        !Modifier.isAbstract(method.modifiers) &&
                        method.parameterTypes.firstOrNull()?.let {
                            CharSequence::class.java.isAssignableFrom(it)
                        } == true
                }
            type = type.superclass
        }

        for (method in methods) {
            val signature = method.toGenericString()
            if (!hookedCommitMethods.add(signature)) continue
            try {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result == false) return
                        val committedText = param.args.getOrNull(0) as? CharSequence ?: return
                        observeCommit(plugin, committedText)
                    }
                })
                plugin.logDebug("Observing committed text through $signature")
            } catch (t: Throwable) {
                hookedCommitMethods.remove(signature)
                plugin.logWarning("Failed to observe $signature: ${t.message}")
            }
        }
    }

    private fun observeCommit(plugin: PluginEntry, committedText: CharSequence) {
        if (!editorEligible || !plugin.enableChineseLearning ||
            !plugin.enableAdaptiveChineseLearning || plugin.forceIncognito ||
            plugin.enablePrivacy || plugin.isCurrentFieldSecure
        ) return

        val rawText = committedText.toString()
        if (rawText.isEmpty() || plugin.isSensitiveText(rawText)) return

        val now = SystemClock.elapsedRealtime()
        val terms = LinkedHashSet(ChineseLearningPolicy.extractTerms(rawText))
        val currentToken = ChineseLearningPolicy.contextToken(rawText)
        synchronized(sessionLock) {
            if (rawText == lastCommitText && now - lastCommitAt <= DUPLICATE_WINDOW_MILLIS) return
            lastCommitText = rawText
            lastCommitAt = now

            val previous = recentChineseToken.takeIf {
                now - recentTokenAt <= CONTEXT_WINDOW_MILLIS
            }
            if (plugin.enableChineseSuggestions) {
                ChineseLearningPolicy.contextPhrase(previous, currentToken)?.let(terms::add)
            }
            if (currentToken != null) {
                recentChineseToken = currentToken
                recentTokenAt = now
            } else {
                recentChineseToken = null
                recentTokenAt = 0L
            }
        }
        if (terms.isEmpty()) return

        val context = applicationContext ?: return
        val locale = dictionaryLocale
        executor.execute {
            try {
                val promotions = localModel(context).record(terms, System.currentTimeMillis())
                for (promotion in promotions) {
                    syncToUserDictionary(context, promotion.term, promotion.frequency, locale)
                    plugin.logDebug(
                        "Promoted learned Chinese term of length ${promotion.term.length} " +
                            "at count ${promotion.count}"
                    )
                }
            } catch (t: Throwable) {
                plugin.logWarning("Chinese learning update failed: ${t.message}")
            }
        }
    }

    private fun localModel(context: Context): LocalModel {
        model?.let { return it }
        return synchronized(this) {
            model ?: LocalModel(context).also { model = it }
        }
    }

    @Suppress("DEPRECATION")
    private fun syncToUserDictionary(
        context: Context,
        term: String,
        frequency: Int,
        locale: Locale
    ) {
        val resolver = context.contentResolver
        val selection = "${UserDictionary.Words.WORD}=? AND ${UserDictionary.Words.LOCALE}=?"
        val selectionArgs = arrayOf(term, locale.toString())
        val currentFrequency = resolver.query(
            UserDictionary.Words.CONTENT_URI,
            arrayOf(UserDictionary.Words.FREQUENCY),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

        if (currentFrequency == null) {
            UserDictionary.Words.addWord(context, term, frequency, null, locale)
            return
        }
        if (currentFrequency >= frequency) return

        val values = ContentValues().apply {
            put(UserDictionary.Words.FREQUENCY, frequency)
        }
        resolver.update(UserDictionary.Words.CONTENT_URI, values, selection, selectionArgs)
    }

    private data class Promotion(val term: String, val count: Int, val frequency: Int)

    private class LocalModel(context: Context) {
        private data class Stats(var count: Int, var lastUsedAt: Long, var promoted: Boolean)

        private val preferences = run {
            val storageContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            storageContext.getSharedPreferences(MODEL_FILE, Context.MODE_PRIVATE)
        }
        private val terms = load()

        @Synchronized
        fun record(observedTerms: Set<String>, now: Long): List<Promotion> {
            val promotions = ArrayList<Promotion>()
            for (term in observedTerms) {
                val stats = terms.getOrPut(term) { Stats(0, now, false) }
                stats.count = (stats.count + 1).coerceAtMost(10_000)
                stats.lastUsedAt = now
                val mayPromote = stats.promoted ||
                    terms.values.count { it.promoted } < ChineseLearningPolicy.MAX_PROMOTED_TERMS
                if (mayPromote && ChineseLearningPolicy.shouldSyncToDictionary(stats.count)) {
                    stats.promoted = true
                    promotions.add(
                        Promotion(
                            term,
                            stats.count,
                            ChineseLearningPolicy.dictionaryFrequency(stats.count)
                        )
                    )
                }
            }
            trim(now)
            persist()
            return promotions
        }

        private fun trim(now: Long) {
            if (terms.size <= ChineseLearningPolicy.MAX_TERMS) return
            val retained = terms.entries
                .sortedByDescending { (_, stats) ->
                    ChineseLearningPolicy.retentionScore(stats.count, stats.lastUsedAt, now)
                }
                .take(ChineseLearningPolicy.MAX_TERMS)
                .mapTo(HashSet()) { it.key }
            terms.keys.retainAll(retained)
        }

        private fun load(): LinkedHashMap<String, Stats> {
            val result = LinkedHashMap<String, Stats>()
            val encrypted = preferences.getString(MODEL_KEY, null) ?: return result
            runCatching {
                val json = JSONObject(CryptoHelper.decrypt(encrypted))
                val keys = json.keys()
                while (keys.hasNext()) {
                    val term = keys.next()
                    if (term.length !in 2..8) continue
                    val values = json.optJSONArray(term) ?: continue
                    val count = values.optInt(0, 0)
                    val lastUsedAt = values.optLong(1, 0L)
                    val promoted = values.optBoolean(2, false)
                    if (count > 0) result[term] = Stats(count, lastUsedAt, promoted)
                }
            }
            return result
        }

        private fun persist() {
            val json = JSONObject()
            for ((term, stats) in terms) {
                json.put(
                    term,
                    JSONArray().put(stats.count).put(stats.lastUsedAt).put(stats.promoted)
                )
            }
            preferences.edit()
                .putString(MODEL_KEY, CryptoHelper.encrypt(json.toString()))
                .apply()
        }
    }
}

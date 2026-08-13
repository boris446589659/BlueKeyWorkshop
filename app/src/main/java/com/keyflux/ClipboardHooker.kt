package com.keyflux

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ClipboardHooker {

    private val SENSITIVE_TEXT_COLUMNS = setOf("text", "html_text")
    private const val ENABLE_PROVIDER_QUERY_OVERRIDES = false

    internal fun containsSensitiveClipboardText(values: Map<String, String?>): Boolean =
        values.any { (key, value) ->
            key.lowercase(Locale.ROOT) in SENSITIVE_TEXT_COLUMNS &&
                value != null && PreferencesManager.detectSensitiveText(value)
        }

    /** Cached discovered class name to avoid re-running DexKit on subsequent hooks. */
    @Volatile
    private var discoveredClassName: String? = null

    /** Hardcoded fallback candidates (kept for when DexKit discovery fails). */
    private val CLIPBOARD_PROVIDER_CANDIDATES = listOf(
        "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider",
        "com.google.android.inputmethod.latin.clipboard.ClipboardContentProvider",
        "com.google.android.apps.inputmethod.latin.clipboard.ClipboardContentProvider"
    )

    /**
     * Dynamically discover the ClipboardContentProvider class using DexKit.
     *
     * Strategy (multi-query, sequential):
     * 1. ContentProvider superclass + "clip" string
     * 2. ContentProvider superclass + "timestamp" string
     * 3. ContentProvider superclass + "content://" URI pattern
     * 4. ContentProvider superclass + "inputmethod" string (broad, last resort)
     *
     * Falls back to hardcoded candidates if DexKit fails.
     */
    private fun findClipboardProviderClass(
        plugin: PluginEntry,
        classLoader: ClassLoader,
        log: (String) -> Unit
    ): Class<*>? {
        // Return cached result if available
        discoveredClassName?.let { name ->
            try {
                val clazz = XposedHelpers.findClass(name, classLoader)
                log("ClipboardContentProvider (cached): $name")
                return clazz
            } catch (t: Throwable) {
                log("Cached class $name no longer resolvable, re-discovering...")
                discoveredClassName = null
            }
        }

        // Try DexKit discovery
        val dexkitClass = findViaDexKit(plugin, classLoader, log)
        if (dexkitClass != null) return dexkitClass

        // Fallback: try hardcoded candidates
        return findViaCandidates(classLoader, log)
    }

    /**
     * Use DexKit to search for the clipboard ContentProvider dynamically.
     */
    private fun findViaDexKit(plugin: PluginEntry, classLoader: ClassLoader, log: (String) -> Unit): Class<*>? =
        plugin.withDexKitBridge(classLoader) bridgeBlock@ { bridge ->
        try {
            log("Using shared DexKit bridge for clipboard discovery")

            // Query 1: ContentProvider with "clip" string
            val result1 = try {
                bridge.findClass {
                    matcher {
                        superClass("android.content.ContentProvider")
                        addUsingString("clip")
                    }
                }.let { results ->
                    results.singleOrNull { it.name.contains("clipboard", ignoreCase = true) }
                        ?: results.singleOrNull()
                }
            } catch (t: Throwable) { null }

            if (result1 != null) {
                val name = result1.name
                discoveredClassName = name
                log("DexKit found clipboard provider via 'clip' string: $name")
                return@bridgeBlock XposedHelpers.findClass(name, classLoader)
            }

            // Query 2: ContentProvider with "timestamp" string
            val result2 = try {
                bridge.findClass {
                    matcher {
                        superClass("android.content.ContentProvider")
                        addUsingString("timestamp")
                    }
                }.let { results ->
                    results.singleOrNull { it.name.contains("clipboard", ignoreCase = true) }
                        ?: results.singleOrNull()
                }
            } catch (t: Throwable) { null }

            if (result2 != null) {
                val name = result2.name
                discoveredClassName = name
                log("DexKit found clipboard provider via 'timestamp' string: $name")
                return@bridgeBlock XposedHelpers.findClass(name, classLoader)
            }

            // Query 3: ContentProvider with "content://" URI
            val result3 = try {
                bridge.findClass {
                    matcher {
                        superClass("android.content.ContentProvider")
                        addUsingString("content://")
                    }
                }.let { results ->
                    results.singleOrNull {
                        it.name.contains("clipboard", ignoreCase = true) ||
                            it.name.contains("inputmethod", ignoreCase = true)
                    } ?: results.singleOrNull()
                }
            } catch (t: Throwable) { null }

            if (result3 != null) {
                val name = result3.name
                // Verify it's clipboard/inputmethod-related
                if (name.contains("clip", ignoreCase = true) || name.contains("inputmethod", ignoreCase = true)) {
                    discoveredClassName = name
                    log("DexKit found clipboard provider via 'content://' URI: $name")
                    return@bridgeBlock XposedHelpers.findClass(name, classLoader)
                }
                // Check if it has clipboard-specific string patterns
                val hasClipboardStrings = try {
                    val classData = bridge.getClassData(name)
                    if (classData != null) {
                        val methods = classData.findMethod {
                            matcher {
                                addUsingString("timestamp")
                                addUsingString("limit")
                            }
                        }
                        methods.isNotEmpty()
                    } else false
                } catch (t: Throwable) { false }

                if (hasClipboardStrings) {
                    discoveredClassName = name
                    log("DexKit found clipboard provider via 'content://' + timestamp+limit: $name")
                    return@bridgeBlock XposedHelpers.findClass(name, classLoader)
                }
            }

            // Query 4: Any ContentProvider in the Gboard package with "inputmethod" string
            val result4 = try {
                bridge.findClass {
                    matcher {
                        superClass("android.content.ContentProvider")
                        addUsingString("inputmethod")
                    }
                }.let { results ->
                    results.singleOrNull { it.name.contains("clipboard", ignoreCase = true) }
                        ?: results.singleOrNull()
                }
            } catch (t: Throwable) { null }

            if (result4 != null) {
                val name = result4.name
                discoveredClassName = name
                log("DexKit found clipboard provider via 'inputmethod' string: $name")
                return@bridgeBlock XposedHelpers.findClass(name, classLoader)
            }

            log("DexKit clipboard discovery: no match found across all queries")
            null
        } catch (t: Throwable) {
            log("DexKit clipboard discovery failed: ${t.message}")
            null
        }
    }

    /**
     * Fallback: try hardcoded candidate class names.
     */
    private fun findViaCandidates(classLoader: ClassLoader, log: (String) -> Unit): Class<*>? {
        for (candidate in CLIPBOARD_PROVIDER_CANDIDATES) {
            try {
                val clazz = XposedHelpers.findClass(candidate, classLoader)
                discoveredClassName = candidate
                log("Found ClipboardContentProvider via candidate: $candidate")
                return clazz
            } catch (t: Throwable) { /* try next */ }
        }
        log("ClipboardContentProvider class not found (DexKit + candidates exhausted)")
        return null
    }

    fun hook(plugin: PluginEntry, classLoader: ClassLoader) {
        plugin.apply {
            val providerClass = findClipboardProviderClass(this, classLoader) { log(it) }

            if (providerClass != null) {
                hookClipboardProvider(this, classLoader, providerClass)
            } else {
                logAlways("ClipboardContentProvider class not found — clipboard hooks skipped. " +
                    "Device: ${CompatibilityManager.manufacturer} ${android.os.Build.MODEL}, " +
                    "Android: ${CompatibilityManager.androidVersion}")
            }

            // InputMethodService hooks (Android framework, always available)
            hookInputMethodService(this, classLoader)
        }
    }

    private fun hookClipboardProvider(plugin: PluginEntry, classLoader: ClassLoader, providerClass: Class<*>) {
        plugin.apply {
            // --- onCreate: fallback initialization from ContentProvider context ---
            tryHook("ClipboardContentProvider#onCreate") { _ ->
                findAndHookMethod(
                    providerClass, "onCreate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val context = XposedHelpers.callMethod(param.thisObject, "getContext") as? Context ?: return
                                initializeOnce(context, classLoader, "ClipboardContentProvider#onCreate")
                            } catch (t: Throwable) {
                                logAlways("Error during ClipboardContentProvider#onCreate: ${t.message}")
                            }
                        }
                    }
                )
            }

            // Gboard 17.8.4 uses this query for multiple internal views. Rewriting its
            // positional arguments can hide freshly inserted clips, so keep it native.
            if (ENABLE_PROVIDER_QUERY_OVERRIDES) tryHook("ClipboardContentProvider#query") { _ ->
                findAndHookMethod(
                    providerClass, "query",
                    Uri::class.java, Array<String>::class.java, String::class.java,
                    Array<String>::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                if (param.args.size < 5) return
                                val selection = param.args[2]?.toString() ?: ""
                                val selectionArgs = param.args[3] as? Array<String>
                                val sortOrder = param.args[4]?.toString()
                                val diagnosticArgs = selectionArgs?.joinToString(",") { value ->
                                    value.toLongOrNull()?.toString() ?: "<text>"
                                }
                                logDebug(
                                    "Clipboard query: selection=$selection, args=[$diagnosticArgs], " +
                                        "sortOrder=$sortOrder"
                                )

                                // Modify time limit
                                val timeIdx = selection.indexOf("timestamp >= ?")
                                if (timeIdx != -1) {
                                    var questionMarkCount = 0
                                    for (i in 0 until timeIdx) {
                                        if (selection[i] == '?') questionMarkCount++
                                    }
                                    val textTime = clipboardTextTime
                                    val afterTimestamp = if (textTime < 0L) 0L else (System.currentTimeMillis() - textTime)
                                    selectionArgs?.let {
                                        if (questionMarkCount < it.size) {
                                            it[questionMarkCount] = afterTimestamp.toString()
                                            param.args[3] = it
                                            log("Modified time limit: ${if (textTime < 0L) "Forever" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(afterTimestamp))}")
                                        }
                                    }
                                }

                                // Modify size limit — match any "limit N" pattern
                                if (sortOrder != null && sortOrder.contains("timestamp DESC limit")) {
                                    param.args[4] = "timestamp DESC limit $clipboardTextSize"
                                    log("Modified size limit to $clipboardTextSize")
                                }
                            } catch (t: Throwable) {
                                log("Error in query hook: ${t.message}")
                            }
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val cursor = param.result as? Cursor
                                log("query end, size=${cursor?.count ?: "null"}")
                            } catch (t: Throwable) { /* ignore */ }
                        }
                    }
                )
            }

            // --- insert: block sensitive clipboard entries ---
            tryHook("ClipboardContentProvider#insert") { _ ->
                findAndHookMethod(
                    providerClass, "insert",
                    Uri::class.java, ContentValues::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                if (!secureClipboard) return
                                val values = param.args[1] as? ContentValues ?: return
                                if (isCurrentFieldSecure) {
                                    log("Blocked clipboard: copied from secure field")
                                    param.result = null
                                    return
                                }
                                val clipboardText = values.keySet().associateWith(values::getAsString)
                                if (containsSensitiveClipboardText(clipboardText)) {
                                    log("Blocked clipboard: matches sensitive pattern")
                                    param.result = null
                                    return
                                }
                            } catch (t: Throwable) {
                                log("Error in insert hook: ${t.message}")
                            }
                        }
                    }
                )
            }
        }
    }

    private fun hookInputMethodService(plugin: PluginEntry, classLoader: ClassLoader) {
        plugin.apply {
            tryHook("InputMethodService#onStartInput") {
                findAndHookMethod(
                    "android.inputmethodservice.InputMethodService", classLoader,
                    "onStartInput",
                    android.view.inputmethod.EditorInfo::class.java,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                // Fail open if Gboard supplies incomplete editor metadata. Keeping
                                // the previous value could incorrectly block a later normal clip.
                                isCurrentFieldSecure = false
                                val editorInfo = param.args[0] as? android.view.inputmethod.EditorInfo ?: return
                                val inputType = editorInfo.inputType
                                val isPassword = (inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT &&
                                    ((inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                                     (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                                     (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                                val isNumberPassword = (inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER &&
                                    (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                                isCurrentFieldSecure = isPassword || isNumberPassword
                                if (forceIncognito) {
                                    editorInfo.imeOptions = editorInfo.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                                }
                            } catch (t: Throwable) {
                                isCurrentFieldSecure = false
                                log("Error in onStartInput hook: ${t.message}")
                            }
                        }
                    }
                )
            }

            tryHook("InputMethodService#onFinishInput") {
                findAndHookMethod(
                    "android.inputmethodservice.InputMethodService", classLoader,
                    "onFinishInput",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            isCurrentFieldSecure = false
                        }
                    }
                )
            }

            tryHook("InputMethodService#onFinishInputView") {
                findAndHookMethod(
                    "android.inputmethodservice.InputMethodService", classLoader,
                    "onFinishInputView",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            isCurrentFieldSecure = false
                        }
                    }
                )
            }
        }
    }

}

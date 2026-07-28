package com.keyflux

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Modifier

/**
 * Applies the palette at the two points used by recent Gboard builds:
 * Android resources are the compatibility fallback, while apbv's style
 * resolver carries the semantic selector (.key, .candidates, etc.).
 */
object ThemeHooker {
    fun hook(plugin: PluginEntry, classLoader: ClassLoader) {
        plugin.apply {
            hookResourceColors(classLoader)
            hookStyleResolver(classLoader)
            hookRuntimeColorHandlers(classLoader)
        }
    }

    private fun PluginEntry.hookResourceColors(classLoader: ClassLoader) {
        tryHook("Resources#getColor(Int)") {
            val method = XposedHelpers.findMethodExact(
                Resources::class.java,
                "getColor",
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(method, resourceColorHook())
        }

        tryHook("Resources#getColor(Int, Theme)") {
            val method = XposedHelpers.findMethodExact(
                Resources::class.java,
                "getColor",
                Int::class.javaPrimitiveType,
                Resources.Theme::class.java
            )
            XposedBridge.hookMethod(method, resourceColorHook())
        }

        tryHook("TypedArray#getColor(Int, Int)") {
            val method = XposedHelpers.findMethodExact(
                TypedArray::class.java,
                "getColor",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!themeEnabled()) return
                    val typedArray = param.thisObject as? TypedArray ?: return
                    val index = param.args[0] as Int
                    val result = param.result as Int
                    val colorId = typedArray.getResourceId(index, 0)
                    if (colorId == 0) return
                    val resources = typedArray.resources
                    val dark = resources.isDarkMode()
                    overrideColor(resources, colorId, result, dark)?.let { param.result = it }
                }
            })
        }

        tryHook("Resources#getColorStateList(Int)") {
            val method = XposedHelpers.findMethodExact(
                Resources::class.java,
                "getColorStateList",
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(method, colorStateListHook())
        }

        tryHook("Resources#getColorStateList(Int, Theme)") {
            val method = XposedHelpers.findMethodExact(
                Resources::class.java,
                "getColorStateList",
                Int::class.javaPrimitiveType,
                Resources.Theme::class.java
            )
            XposedBridge.hookMethod(method, colorStateListHook())
        }

        tryHook("TypedArray#getColorStateList(Int)") {
            val method = XposedHelpers.findMethodExact(
                TypedArray::class.java,
                "getColorStateList",
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!themeEnabled()) return
                    val typedArray = param.thisObject as? TypedArray ?: return
                    val original = param.result as? ColorStateList ?: return
                    val id = typedArray.getResourceId(param.args[0] as Int, 0)
                    if (id == 0) return
                    param.result = recolorResourceStateList(
                        typedArray.resources,
                        id,
                        original,
                        typedArray.resources.isDarkMode()
                    )
                }
            })
        }
    }

    private fun PluginEntry.resourceColorHook() = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!themeEnabled() && !enableAmoled) return
            val resources = param.thisObject as? Resources ?: return
            val dark = resources.isDarkMode()
            if (!enableCustomTheme && !dark) return
            val id = param.args[0] as Int
            val result = param.result as Int
            overrideColor(resources, id, result, dark)?.let { param.result = it }
        }
    }

    private fun PluginEntry.colorStateListHook() = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!themeEnabled()) return
            val resources = param.thisObject as? Resources ?: return
            val original = param.result as? ColorStateList ?: return
            val id = param.args[0] as Int
            param.result = recolorResourceStateList(resources, id, original, resources.isDarkMode())
        }
    }

    private fun PluginEntry.hookStyleResolver(classLoader: ClassLoader) {
        if (!enableCustomTheme) {
            log("Custom theme style resolver is installed lazily when enabled")
        }
        try {
            val resolverClass = XposedHelpers.findClass("defpackage.apbv", classLoader)
            val method = XposedHelpers.findMethodExact(
                resolverClass,
                "a",
                String::class.java,
                java.util.List::class.java
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!enableCustomTheme) return
                    val result = param.result as? List<*> ?: return
                    val selector = stylePath(param.args[0] as? String, param.args[1] as? List<*>)
                    val dark = (param.thisObject as? Any)?.let {
                        runCatching {
                            XposedHelpers.callMethod(it, "getContext") as? android.content.Context
                        }.getOrNull()?.resources?.isDarkMode()
                    } ?: true
                    applyStylePalette(result, selector, dark)
                }
            })
            logAlways("Hooked Gboard semantic style resolver ${resolverClass.name}#${method.name}")
        } catch (error: Throwable) {
            log("Semantic style resolver unavailable: ${error.message}")
        }
    }

    /**
     * Dynamic-color keyboards create the final key drawable after the selector resolver runs.
     * Recolor these handler instances immediately before they apply their ColorStateLists.
     */
    private fun PluginEntry.hookRuntimeColorHandlers(classLoader: ClassLoader) {
        val handlerNames = listOf("apdy", "apep", "apet", "apff", "apec", "apei", "apee", "apek")
        for (name in handlerNames) {
            try {
                val handlerClass = XposedHelpers.findClass("defpackage.$name", classLoader)
                XposedHelpers.findAndHookMethod(handlerClass, "d", View::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!enableCustomTheme) return
                        val view = param.args.firstOrNull() as? View ?: return
                        replaceRecognizedColorStateLists(param.thisObject, view.resources.isDarkMode())
                    }
                })
            } catch (error: Throwable) {
                log("Runtime color handler unavailable: $name: ${error.message}")
            }
        }
        logAlways("Hooked Gboard runtime key color handlers")
    }

    private fun PluginEntry.applyStylePalette(
        handlers: List<*>,
        selector: String,
        dark: Boolean
    ) {
        val palette = themePalette(dark)
        for (handler in handlers) {
            if (handler == null) continue
            try {
                val className = handler.javaClass.name
                val role = when {
                    className == "defpackage.apdy" -> backgroundRole(selector)
                    className == "defpackage.apep" -> foregroundRole(selector)
                    className == "defpackage.apet" -> ThemeRole.SECONDARY
                    className == "defpackage.apff" -> ThemeRole.KEY_PRESSED
                    // These handlers own the ColorStateLists used by Gboard key drawables.
                    className == "defpackage.apec" ||
                        className == "defpackage.apei" ||
                        className == "defpackage.apee" ||
                        className == "defpackage.apek" -> backgroundRole(selector)
                    else -> null
                } ?: continue

                val target = if (className == "defpackage.apep" && isActionSelector(selector)) {
                    ThemePalette.contrastColor(palette.accent)
                } else {
                    palette.color(role)
                }
                replaceColorStateLists(handler, target)
            } catch (error: Throwable) {
                log("Failed to apply style color for ${handler.javaClass.name}: ${error.message}")
            }
        }
    }

    private fun PluginEntry.replaceColorStateLists(handler: Any, target: Int) {
        var current: Class<*>? = handler.javaClass
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type != ColorStateList::class.java) continue
                val original = try {
                    field.isAccessible = true
                    field.get(handler) as? ColorStateList
                } catch (_: Throwable) {
                    null
                } ?: continue
                XposedHelpers.setObjectField(handler, field.name, recolorColorStateList(original, target))
            }
            current = current.superclass
        }
    }

    private fun PluginEntry.replaceRecognizedColorStateLists(handler: Any, dark: Boolean) {
        val palette = themePalette(dark)
        var current: Class<*>? = handler.javaClass
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type != ColorStateList::class.java) continue
                val original = try {
                    field.isAccessible = true
                    field.get(handler) as? ColorStateList
                } catch (_: Throwable) {
                    null
                } ?: continue
                val replaced = recolorRecognizedColorStateList(original, palette, dark)
                if (replaced !== original) {
                    XposedHelpers.setObjectField(handler, field.name, replaced)
                }
            }
            current = current.superclass
        }
    }

    private fun backgroundRole(selector: String): ThemeRole? = when {
        isActionSelector(selector) || selector.contains("selected") || selector.contains("highlight") ->
            ThemeRole.ACCENT
        selector.contains(".key") || selector.contains("space_bar") || selector.contains("popup-item") ->
            ThemeRole.KEY_SURFACE
        selector.contains("keyboard") || selector.contains("candidate") || selector.contains("header") ||
            selector.contains("navbar") || selector.contains("body") -> ThemeRole.BACKGROUND
        else -> null
    }

    private fun foregroundRole(selector: String): ThemeRole? = when {
        selector.contains("secondary") || selector.contains("hint") || selector.contains("candidate") ->
            ThemeRole.SECONDARY
        else -> ThemeRole.PRIMARY
    }

    private fun isActionSelector(selector: String): Boolean =
        selector.contains(".action") || selector.contains("action")

    private fun stylePath(selector: String?, parents: List<*>?): String =
        ((parents ?: emptyList<Any?>()).mapNotNull { it?.toString() } + listOfNotNull(selector))
            .joinToString("|")
            .lowercase()

    private fun PluginEntry.recolorResourceStateList(
        resources: Resources,
        id: Int,
        original: ColorStateList,
        dark: Boolean
    ): ColorStateList {
        val states = original.stateSpecsOrNull() ?: return original
        val colors = original.colorsOrNull() ?: return original
        val mapped = colors.map { overrideColor(resources, id, it, dark) ?: it }.toIntArray()
        if (mapped.contentEquals(colors)) return original
        return ColorStateList(states, mapped)
    }

    private fun PluginEntry.recolorColorStateList(
        original: ColorStateList,
        target: Int
    ): ColorStateList {
        val states = original.stateSpecsOrNull() ?: return ColorStateList.valueOf(
            ThemePalette.applyConfiguredAlpha(original.defaultColor, target)
        )
        val colors = original.colorsOrNull() ?: return ColorStateList.valueOf(
            ThemePalette.applyConfiguredAlpha(original.defaultColor, target)
        )
        val mapped = colors.map { ThemePalette.applyConfiguredAlpha(it, target) }.toIntArray()
        return ColorStateList(states, mapped)
    }

    private fun recolorRecognizedColorStateList(
        original: ColorStateList,
        palette: ThemePalette,
        dark: Boolean
    ): ColorStateList {
        val states = original.stateSpecsOrNull() ?: return original
        val colors = original.colorsOrNull() ?: return original
        val mapped = colors.map { color ->
            val role = ThemePalette.classifyResourceColor(color, dark)
            if (role == null) color else ThemePalette.applyConfiguredAlpha(color, palette.color(role))
        }.toIntArray()
        return if (mapped.contentEquals(colors)) original else ColorStateList(states, mapped)
    }

    private fun ColorStateList.stateSpecsOrNull(): Array<IntArray>? = try {
        @Suppress("UNCHECKED_CAST")
        (XposedHelpers.getObjectField(this, "mStateSpecs") as? Array<IntArray>)?.map { it.clone() }?.toTypedArray()
    } catch (_: Throwable) {
        null
    }

    private fun ColorStateList.colorsOrNull(): IntArray? = try {
        (XposedHelpers.getObjectField(this, "mColors") as? IntArray)?.clone()
    } catch (_: Throwable) {
        null
    }

    private fun Resources.isDarkMode(): Boolean =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun PluginEntry.themeEnabled(): Boolean = enableCustomTheme
}

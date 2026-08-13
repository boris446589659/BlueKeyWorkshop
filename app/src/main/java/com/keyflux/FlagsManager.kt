package com.keyflux

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.wrap.DexMethod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object FlagsManager {
    private val hookedTarget = AtomicReference<String?>(null)
    private val loggedOverrides = ConcurrentHashMap.newKeySet<String>()

    fun isValidTarget(classLoader: ClassLoader, dexMethod: DexMethod): Boolean {
        return try {
            val clazz = XposedHelpers.findClass(dexMethod.className, classLoader)
            clazz.declaredMethods.any {
                it.name == dexMethod.name &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType != Void.TYPE
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun hook(plugin: PluginEntry, classLoader: ClassLoader, dexMethod: DexMethod): Boolean {
        val methodName = dexMethod.name
        val className = dexMethod.className
        val tag = "$className#$methodName"
        if (!isValidTarget(classLoader, dexMethod)) {
            plugin.logAlways("Rejected invalid cached flag reader: $tag")
            return false
        }
        if (!hookedTarget.compareAndSet(null, tag)) {
            return hookedTarget.get() == tag
        }

        return try {
            plugin.log("Hooking flag reader: $tag")
            XposedHelpers.findAndHookMethod(
                className, classLoader, methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!plugin.canApplyFlagOverrides) return
                        try {
                            val name = plugin.flagsOverride.getFlagName(param.thisObject) ?: return
                            val override = plugin.flagsOverride.evaluateFlagOverride(name) ?: return
                            param.result = override
                            if (plugin.logLevel.includes(LogLevel.DEBUG) && loggedOverrides.add(name)) {
                                plugin.log("Overrode flag $name to $override")
                            }
                        } catch (t: Throwable) {
                            plugin.log("Error evaluating flag override: ${t.message}")
                        }
                    }
                }
            )
            true
        } catch (t: Throwable) {
            hookedTarget.compareAndSet(tag, null)
            plugin.logAlways("Failed to hook flag reader $tag: ${t.message}")
            false
        }
    }
}

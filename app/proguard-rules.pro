# ProGuard rules for the BlueKey Workshop module
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.jvm.internal.SourceDebugExtension
-keep class com.keyflux.PluginEntry
-keepclassmembers class * {
    public static ** Companion;
}

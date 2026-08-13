package com.keyflux

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class SettingsProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.keyflux.provider"
        const val METHOD_SEED_MISSING = "seed_missing"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/settings")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        enforceSettingsUri(uri)
        enforceAllowedCaller()
        val context = context ?: return null
        val sp = context.getSharedPreferences(ModulePreferences.FILE_NAME, Context.MODE_PRIVATE)
        val cursor = MatrixCursor(arrayOf("key", "value", "type"))
        for ((key, value) in ModulePreferences.read(sp)) {
            val type = when (value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                else -> "string"
            }
            cursor.addRow(arrayOf(key, value.toString(), type))
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        enforceAllowedCaller()
        if (method != METHOD_SEED_MISSING) {
            throw IllegalArgumentException("Unsupported provider method")
        }

        val context = context ?: return Bundle().apply { putBoolean("saved", false) }
        val values = buildMap<String, Any> {
            extras?.keySet()?.forEach { key ->
                val value = extras.get(key)
                if (ProviderAccessPolicy.isExposedPreference(key) &&
                    ProviderAccessPolicy.isSupportedBundleValue(value)
                ) {
                    put(key, value!!)
                }
            }
        }
        val preferences = context.getSharedPreferences(ModulePreferences.FILE_NAME, Context.MODE_PRIVATE)
        val saved = ModulePreferences.write(preferences, values, onlyIfMissing = true)
        if (saved && values.isNotEmpty()) {
            context.contentResolver.notifyChange(CONTENT_URI, null)
        }
        return Bundle().apply { putBoolean("saved", saved) }
    }

    override fun getType(uri: Uri): String {
        enforceSettingsUri(uri)
        return "vnd.android.cursor.dir/vnd.com.keyflux.settings"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        rejectMutation(uri)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        rejectMutation(uri)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        rejectMutation(uri)
    }

    private fun enforceSettingsUri(uri: Uri) {
        if (uri != CONTENT_URI) {
            throw IllegalArgumentException("Unsupported settings URI")
        }
    }

    private fun enforceAllowedCaller() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return

        val packages = context?.packageManager?.getPackagesForUid(callingUid)
        if (!ProviderAccessPolicy.isAllowedCaller(packages)) {
            throw SecurityException("Caller UID $callingUid is not allowed to read BlueKey Workshop settings")
        }
    }

    private fun rejectMutation(uri: Uri): Nothing {
        enforceSettingsUri(uri)
        enforceAllowedCaller()
        throw UnsupportedOperationException("BlueKey Workshop settings provider is read-only")
    }
}

package com.keyflux

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var preferences: ModulePreferences
    private lateinit var settingsContainer: LinearLayout
    private lateinit var changeStatus: TextView
    private lateinit var moduleStatusIndicator: View
    private lateinit var moduleStatusTitle: TextView
    private lateinit var moduleStatusSummary: TextView
    private lateinit var lsposedVersion: TextView
    private lateinit var xposedApiVersion: TextView
    private lateinit var gboardVersion: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val toggleRows = HashMap<String, ToggleRow>()
    private var statusNonce: String? = null
    private var statusReceiverRegistered = false
    private var lastGboardProcessToken: String? = null
    private var pendingRestartProcessToken: String? = null
    private var restartPending = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ModuleStatusProtocol.RESPONSE_ACTION) return
            val nonce = intent.getStringExtra(ModuleStatusProtocol.EXTRA_NONCE)
            if (nonce == null || nonce != statusNonce) return
            statusNonce = null
            mainHandler.removeCallbacksAndMessages(STATUS_TIMEOUT_TOKEN)

            val loadedCode = intent.getIntExtra(
                ModuleStatusProtocol.EXTRA_MODULE_VERSION_CODE,
                -1
            )
            val loadedName = intent.getStringExtra(
                ModuleStatusProtocol.EXTRA_MODULE_VERSION_NAME
            ).orEmpty()
            val xposedApi = intent.getIntExtra(ModuleStatusProtocol.EXTRA_XPOSED_API, -1)
            val failedHookCount = intent.getIntExtra(
                ModuleStatusProtocol.EXTRA_FAILED_HOOK_COUNT,
                0
            )
            reconcileRestartReminder(
                intent.getStringExtra(ModuleStatusProtocol.EXTRA_PROCESS_TOKEN)
            )
            xposedApiVersion.text = if (xposedApi > 0) {
                getString(R.string.xposed_api_format, xposedApi)
            } else {
                getString(R.string.status_unknown)
            }

            if (loadedCode != BuildConfig.VERSION_CODE) {
                showModuleStatus(
                    R.string.module_status_restart,
                    getString(R.string.module_status_version_mismatch, loadedName),
                    com.google.android.material.R.attr.colorTertiary
                )
            } else if (failedHookCount > 0) {
                showModuleStatus(
                    R.string.module_status_active,
                    getString(R.string.module_status_hook_warnings, failedHookCount),
                    com.google.android.material.R.attr.colorTertiary
                )
            } else {
                showModuleStatus(
                    R.string.module_status_active,
                    getString(R.string.module_status_active_summary),
                    com.google.android.material.R.attr.colorPrimary
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        setContentView(R.layout.activity_main)

        preferences = ModulePreferences(this)
        settingsContainer = findViewById(R.id.settings_container)
        changeStatus = findViewById(R.id.change_status)
        moduleStatusIndicator = findViewById(R.id.module_status_indicator)
        moduleStatusTitle = findViewById(R.id.module_status_title)
        moduleStatusSummary = findViewById(R.id.module_status_summary)
        lsposedVersion = findViewById(R.id.lsposed_version)
        xposedApiVersion = findViewById(R.id.xposed_api_version)
        gboardVersion = findViewById(R.id.gboard_version)

        applySystemBarInsets()
        updateInstalledVersions()
        buildSettings()
        findViewById<MaterialButton>(R.id.open_gboard_button).setOnClickListener {
            openGboardAppInfo()
        }
    }

    override fun onStart() {
        super.onStart()
        registerStatusReceiver()
        requestModuleStatus()
    }

    override fun onStop() {
        statusNonce = null
        mainHandler.removeCallbacksAndMessages(STATUS_TIMEOUT_TOKEN)
        if (statusReceiverRegistered) {
            runCatching { unregisterReceiver(statusReceiver) }
            statusReceiverRegistered = false
        }
        super.onStop()
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val isLight = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.root)
        val appBar = findViewById<AppBarLayout>(R.id.app_bar)
        val actionBar = findViewById<View>(R.id.action_bar)
        val initialTop = appBar.paddingTop
        val initialAppBarLeft = appBar.paddingLeft
        val initialAppBarRight = appBar.paddingRight
        val initialActionLeft = actionBar.paddingLeft
        val initialActionRight = actionBar.paddingRight
        val initialBottom = actionBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = initialAppBarLeft + safeArea.left,
                top = initialTop + safeArea.top,
                right = initialAppBarRight + safeArea.right
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(actionBar) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = initialActionLeft + safeArea.left,
                right = initialActionRight + safeArea.right,
                bottom = initialBottom + safeArea.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatusReceiver() {
        if (statusReceiverRegistered) return
        val filter = IntentFilter(ModuleStatusProtocol.RESPONSE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        statusReceiverRegistered = true
    }

    private fun requestModuleStatus() {
        updateInstalledVersions()
        val nonce = UUID.randomUUID().toString()
        statusNonce = nonce
        xposedApiVersion.text = getString(R.string.status_checking)
        showModuleStatus(
            R.string.module_status_checking,
            getString(R.string.module_status_checking_summary),
            com.google.android.material.R.attr.colorOutline
        )
        sendBroadcast(
            Intent(ModuleStatusProtocol.REQUEST_ACTION)
                .setPackage(PluginEntry.PACKAGE_NAME)
                .putExtra(ModuleStatusProtocol.EXTRA_NONCE, nonce)
        )

        val timeout = Runnable {
            if (statusNonce != nonce) return@Runnable
            statusNonce = null
            if (restartPending && lastGboardProcessToken != null) {
                clearRestartReminder()
            }
            xposedApiVersion.text = getString(R.string.status_no_response)
            val summary = if (packageVersion(PluginEntry.PACKAGE_NAME) == null) {
                getString(R.string.module_status_gboard_missing)
            } else {
                getString(R.string.module_status_inactive_summary)
            }
            showModuleStatus(
                R.string.module_status_inactive,
                summary,
                com.google.android.material.R.attr.colorError
            )
        }
        mainHandler.postAtTime(timeout, STATUS_TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + 1800L)
    }

    private fun showModuleStatus(titleRes: Int, summary: String, colorAttr: Int) {
        moduleStatusTitle.setText(titleRes)
        moduleStatusSummary.text = summary
        val color = MaterialColors.getColor(moduleStatusIndicator, colorAttr)
        moduleStatusIndicator.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun updateInstalledVersions() {
        lsposedVersion.text = packageVersion(LSPOSED_MANAGER_PACKAGE)
            ?: getString(R.string.status_not_detected)
        gboardVersion.text = packageVersion(PluginEntry.PACKAGE_NAME)
            ?: getString(R.string.status_not_installed)
        findViewById<TextView>(R.id.module_version).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        findViewById<TextView>(R.id.android_version).text = getString(
            R.string.android_version_format,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            Build.MODEL
        )
    }

    @Suppress("DEPRECATION")
    private fun packageVersion(packageName: String): String? = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        getString(R.string.package_version_format, info.versionName ?: "?", code)
    } catch (_: Exception) {
        null
    }

    private fun buildSettings() {
        settingsContainer.removeAllViews()
        toggleRows.clear()
        val stored = preferences.readAll()

        addSection(
            getString(R.string.section_input),
            listOf(
                ToggleSetting("keyflux_enable_multilingual", false),
                ToggleSetting("keyflux_enable_grammar", false),
                ToggleSetting("keyflux_enable_ai", false),
                ToggleSetting("keyflux_enable_floating", false),
                ToggleSetting("keyflux_enable_emoji_kitchen", false),
                ToggleSetting("keyflux_metered_downloads", false)
            ),
            stored
        )
        addSection(
            getString(R.string.section_learning),
            listOf(
                ToggleSetting("keyflux_enable_chinese_learning", false),
                ToggleSetting("keyflux_enable_adaptive_chinese_learning", true, dependent = true),
                ToggleSetting("keyflux_enable_chinese_suggestions", true, dependent = true),
                ToggleSetting("keyflux_enable_emoji_suggestions", true, dependent = true)
            ),
            stored
        )
        addSection(
            getString(R.string.section_privacy),
            listOf(
                ToggleSetting("keyflux_force_incognito", false),
                ToggleSetting("keyflux_enable_privacy", false),
                ToggleSetting("keyflux_secure_clipboard", false)
            ),
            stored
        )
        addSection(
            getString(R.string.section_appearance),
            listOf(
                ToggleSetting("keyflux_enable_access_point", false),
                ToggleSetting("keyflux_enable_amoled", false),
                ToggleSetting(ThemePalette.ENABLED_KEY, false)
            ),
            stored
        )
        addActionRow(
            Localization.getString("keyflux_theme_editor_title"),
            themeSummary(stored)
        ) { summary ->
            ThemeEditorDialog.show(this, preferences.readAll()) { values ->
                if (preferences.putAll(values)) {
                    summary.text = themeSummary(preferences.readAll())
                    markChanged()
                } else {
                    showSaveError()
                }
            }
        }

        addSection(
            getString(R.string.section_experimental),
            listOf(
                ToggleSetting("keyflux_enable_inline_suggestions", false),
                ToggleSetting("keyflux_enable_proactive_emoji", false),
                ToggleSetting("keyflux_enable_clipboard_chips", false),
                ToggleSetting("keyflux_enable_tflite_engine", false),
                ToggleSetting("keyflux_enable_fast_access", false)
            ),
            stored
        )

        addSectionTitle(getString(R.string.section_diagnostics))
        addLogLevelRow(stored)
        updateLearningDependencies()
    }

    private fun addSection(
        title: String,
        settings: List<ToggleSetting>,
        stored: Map<String, Any>
    ) {
        addSectionTitle(title)
        settings.forEach { setting -> addToggleRow(setting, stored) }
    }

    private fun addSectionTitle(title: String) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_section_title, settingsContainer, false) as TextView
        view.text = title
        settingsContainer.addView(view)
    }

    private fun addToggleRow(setting: ToggleSetting, stored: Map<String, Any>) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_switch_setting, settingsContainer, false)
        val title = row.findViewById<TextView>(R.id.setting_title)
        val summary = row.findViewById<TextView>(R.id.setting_summary)
        val toggle = row.findViewById<MaterialSwitch>(R.id.setting_switch)
        title.text = Localization.getString(setting.key + "_title")
        summary.text = Localization.getString(setting.key + "_summary")
        toggle.contentDescription = title.text
        toggle.isChecked = stored[setting.key] as? Boolean ?: setting.defaultValue

        var restoring = false
        toggle.setOnCheckedChangeListener { _, checked ->
            if (restoring) return@setOnCheckedChangeListener
            if (!preferences.put(setting.key, checked)) {
                restoring = true
                toggle.isChecked = !checked
                restoring = false
                showSaveError()
                return@setOnCheckedChangeListener
            }
            markChanged()
            if (setting.key == "keyflux_enable_chinese_learning") {
                updateLearningDependencies()
            }
        }
        row.setOnClickListener {
            if (toggle.isEnabled) toggle.toggle()
        }
        settingsContainer.addView(row)
        toggleRows[setting.key] = ToggleRow(row, toggle, setting.dependent)
    }

    private fun addActionRow(
        titleText: String,
        summaryText: String,
        onClick: (TextView) -> Unit
    ) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_action_setting, settingsContainer, false)
        row.findViewById<TextView>(R.id.setting_title).text = titleText
        val summary = row.findViewById<TextView>(R.id.setting_summary)
        summary.text = summaryText
        row.setOnClickListener { onClick(summary) }
        settingsContainer.addView(row)
    }

    private fun addLogLevelRow(stored: Map<String, Any>) {
        val level = LogLevel.fromStored(
            stored["keyflux_log_level"],
            stored["keyflux_log_switch"] as? Boolean ?: false
        )
        addActionRow(
            Localization.getString("keyflux_log_level_title"),
            logLevelLabel(level)
        ) { summary -> showLogLevelDialog(summary) }
    }

    private fun showLogLevelDialog(summary: TextView) {
        val levels = LogLevel.values()
        val current = LogLevel.fromStored(
            preferences.readAll()["keyflux_log_level"],
            false
        )
        val labels = levels.map(::logLevelLabel).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(Localization.getString("keyflux_log_level_title"))
            .setSingleChoiceItems(labels, levels.indexOf(current)) { dialog, index ->
                if (preferences.put("keyflux_log_level", levels[index].storedValue)) {
                    summary.text = labels[index]
                    markChanged()
                    dialog.dismiss()
                } else {
                    showSaveError()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateLearningDependencies() {
        val enabled = toggleRows["keyflux_enable_chinese_learning"]?.toggle?.isChecked == true
        toggleRows.values.filter { it.dependent }.forEach { item ->
            item.toggle.isEnabled = enabled
            item.container.isEnabled = enabled
            item.container.alpha = if (enabled) 1f else 0.46f
        }
    }

    private fun markChanged() {
        if (!restartPending) {
            pendingRestartProcessToken = lastGboardProcessToken
        }
        restartPending = true
        changeStatus.text = getString(R.string.status_restart_required)
    }

    private fun reconcileRestartReminder(processToken: String?) {
        if (processToken.isNullOrEmpty()) return
        if (restartPending) {
            val pendingToken = pendingRestartProcessToken
            if (pendingToken == null) {
                pendingRestartProcessToken = processToken
            } else if (pendingToken != processToken) {
                clearRestartReminder()
            }
        }
        lastGboardProcessToken = processToken
    }

    private fun clearRestartReminder() {
        restartPending = false
        pendingRestartProcessToken = null
        changeStatus.text = getString(R.string.status_saved)
    }

    private fun themeSummary(stored: Map<String, Any>): String = String.format(
        Localization.getString("keyflux_theme_editor_summary"),
        ThemePalette.formatColor(ThemePalette.fromPreferences(stored, ThemeMode.LIGHT).background),
        ThemePalette.formatColor(ThemePalette.fromPreferences(stored, ThemeMode.DARK).background)
    )

    private fun logLevelLabel(level: LogLevel): String =
        Localization.getString("keyflux_log_level_${level.storedValue}")

    private fun showSaveError() {
        Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show()
    }

    private fun openGboardAppInfo() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${PluginEntry.PACKAGE_NAME}")
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.gboard_not_found, Toast.LENGTH_LONG).show()
        }
    }

    private data class ToggleSetting(
        val key: String,
        val defaultValue: Boolean,
        val dependent: Boolean = false
    )

    private data class ToggleRow(
        val container: View,
        val toggle: MaterialSwitch,
        val dependent: Boolean
    )

    companion object {
        private const val LSPOSED_MANAGER_PACKAGE = "org.lsposed.manager"
        private val STATUS_TIMEOUT_TOKEN = Any()
    }
}

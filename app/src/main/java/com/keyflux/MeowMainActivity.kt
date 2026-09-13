package com.keyflux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.lingqiqi5211.meowui.component.MeowActionPreference
import io.github.lingqiqi5211.meowui.component.MeowAppearanceContent
import io.github.lingqiqi5211.meowui.component.MeowAppearanceLabels
import io.github.lingqiqi5211.meowui.component.MeowNavigationBar
import io.github.lingqiqi5211.meowui.component.MeowNavigationBarStyle
import io.github.lingqiqi5211.meowui.component.MeowNavigationItem
import io.github.lingqiqi5211.meowui.component.MeowPreferencePage
import io.github.lingqiqi5211.meowui.component.MeowPreferenceSection
import io.github.lingqiqi5211.meowui.component.MeowSwitchPreference
import io.github.lingqiqi5211.meowui.theme.MeowAppearance
import io.github.lingqiqi5211.meowui.theme.MeowColorSpec
import io.github.lingqiqi5211.meowui.theme.MeowPaletteStyle
import io.github.lingqiqi5211.meowui.theme.MeowThemeMode
import io.github.lingqiqi5211.meowui.core.MeowUiStyle
import io.github.lingqiqi5211.meowui.theme.MeowTheme
import java.util.UUID

/** Compose/MeowUI settings host. Preference keys remain compatible with the legacy provider. */
class MeowMainActivity : ComponentActivity() {
    private lateinit var preferences: ModulePreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statusReceiverRegistered = false
    private var statusNonce: String? = null
    private var statusCallback: ((StatusSnapshot) -> Unit)? = null
    private var lastGboardProcessToken: String? = null
    private var pendingRestartProcessToken: String? = null
    private var restartPending = false
    private val restartProbe = object : Runnable {
        override fun run() {
            if (!restartPending || !statusReceiverRegistered) return
            requestModuleStatus(showChecking = false)
            if (restartPending) mainHandler.postDelayed(this, RESTART_PROBE_INTERVAL_MS)
        }
    }
    @Volatile private var pendingStatus: StatusSnapshot? = null

    private fun publishStatus(status: StatusSnapshot) {
        pendingStatus = status
        statusCallback?.invoke(status)
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ModuleStatusProtocol.RESPONSE_ACTION) return
            val nonce = intent.getStringExtra(ModuleStatusProtocol.EXTRA_NONCE)
            Log.i(TAG, "status response nonce=$nonce expected=$statusNonce package=${intent.`package`}")
            if (nonce == null || nonce != statusNonce) return
            statusNonce = null
            mainHandler.removeCallbacksAndMessages(STATUS_TIMEOUT)
            val processToken = intent.getStringExtra(ModuleStatusProtocol.EXTRA_PROCESS_TOKEN)
            reconcileRestartReminder(processToken)
            val loadedCode = intent.getIntExtra(ModuleStatusProtocol.EXTRA_MODULE_VERSION_CODE, -1)
            publishStatus(
                StatusSnapshot(
                    active = loadedCode == BuildConfig.VERSION_CODE,
                    versionName = intent.getStringExtra(ModuleStatusProtocol.EXTRA_MODULE_VERSION_NAME),
                    versionMismatch = loadedCode >= 0 && loadedCode != BuildConfig.VERSION_CODE,
                    failedHooks = intent.getIntExtra(ModuleStatusProtocol.EXTRA_FAILED_HOOK_COUNT, 0),
                    processToken = processToken,
                )
            )
            Log.i(TAG, "status accepted versionCode=$loadedCode failedHooks=${intent.getIntExtra(ModuleStatusProtocol.EXTRA_FAILED_HOOK_COUNT, 0)}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ModulePreferences(this)
        setContent {
            SettingsPage()
        }
    }

    override fun onStart() {
        super.onStart()
        registerStatusReceiver()
        requestModuleStatus()
    }

    override fun onStop() {
        statusNonce = null
        mainHandler.removeCallbacksAndMessages(STATUS_TIMEOUT)
        mainHandler.removeCallbacks(restartProbe)
        if (statusReceiverRegistered) {
            runCatching { unregisterReceiver(statusReceiver) }
            statusReceiverRegistered = false
        }
        statusCallback = null
        super.onStop()
    }

    private fun registerStatusReceiver() {
        if (statusReceiverRegistered) return
        val filter = IntentFilter(ModuleStatusProtocol.RESPONSE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Gboard sends the response from its own UID and cannot hold our signature permission.
            registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        statusReceiverRegistered = true
    }

    private fun requestModuleStatus(showChecking: Boolean = true) {
        val nonce = UUID.randomUUID().toString()
        statusNonce = nonce
        Log.i(TAG, "status request nonce=$nonce")
        if (showChecking) publishStatus(StatusSnapshot(checking = true))
        sendBroadcast(
            Intent(ModuleStatusProtocol.REQUEST_ACTION)
                .setPackage(PluginEntry.PACKAGE_NAME)
                .putExtra(ModuleStatusProtocol.EXTRA_NONCE, nonce),
        )
        mainHandler.postAtTime({
            if (statusNonce != nonce) return@postAtTime
            statusNonce = null
            if (showChecking) publishStatus(StatusSnapshot(active = false))
        }, STATUS_TIMEOUT, android.os.SystemClock.uptimeMillis() + 5000L)
    }

    private fun markChanged() {
        if (!restartPending) {
            pendingRestartProcessToken = lastGboardProcessToken
        }
        restartPending = true
        mainHandler.removeCallbacks(restartProbe)
        mainHandler.postDelayed(restartProbe, RESTART_PROBE_INTERVAL_MS)
    }

    private fun reconcileRestartReminder(processToken: String?) {
        if (processToken.isNullOrEmpty()) return
        if (restartPending) {
            val pendingToken = pendingRestartProcessToken
            // If the first status request raced with a setting change, establish
            // the baseline token and keep the reminder until Gboard restarts.
            if (pendingToken == null) {
                pendingRestartProcessToken = processToken
            } else if (pendingToken != processToken) {
                restartPending = false
                pendingRestartProcessToken = null
                mainHandler.removeCallbacks(restartProbe)
            }
        }
        lastGboardProcessToken = processToken
    }

    @Composable
    private fun SettingsPage() {
        // Decryption is moved off the first frame so the launch window can be replaced immediately.
        val storedValues = remember { mutableStateOf<Map<String, Any>?>(null) }
        LaunchedEffect(Unit) {
            val loaded = withContext(Dispatchers.Default) { preferences.readAll().toMap() }
            storedValues.value = loaded
        }
        val values = storedValues.value
        val appearance = appearanceFrom(values ?: emptyMap())
        if (values == null) {
            MeowTheme(appearance = appearance) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return
        }
        val updateAppearance: (MeowAppearance) -> Unit = { updated ->
            val before = appearanceFrom(values)
            if (updated != before) {
                persistAppearance(updated)
                storedValues.value = preferences.readAll().toMap()
                markChanged()
            }
        }
        var selectedPage by rememberSaveable { mutableIntStateOf(0) }
        val navigationItems = remember {
            listOf(
                MeowNavigationItem("设置", Icons.Rounded.Settings),
                MeowNavigationItem("外观", Icons.Rounded.Palette),
                MeowNavigationItem("关于", Icons.Rounded.Info),
            )
        }
        val bottomBar: @Composable () -> Unit = {
            MeowNavigationBar(
                items = navigationItems,
                selectedIndex = selectedPage,
                onItemSelected = { selectedPage = it },
                style = if (appearance.floatingNavigationBarEnabled) {
                    MeowNavigationBarStyle.Floating
                } else {
                    MeowNavigationBarStyle.Standard
                },
            )
        }

        MeowTheme(appearance = appearance) {
            // Keep navigation outside AnimatedContent so it remains stationary while pages slide.
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    modifier = Modifier.fillMaxSize().padding(bottom = 88.dp),
                    targetState = selectedPage,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(
                                animationSpec = tween(260),
                                initialOffsetX = { width -> width / 8 * direction },
                            )) togetherWith
                            (fadeOut(animationSpec = tween(160)) +
                                slideOutHorizontally(
                                    animationSpec = tween(220),
                                    targetOffsetX = { width -> -width / 8 * direction },
                                ))
                    },
                    label = "settings_page_transition",
                ) { page ->
                    when (page) {
                        1 -> AppearanceSettingsPage(appearance, updateAppearance, {})
                        2 -> AboutPage({})
                        else -> MainSettingsPage(
                            values = values,
                            onValuesChange = { storedValues.value = it },
                            bottomBar = {},
                        )
                    }
                }
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { bottomBar() }
            }
        }
    }

    @Composable
    private fun MainSettingsPage(
        values: Map<String, Any>,
        onValuesChange: (Map<String, Any>) -> Unit,
        bottomBar: @Composable () -> Unit,
    ) {
        var status by remember { mutableStateOf(pendingStatus ?: StatusSnapshot(checking = true)) }
        var restartRequired by remember { mutableStateOf(restartPending) }
        statusCallback = {
            status = it
            restartRequired = restartPending
        }

        fun setValue(key: String, value: Any) {
            if (preferences.put(key, value)) {
                onValuesChange(values.toMutableMap().also { it[key] = value })
                markChanged()
                restartRequired = restartPending
            }
        }

        MeowPreferencePage(
            title = Localization.getString("keyflux_category_title"),
            subtitle = if (restartRequired) getString(R.string.status_restart_required)
            else getString(R.string.status_saved),
            bottomBar = bottomBar,
        ) {
            MeowPreferenceSection {
                MeowActionPreference(
                    title = if (status.checking) getString(R.string.module_status_checking)
                    else if (status.versionMismatch) getString(R.string.module_status_restart)
                    else if (status.active) getString(R.string.module_status_active)
                    else getString(R.string.module_status_inactive),
                    summary = status.summary(this@MeowMainActivity),
                    enabled = false,
                    onClick = {},
                )
                MeowActionPreference(
                    title = getString(R.string.open_gboard_info),
                    summary = getString(R.string.app_subtitle),
                    navigation = true,
                    onClick = ::openGboardAppInfo,
                )
            }

            settingsSection(
                title = getString(R.string.section_input),
                keys = listOf(
                    "keyflux_enable_multilingual" to false,
                    "keyflux_enable_grammar" to false,
                    "keyflux_enable_ai" to false,
                    "keyflux_enable_floating" to false,
                    "keyflux_enable_emoji_kitchen" to false,
                    "keyflux_metered_downloads" to false,
                ),
                values = values,
                onChange = ::setValue,
            )
            val learningEnabled = values["keyflux_enable_chinese_learning"] as? Boolean ?: false
            settingsSection(
                title = getString(R.string.section_learning),
                keys = listOf(
                    "keyflux_enable_chinese_learning" to false,
                    "keyflux_enable_adaptive_chinese_learning" to true,
                    "keyflux_enable_chinese_suggestions" to true,
                    "keyflux_enable_emoji_suggestions" to true,
                ),
                values = values,
                onChange = ::setValue,
                enabled = { key -> key == "keyflux_enable_chinese_learning" || learningEnabled },
            )
            settingsSection(
                title = getString(R.string.section_privacy),
                keys = listOf(
                    "keyflux_force_incognito" to false,
                    "keyflux_enable_privacy" to false,
                    "keyflux_secure_clipboard" to false,
                ),
                values = values,
                onChange = ::setValue,
            )
            settingsSection(
                title = getString(R.string.section_appearance),
                keys = listOf(
                    "keyflux_enable_access_point" to false,
                    "keyflux_enable_amoled" to false,
                    ThemePalette.ENABLED_KEY to false,
                ),
                values = values,
                onChange = ::setValue,
            )
            MeowPreferenceSection(title = getString(R.string.section_experimental)) {
                listOf(
                    "keyflux_enable_inline_suggestions" to false,
                    "keyflux_enable_proactive_emoji" to false,
                    "keyflux_enable_clipboard_chips" to false,
                    "keyflux_enable_tflite_engine" to false,
                    "keyflux_enable_fast_access" to false,
                ).forEach { (key, default) ->
                    MeowSwitchPreference(
                        title = Localization.getString(key + "_title"),
                        summary = Localization.getString(key + "_summary"),
                        checked = values[key] as? Boolean ?: default,
                        onCheckedChange = { setValue(key, it) },
                    )
                }
            }
            MeowPreferenceSection(title = getString(R.string.section_diagnostics)) {
                MeowActionPreference(
                    title = Localization.getString("keyflux_theme_editor_title"),
                    summary = getString(R.string.section_appearance),
                    navigation = true,
                    onClick = {
                        ThemeEditorDialog.show(this@MeowMainActivity, values) { updated ->
                            if (preferences.putAll(updated)) {
                                onValuesChange(values.toMutableMap().also { it.putAll(updated) })
                                markChanged()
                                restartRequired = restartPending
                            }
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun AppearanceSettingsPage(
        appearance: MeowAppearance,
        onAppearanceChange: (MeowAppearance) -> Unit,
        bottomBar: @Composable () -> Unit,
    ) {
        MeowPreferencePage(title = "外观", bottomBar = bottomBar) {
            MeowAppearanceContent(
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
                labels = MeowAppearanceLabels(
                    title = "外观",
                    themeColor = "主题颜色",
                    themeMode = "主题模式",
                    systemMode = "跟随系统",
                    lightMode = "浅色",
                    darkMode = "深色",
                    amoledDark = "AMOLED 深色",
                    amoledDarkSummary = "深色模式下使用纯黑背景",
                    colorSettings = "颜色",
                    paletteStyle = "调色板风格",
                    colorSpec = "颜色标准",
                    miuixMonet = "动态取色",
                    miuixMonetSummary = "根据壁纸或自定义种子色生成界面颜色",
                    interfaceSettings = "界面",
                    interfaceStyle = "界面风格",
                    floatingNavigationBar = "悬浮底栏",
                    floatingNavigationBarSummary = "使用 MeowUI 的悬浮胶囊底栏",
                    blur = "背景模糊",
                    blurSummary = "为顶栏和悬浮底栏启用磨砂效果",
                    predictiveBack = "预测式返回",
                    predictiveBackSummary = "滑动返回时预览目标页面",
                    interfaceScale = "界面缩放",
                    interfaceScaleSummary = "调整整个设置界面的显示大小",
                    defaultValue = "默认",
                    customColor = "自定义颜色",
                    dialogConfirm = "完成",
                    dialogCancel = "取消",
                ),
            )
        }
    }

    @Composable
    private fun AboutPage(bottomBar: @Composable () -> Unit) {
        MeowPreferencePage(title = "关于", subtitle = "蓝键工坊", bottomBar = bottomBar) {
            MeowPreferenceSection(title = "蓝键工坊") {
                MeowActionPreference(title = "项目地址", summary = PROJECT_URL, navigation = true) { openUrl(PROJECT_URL) }
                MeowActionPreference(title = "当前版本", summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", enabled = false) { }
                MeowActionPreference(title = "开源许可", summary = "BlueKey Workshop MIT · MeowUI Apache 2.0", navigation = true) { openUrl(MEOWUI_URL) }
            }
            MeowPreferenceSection(title = "开源引用") {
                MeowActionPreference(title = "MeowUI", summary = "双风格 Compose UI 库（Material 3 Expressive / Miuix）", navigation = true) { openUrl(MEOWUI_URL) }
                MeowActionPreference(title = "LSPosed API", summary = "Xposed 模块运行框架", navigation = true) { openUrl(LSPOSED_URL) }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show() }
    }

    private fun appearanceFrom(values: Map<String, Any>): MeowAppearance {
        val style = if (values[KEY_UI_STYLE] == "miuix") MeowUiStyle.Miuix
        else MeowUiStyle.MaterialExpressive
        val themeMode = MeowThemeMode.entries.firstOrNull {
            it.name == values[KEY_UI_THEME_MODE]
        } ?: MeowThemeMode.System
        val paletteStyle = MeowPaletteStyle.entries.firstOrNull {
            it.name == values[KEY_UI_PALETTE]
        } ?: MeowPaletteStyle.TonalSpot
        val colorSpec = MeowColorSpec.entries.firstOrNull {
            it.name == values[KEY_UI_COLOR_SPEC]
        } ?: MeowColorSpec.Spec2025
        val seed = (values[KEY_UI_SEED] as? Int)?.let(::Color) ?: Color(0xFF6750A4)
        return MeowAppearance(
            style = style,
            themeMode = themeMode,
            dynamicColor = values[KEY_UI_DYNAMIC] as? Boolean ?: true,
            seedColor = seed,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
            miuixMonetEnabled = values[KEY_UI_MONET] as? Boolean ?: true,
            amoledDarkEnabled = values[KEY_UI_AMOLED] as? Boolean ?: false,
            blurEnabled = values[KEY_UI_BLUR] as? Boolean ?: true,
            floatingNavigationBarEnabled = values[KEY_UI_FLOATING] as? Boolean ?: true,
            predictiveBackEnabled = values[KEY_UI_PREDICTIVE] as? Boolean ?: true,
            interfaceScale = (values[KEY_UI_SCALE] as? Float ?: 1f).coerceIn(0.8f, 1.1f),
        )
    }

    private fun persistAppearance(appearance: MeowAppearance) {
        preferences.putAll(
            mapOf(
                KEY_UI_STYLE to if (appearance.style == MeowUiStyle.Miuix) "miuix" else "material",
                KEY_UI_THEME_MODE to appearance.themeMode.name,
                KEY_UI_DYNAMIC to appearance.dynamicColor,
                KEY_UI_SEED to appearance.seedColor.toArgb(),
                KEY_UI_PALETTE to appearance.paletteStyle.name,
                KEY_UI_COLOR_SPEC to appearance.colorSpec.name,
                KEY_UI_MONET to appearance.miuixMonetEnabled,
                KEY_UI_AMOLED to appearance.amoledDarkEnabled,
                KEY_UI_BLUR to appearance.blurEnabled,
                KEY_UI_FLOATING to appearance.floatingNavigationBarEnabled,
                KEY_UI_PREDICTIVE to appearance.predictiveBackEnabled,
                KEY_UI_SCALE to appearance.interfaceScale,
            ),
        )
    }

    @Composable
    private fun settingsSection(
        title: String,
        keys: List<Pair<String, Boolean>>,
        values: Map<String, Any>,
        onChange: (String, Any) -> Unit,
        enabled: (String) -> Boolean = { true },
    ) {
        MeowPreferenceSection(title = title) {
            keys.forEach { (key, default) ->
                MeowSwitchPreference(
                    title = Localization.getString(key + "_title"),
                    summary = Localization.getString(key + "_summary"),
                    checked = values[key] as? Boolean ?: default,
                    enabled = enabled(key),
                    onCheckedChange = { onChange(key, it) },
                )
            }
        }
    }

    private fun openGboardAppInfo() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${PluginEntry.PACKAGE_NAME}")
            })
        }.onFailure {
            Toast.makeText(this, R.string.gboard_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private data class StatusSnapshot(
        val active: Boolean = false,
        val checking: Boolean = false,
        val versionName: String? = null,
        val versionMismatch: Boolean = false,
        val failedHooks: Int = 0,
        val processToken: String? = null,
    ) {
        fun summary(context: Context): String = when {
            checking -> context.getString(R.string.module_status_checking_summary)
            versionMismatch -> context.getString(
                R.string.module_status_version_mismatch,
                versionName.orEmpty(),
            )
            !active -> context.getString(R.string.module_status_inactive_summary)
            failedHooks > 0 -> context.getString(R.string.module_status_hook_warnings, failedHooks)
            versionName.isNullOrBlank() -> context.getString(R.string.module_status_active_summary)
            else -> context.getString(R.string.module_status_active_summary) + " (" + versionName + ")"
        }
    }

    private companion object {
        const val TAG = "KeyFluxUI"
        const val RESTART_PROBE_INTERVAL_MS = 2000L
        const val PROJECT_URL = "https://github.com/boris446589659/BlueKeyWorkshop"
        const val MEOWUI_URL = "https://github.com/lingqiqi5211/MeowUI"
        const val LSPOSED_URL = "https://github.com/LSPosed/LSPosed"
        const val KEY_UI_STYLE = "keyflux_ui_style"
        const val KEY_UI_THEME_MODE = "keyflux_ui_theme_mode"
        const val KEY_UI_DYNAMIC = "keyflux_ui_dynamic_color"
        const val KEY_UI_SEED = "keyflux_ui_seed_color"
        const val KEY_UI_PALETTE = "keyflux_ui_palette_style"
        const val KEY_UI_COLOR_SPEC = "keyflux_ui_color_spec"
        const val KEY_UI_MONET = "keyflux_ui_miuix_monet"
        const val KEY_UI_AMOLED = "keyflux_ui_amoled_dark"
        const val KEY_UI_BLUR = "keyflux_ui_blur"
        const val KEY_UI_FLOATING = "keyflux_ui_floating_bar"
        const val KEY_UI_PREDICTIVE = "keyflux_ui_predictive_back"
        const val KEY_UI_SCALE = "keyflux_ui_scale"
        val STATUS_TIMEOUT = Any()
    }
}

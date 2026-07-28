package com.keyflux

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.math.roundToInt

internal object ThemeEditorDialog {
    fun show(
        activity: Activity,
        preferences: Map<String, Any>,
        onSave: (Map<String, Any>) -> Unit
    ) {
        var lightPalette = ThemePalette.fromPreferences(preferences, ThemeMode.LIGHT)
        var darkPalette = ThemePalette.fromPreferences(preferences, ThemeMode.DARK)
        var mode = if (isNightMode(activity)) ThemeMode.DARK else ThemeMode.LIGHT
        val panel = SettingsPanel.create(
            activity,
            Localization.getString("keyflux_theme_editor_dialog_title")
        )
        val content = panel.content
        val colors = panel.colors

        fun palette(): ThemePalette = if (mode == ThemeMode.DARK) darkPalette else lightPalette
        fun setPalette(value: ThemePalette) {
            if (mode == ThemeMode.DARK) darkPalette = value else lightPalette = value
        }
        lateinit var refresh: () -> Unit

        val modeRow = SettingsPanel.row(activity, colors).apply {
            setPadding(SettingsPanel.dp(activity, 6), SettingsPanel.dp(activity, 6),
                SettingsPanel.dp(activity, 6), SettingsPanel.dp(activity, 6))
        }
        val lightMode = modeButton(activity, colors, Localization.getString("keyflux_theme_mode_light"))
        val darkMode = modeButton(activity, colors, Localization.getString("keyflux_theme_mode_dark"))
        modeRow.addView(lightMode, weightedButtonParams(activity))
        modeRow.addView(darkMode, weightedButtonParams(activity))
        content.addView(modeRow, fullWidthParams(activity))

        val preview = ThemePreviewView(activity).apply {
            background = SettingsPanel.rounded(colors.elevated, SettingsPanel.dp(activity, 4), colors.divider)
            clipToOutline = true
        }
        content.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            SettingsPanel.dp(activity, 210)
        ).also { it.topMargin = SettingsPanel.dp(activity, 12) })

        SettingsPanel.addSection(
            content,
            Localization.getString("keyflux_theme_preset"),
            colors,
            activity
        )
        val presetRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val presets = listOf(
            1 to Localization.getString("keyflux_theme_preset_amoled"),
            2 to Localization.getString("keyflux_theme_preset_ocean"),
            3 to Localization.getString("keyflux_theme_preset_sakura")
        )
        val presetButtons = presets.map { (index, label) ->
            presetButton(activity, colors, label) {
                setPalette(ThemePalette.preset(mode, index))
                refresh()
            }.also { button ->
                presetRow.addView(button, weightedButtonParams(activity).also {
                    it.rightMargin = if (index == 3) 0 else SettingsPanel.dp(activity, 8)
                })
            }
        }
        content.addView(presetRow, fullWidthParams(activity))

        SettingsPanel.addSection(
            content,
            Localization.getString("keyflux_panel_colors"),
            colors,
            activity
        )
        val rolesContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rolesContainer, fullWidthParams(activity))

        SettingsPanel.addSection(
            content,
            Localization.getString("keyflux_panel_actions"),
            colors,
            activity
        )
        val tools = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val reset = SettingsPanel.actionButton(
            activity,
            Localization.getString("keyflux_theme_reset"),
            colors
        ) {
            setPalette(ThemePalette.default(mode))
            refresh()
        }
        val export = SettingsPanel.actionButton(
            activity,
            Localization.getString("keyflux_theme_export"),
            colors
        ) {
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                ClipData.newPlainText("KeyFlux theme", encode(lightPalette, darkPalette))
            )
            Toast.makeText(
                activity,
                Localization.getString("keyflux_theme_exported"),
                Toast.LENGTH_SHORT
            ).show()
        }
        val import = SettingsPanel.actionButton(
            activity,
            Localization.getString("keyflux_theme_import"),
            colors
        ) {
            showImportPanel(activity) { importedLight, importedDark ->
                lightPalette = importedLight
                darkPalette = importedDark
                refresh()
            }
        }
        listOf(reset, export, import).forEachIndexed { index, button ->
            tools.addView(button, weightedButtonParams(activity).also {
                it.rightMargin = if (index == 2) 0 else SettingsPanel.dp(activity, 8)
            })
        }
        content.addView(tools, fullWidthParams(activity))

        refresh = {
            val current = palette()
            preview.palette = current
            updateModeButton(lightMode, mode == ThemeMode.LIGHT, colors, activity)
            updateModeButton(darkMode, mode == ThemeMode.DARK, colors, activity)
            presets.forEachIndexed { index, (presetIndex, _) ->
                val preset = ThemePalette.preset(mode, presetIndex)
                presetButtons[index].background = SettingsPanel.rounded(
                    preset.accent,
                    SettingsPanel.dp(activity, 6),
                    if (current == preset) colors.text else preset.accent
                )
                presetButtons[index].setTextColor(ThemePalette.contrastColor(preset.accent))
            }
            rolesContainer.removeAllViews()
            ThemeRole.values().forEach { role ->
                rolesContainer.addView(roleRow(activity, role, current.color(role), colors) { selected ->
                    setPalette(palette().withColor(role, selected))
                    refresh()
                }, fullWidthParams(activity).also { it.topMargin = SettingsPanel.dp(activity, 8) })
            }
        }

        lightMode.setOnClickListener {
            mode = ThemeMode.LIGHT
            refresh()
        }
        darkMode.setOnClickListener {
            mode = ThemeMode.DARK
            refresh()
        }
        refresh()

        panel.footer.addView(SettingsPanel.textAction(
            activity,
            activity.getString(android.R.string.cancel),
            colors
        ) { panel.dialog.dismiss() }, footerButtonParams(activity))
        panel.footer.addView(SettingsPanel.textAction(
            activity,
            activity.getString(android.R.string.ok),
            colors
        ) {
            val values = LinkedHashMap<String, Any>()
            values.putAll(lightPalette.toPreferences(ThemeMode.LIGHT))
            values.putAll(darkPalette.toPreferences(ThemeMode.DARK))
            onSave(values)
            panel.dialog.dismiss()
        }, footerButtonParams(activity).also { it.leftMargin = SettingsPanel.dp(activity, 8) })
        panel.show()
    }

    private fun roleRow(
        activity: Activity,
        role: ThemeRole,
        color: Int,
        panelColors: SettingsPanel.Colors,
        onColor: (Int) -> Unit
    ): View {
        val row = SettingsPanel.row(activity, panelColors)
        val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        text.addView(TextView(activity).apply {
            this.text = Localization.getString("keyflux_theme_role_${role.keyPart}")
            textSize = 16f
            setTextColor(panelColors.text)
        })
        text.addView(TextView(activity).apply {
            this.text = ThemePalette.formatColor(color)
            textSize = 13f
            setTextColor(panelColors.muted)
        })
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(activity).apply {
            background = SettingsPanel.rounded(color, SettingsPanel.dp(activity, 18), panelColors.divider).apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
            }
        }, LinearLayout.LayoutParams(SettingsPanel.dp(activity, 36), SettingsPanel.dp(activity, 36)))
        row.addView(TextView(activity).apply {
            this.text = ">"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(panelColors.muted)
        }, LinearLayout.LayoutParams(SettingsPanel.dp(activity, 30), ViewGroup.LayoutParams.MATCH_PARENT))
        row.setOnClickListener {
            ColorPickerPanel.show(activity, Localization.getString("keyflux_theme_role_${role.keyPart}"), color, onColor)
        }
        return row
    }

    private fun modeButton(
        activity: Activity,
        colors: SettingsPanel.Colors,
        label: CharSequence
    ): Button = Button(activity).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        minHeight = 0
        minimumHeight = SettingsPanel.dp(activity, 44)
        setTextColor(colors.text)
        background = SettingsPanel.rounded(colors.elevated, SettingsPanel.dp(activity, 4), colors.divider)
        stateListAnimator = null
        elevation = 0f
        translationZ = 0f
    }

    private fun presetButton(
        activity: Activity,
        colors: SettingsPanel.Colors,
        label: CharSequence,
        onClick: () -> Unit
    ): Button = Button(activity).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        minHeight = 0
        minimumHeight = SettingsPanel.dp(activity, 48)
        background = SettingsPanel.rounded(colors.elevated, SettingsPanel.dp(activity, 4), colors.divider)
        stateListAnimator = null
        elevation = 0f
        translationZ = 0f
        setOnClickListener { onClick() }
    }

    private fun updateModeButton(
        button: Button,
        selected: Boolean,
        colors: SettingsPanel.Colors,
        activity: Activity
    ) {
        button.background = SettingsPanel.rounded(
            if (selected) colors.accent else colors.elevated,
            SettingsPanel.dp(activity, 4),
            if (selected) colors.accent else colors.divider
        )
        button.setTextColor(if (selected) colors.onAccent else colors.text)
    }

    private fun showImportPanel(
        activity: Activity,
        onImport: (ThemePalette, ThemePalette) -> Unit
    ) {
        val panel = SettingsPanel.create(activity, Localization.getString("keyflux_theme_import_title"))
        val input = EditText(activity).apply {
            minLines = 8
            maxLines = 14
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            typeface = Typeface.MONOSPACE
            setTextColor(panel.colors.text)
            setHintTextColor(panel.colors.muted)
            background = SettingsPanel.rounded(panel.colors.elevated, SettingsPanel.dp(activity, 4), panel.colors.divider)
            setPadding(SettingsPanel.dp(activity, 14), SettingsPanel.dp(activity, 14),
                SettingsPanel.dp(activity, 14), SettingsPanel.dp(activity, 14))
        }
        panel.content.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            SettingsPanel.dp(activity, 240)
        ))
        panel.footer.addView(SettingsPanel.textAction(
            activity, activity.getString(android.R.string.cancel), panel.colors
        ) { panel.dialog.dismiss() }, footerButtonParams(activity))
        panel.footer.addView(SettingsPanel.textAction(
            activity, activity.getString(android.R.string.ok), panel.colors
        ) {
            val decoded = runCatching { decode(input.text.toString()) }.getOrNull()
            if (decoded == null) {
                input.error = Localization.getString("keyflux_theme_import_invalid")
            } else {
                onImport(decoded.first, decoded.second)
                panel.dialog.dismiss()
            }
        }, footerButtonParams(activity).also { it.leftMargin = SettingsPanel.dp(activity, 8) })
        panel.show()
    }

    private fun encode(light: ThemePalette, dark: ThemePalette): String {
        fun paletteObject(palette: ThemePalette) = JSONObject().apply {
            ThemeRole.values().forEach { role -> put(role.keyPart, ThemePalette.formatColor(palette.color(role))) }
        }
        return JSONObject()
            .put("format", "KeyFluxTheme")
            .put("version", 1)
            .put("light", paletteObject(light))
            .put("dark", paletteObject(dark))
            .toString()
    }

    private fun decode(raw: String): Pair<ThemePalette, ThemePalette> {
        val root = JSONObject(raw)
        require(root.optString("format") == "KeyFluxTheme")
        fun read(mode: ThemeMode): ThemePalette {
            val source = root.getJSONObject(mode.keyPart)
            var palette = ThemePalette.default(mode)
            ThemeRole.values().forEach { role ->
                val color = ThemePalette.parseColor(source.getString(role.keyPart))
                    ?: throw IllegalArgumentException("Invalid color")
                palette = palette.withColor(role, color)
            }
            return palette
        }
        return read(ThemeMode.LIGHT) to read(ThemeMode.DARK)
    }

    private fun isNightMode(context: Context): Boolean {
        val mask = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun weightedButtonParams(activity: Activity) = LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
    )

    private fun fullWidthParams(activity: Activity) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun footerButtonParams(activity: Activity) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, SettingsPanel.dp(activity, 44)
    )

    private object ColorPickerPanel {
        fun show(
            activity: Activity,
            title: CharSequence,
            initialColor: Int,
            onColor: (Int) -> Unit
        ) {
            var currentColor = initialColor
            var syncing = false
            val hsv = FloatArray(3)
            Color.colorToHSV(currentColor, hsv)
            val panel = SettingsPanel.create(activity, title)
            val preview = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, SettingsPanel.dp(activity, 104)
                )
            }
            panel.content.addView(preview)

            SettingsPanel.addSection(panel.content, "HEX", panel.colors, activity)
            val hexInput = EditText(activity).apply {
                setText(ThemePalette.formatColor(currentColor))
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                typeface = Typeface.MONOSPACE
                setSelectAllOnFocus(true)
                setSingleLine(true)
                textSize = 18f
                setTextColor(panel.colors.text)
                background = SettingsPanel.rounded(panel.colors.elevated, SettingsPanel.dp(activity, 4), panel.colors.divider)
                setPadding(SettingsPanel.dp(activity, 14), 0, SettingsPanel.dp(activity, 14), 0)
            }
            panel.content.addView(hexInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SettingsPanel.dp(activity, 56)
            ))

            SettingsPanel.addSection(panel.content, "HSV", panel.colors, activity)
            fun slider(label: String, maximum: Int): Pair<TextView, SeekBar> {
                val titleView = TextView(activity).apply {
                    textSize = 14f
                    setTextColor(panel.colors.text)
                }
                val bar = SeekBar(activity).apply { max = maximum }
                panel.content.addView(titleView, fullWidthParams(activity).also {
                    it.topMargin = SettingsPanel.dp(activity, 8)
                })
                panel.content.addView(bar, fullWidthParams(activity))
                titleView.tag = label
                return titleView to bar
            }

            val (hueLabel, hue) = slider(Localization.getString("keyflux_theme_hue"), 360)
            val (satLabel, saturation) = slider(Localization.getString("keyflux_theme_saturation"), 100)
            val (valueLabel, value) = slider(Localization.getString("keyflux_theme_value"), 100)
            val (alphaLabel, alpha) = slider(Localization.getString("keyflux_theme_alpha"), 255)

            fun setPreviewColor(color: Int) {
                preview.background = SettingsPanel.rounded(
                    color,
                    SettingsPanel.dp(activity, 4),
                    ThemePalette.contrastColor(color)
                )
            }

            fun updateLabels() {
                hueLabel.text = "${hueLabel.tag}: ${hue.progress}\u00b0"
                satLabel.text = "${satLabel.tag}: ${saturation.progress}%"
                valueLabel.text = "${valueLabel.tag}: ${value.progress}%"
                alphaLabel.text = "${alphaLabel.tag}: ${(alpha.progress * 100f / 255f).roundToInt()}%"
            }

            fun syncControls(updateHex: Boolean) {
                syncing = true
                Color.colorToHSV(currentColor, hsv)
                hue.progress = hsv[0].roundToInt()
                saturation.progress = (hsv[1] * 100f).roundToInt()
                value.progress = (hsv[2] * 100f).roundToInt()
                alpha.progress = currentColor ushr 24
                if (updateHex) {
                    hexInput.setText(ThemePalette.formatColor(currentColor))
                    hexInput.setSelection(hexInput.text.length)
                }
                setPreviewColor(currentColor)
                updateLabels()
                syncing = false
            }

            val seekListener = object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (syncing) return
                    currentColor = Color.HSVToColor(alpha.progress, floatArrayOf(
                        hue.progress.toFloat(),
                        saturation.progress / 100f,
                        value.progress / 100f
                    ))
                    syncing = true
                    hexInput.setText(ThemePalette.formatColor(currentColor))
                    hexInput.setSelection(hexInput.text.length)
                    setPreviewColor(currentColor)
                    updateLabels()
                    syncing = false
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
            hue.setOnSeekBarChangeListener(seekListener)
            saturation.setOnSeekBarChangeListener(seekListener)
            value.setOnSeekBarChangeListener(seekListener)
            alpha.setOnSeekBarChangeListener(seekListener)
            hexInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (syncing) return
                    ThemePalette.parseColor(s?.toString().orEmpty())?.let {
                        currentColor = it
                        syncControls(updateHex = false)
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            syncControls(updateHex = true)

            panel.footer.addView(SettingsPanel.textAction(
                activity, activity.getString(android.R.string.cancel), panel.colors
            ) { panel.dialog.dismiss() }, footerButtonParams(activity))
            panel.footer.addView(SettingsPanel.textAction(
                activity, activity.getString(android.R.string.ok), panel.colors
            ) {
                onColor(currentColor)
                panel.dialog.dismiss()
            }, footerButtonParams(activity).also { it.leftMargin = SettingsPanel.dp(activity, 8) })
            panel.show()
            panel.dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    private class ThemePreviewView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        var palette: ThemePalette = ThemePalette.DARK_DEFAULT
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()
            paint.color = palette.background
            canvas.drawRect(0f, 0f, width, height, paint)

            val density = resources.displayMetrics.density
            val gap = 6f * density
            val candidateHeight = 38f * density
            paint.color = palette.secondary
            paint.textSize = 13f * density
            paint.textAlign = Paint.Align.CENTER
            arrayOf("hello", "world", "KeyFlux").forEachIndexed { index, value ->
                canvas.drawText(value, width * (index * 2 + 1) / 6f, candidateHeight * 0.68f, paint)
            }

            val columns = 9
            val rows = 3
            val keyWidth = (width - gap * (columns + 1)) / columns
            val keyHeight = (height - candidateHeight - gap * (rows + 1)) / rows
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val left = gap + column * (keyWidth + gap)
                    val top = candidateHeight + gap + row * (keyHeight + gap)
                    rect.set(left, top, left + keyWidth, top + keyHeight)
                    paint.color = if (row == 2 && column == columns - 1) palette.accent else palette.keySurface
                    canvas.drawRoundRect(rect, 8f * density, 8f * density, paint)
                    paint.color = if (row == 2 && column == columns - 1) {
                        ThemePalette.contrastColor(palette.accent)
                    } else {
                        palette.primary
                    }
                    paint.textSize = 16f * density
                    canvas.drawText(
                        if (row == 2 && column == columns - 1) ">" else ('A'.code + (row * columns + column) % 26).toChar().toString(),
                        rect.centerX(),
                        rect.centerY() - (paint.ascent() + paint.descent()) / 2f,
                        paint
                    )
                }
            }
        }
    }
}

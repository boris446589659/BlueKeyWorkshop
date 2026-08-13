package com.keyflux

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Full-screen settings surface modeled after Gboard's correction settings page. */
internal object ChineseLearningSettingsDialog {
    fun show(
        activity: Activity,
        preferences: Map<String, Any>,
        onPreferenceChanged: (String, Boolean) -> Boolean
    ) {
        val panel = SettingsPanel.create(
            activity,
            Localization.getString("keyflux_enable_chinese_learning_title"),
            SettingsPanel.NavigationIcon.BACK,
            showFooter = false
        )
        val content = panel.content
        val colors = panel.colors

        val childRows = ArrayList<ToggleRow>()
        lateinit var updateChildState: () -> Unit

        fun addToggle(key: String, defaultValue: Boolean, isMaster: Boolean = false): ToggleRow {
            val row = toggleRow(
                activity,
                colors,
                Localization.getString(key + "_title"),
                Localization.getString(key + "_summary"),
                preferences[key] as? Boolean ?: defaultValue
            )
            var restoring = false
            row.toggle.setOnCheckedChangeListener { _, checked ->
                if (restoring) return@setOnCheckedChangeListener
                if (!onPreferenceChanged(key, checked)) {
                    restoring = true
                    row.toggle.isChecked = !checked
                    restoring = false
                    Toast.makeText(
                        activity,
                        Localization.getString("keyflux_chinese_learning_save_failed"),
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (isMaster) updateChildState()
            }
            content.addView(row.container)
            return row
        }

        addSection(
            content,
            activity,
            colors,
            Localization.getString("keyflux_chinese_learning_section_learning")
        )
        val master = addToggle("keyflux_enable_chinese_learning", false, isMaster = true)
        childRows.add(addToggle("keyflux_enable_adaptive_chinese_learning", true))

        addSection(
            content,
            activity,
            colors,
            Localization.getString("keyflux_chinese_learning_section_suggestions")
        )
        childRows.add(addToggle("keyflux_enable_chinese_suggestions", true))
        childRows.add(addToggle("keyflux_enable_emoji_suggestions", true))

        updateChildState = {
            val enabled = master.toggle.isChecked
            for (row in childRows) {
                row.toggle.isEnabled = enabled
                row.container.alpha = if (enabled) 1f else 0.48f
            }
        }
        updateChildState()
        panel.show()
    }

    private data class ToggleRow(val container: LinearLayout, val toggle: Switch)

    @Suppress("DEPRECATION")
    private fun toggleRow(
        activity: Activity,
        colors: SettingsPanel.Colors,
        title: CharSequence,
        summary: CharSequence,
        checked: Boolean
    ): ToggleRow {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = SettingsPanel.dp(activity, 76)
            setPadding(
                SettingsPanel.dp(activity, 4),
                SettingsPanel.dp(activity, 8),
                0,
                SettingsPanel.dp(activity, 8)
            )
        }
        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.addView(TextView(activity).apply {
            text = title
            textSize = 16f
            setTextColor(colors.text)
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        labels.addView(TextView(activity).apply {
            text = summary
            textSize = 13f
            setTextColor(colors.muted)
            setPadding(0, SettingsPanel.dp(activity, 2), 0, 0)
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val toggle = Switch(activity).apply {
            isChecked = checked
            showText = false
            contentDescription = title
            thumbTintList = ColorStateList(
                states,
                intArrayOf(colors.accent, colors.muted)
            )
            trackTintList = ColorStateList(
                states,
                intArrayOf(withAlpha(colors.accent, 112), withAlpha(colors.muted, 72))
            )
        }
        container.addView(toggle, LinearLayout.LayoutParams(
            SettingsPanel.dp(activity, 56),
            SettingsPanel.dp(activity, 48)
        ).also { it.marginStart = SettingsPanel.dp(activity, 12) })
        return ToggleRow(container, toggle)
    }

    private fun addSection(
        content: LinearLayout,
        activity: Activity,
        colors: SettingsPanel.Colors,
        title: CharSequence
    ) {
        content.addView(TextView(activity).apply {
            text = title
            textSize = 13f
            setTextColor(colors.accent)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            SettingsPanel.dp(activity, 42)
        ).also { it.topMargin = SettingsPanel.dp(activity, 6) })
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

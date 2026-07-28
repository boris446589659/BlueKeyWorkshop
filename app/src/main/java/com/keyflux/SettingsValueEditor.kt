package com.keyflux

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.max

/** Compact, slider-based editor for a single numeric preference. */
internal object SettingsValueEditor {
    fun show(
        activity: Activity,
        title: CharSequence,
        value: String,
        defaultValue: Int,
        minimum: Int,
        maximum: Int,
        valueLabel: (Int) -> CharSequence,
        onSave: (Int) -> Unit
    ) {
        val colors = SettingsPanel.colors(activity)
        val upperBound = max(maximum, defaultValue)
        var selectedValue = (value.toIntOrNull() ?: defaultValue).coerceIn(minimum, upperBound)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                SettingsPanel.dp(activity, 28),
                SettingsPanel.dp(activity, 28),
                SettingsPanel.dp(activity, 20),
                SettingsPanel.dp(activity, 10)
            )
            background = SettingsPanel.rounded(colors.surface, SettingsPanel.dp(activity, 30))
        }
        root.addView(TextView(activity).apply {
            text = title
            textSize = 23f
            setTextColor(colors.text)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val valueView = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(colors.muted)
        }
        root.addView(valueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            SettingsPanel.dp(activity, 88)
        ).also {
            it.topMargin = SettingsPanel.dp(activity, 14)
        })

        val slider = SeekBar(activity).apply {
            max = upperBound - minimum
            progress = selectedValue - minimum
            progressTintList = ColorStateList.valueOf(colors.accent)
            thumbTintList = ColorStateList.valueOf(colors.accent)
        }
        root.addView(slider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also {
            it.leftMargin = SettingsPanel.dp(activity, 2)
            it.rightMargin = SettingsPanel.dp(activity, 2)
        })

        fun updateValue(progress: Int) {
            selectedValue = progress + minimum
            valueView.text = valueLabel(selectedValue)
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateValue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        updateValue(slider.progress)

        val actions = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        actions.addView(SettingsPanel.textAction(
            activity,
            Localization.getString("keyflux_theme_reset"),
            colors
        ) {
            slider.progress = (defaultValue.coerceIn(minimum, upperBound) - minimum)
        }, actionParams(activity))
        actions.addView(SettingsPanel.textAction(
            activity,
            activity.getString(android.R.string.cancel),
            colors
        ) { dialog.dismiss() }, actionParams(activity))
        actions.addView(SettingsPanel.textAction(
            activity,
            activity.getString(android.R.string.ok),
            colors
        ) {
            onSave(selectedValue)
            dialog.dismiss()
        }, actionParams(activity))
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            SettingsPanel.dp(activity, 56)
        ).also { it.topMargin = SettingsPanel.dp(activity, 10) })

        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.58f }
                setGravity(Gravity.CENTER)
                val width = (activity.resources.displayMetrics.widthPixels - SettingsPanel.dp(activity, 40))
                    .coerceAtMost(SettingsPanel.dp(activity, 520))
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
    }

    private fun actionParams(activity: Activity) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        SettingsPanel.dp(activity, 44)
    )
}

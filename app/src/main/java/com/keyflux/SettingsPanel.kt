package com.keyflux

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Full-height detail surface used by the theme editor and color picker.
 */
internal object SettingsPanel {
    enum class NavigationIcon { BACK, CLOSE }

    data class Handle(
        val dialog: Dialog,
        val content: LinearLayout,
        val footer: LinearLayout,
        val colors: Colors
    ) {
        fun show() {
            dialog.show()
        }
    }

    data class Colors(
        val surface: Int,
        val elevated: Int,
        val text: Int,
        val muted: Int,
        val divider: Int,
        val accent: Int,
        val onAccent: Int
    )

    fun colors(context: Context): Colors = palette(context)

    fun create(
        activity: Activity,
        title: CharSequence,
        navigationIcon: NavigationIcon = NavigationIcon.CLOSE,
        showFooter: Boolean = true
    ): Handle {
        val colors = palette(activity)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.surface)
        }

        val toolbar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, if (navigationIcon == NavigationIcon.BACK) 8 else 20),
                0,
                dp(activity, 8),
                0
            )
            minimumHeight = dp(activity, 64)
        }
        if (navigationIcon == NavigationIcon.BACK) {
            toolbar.addView(navigationButton(activity, colors, navigationIcon) { dialog.dismiss() }, LinearLayout.LayoutParams(
                dp(activity, 48), dp(activity, 48)
            ))
        }
        toolbar.addView(TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(colors.text)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        if (navigationIcon == NavigationIcon.CLOSE) {
            toolbar.addView(navigationButton(activity, colors, navigationIcon) { dialog.dismiss() }, LinearLayout.LayoutParams(
                dp(activity, 44), dp(activity, 44)
            ))
        }
        root.addView(toolbar, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 64)
        ))
        root.addView(divider(activity, colors), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)
        ))

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 20))
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val footer = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 12))
        }
        if (showFooter) {
            root.addView(divider(activity, colors), ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)
            ))
            root.addView(footer, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 72)
            ))
        }

        dialog.setContentView(root)
        // Configure before show so the first rendered frame is already full screen.
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(colors.surface))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.FILL)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            attributes = attributes.apply { windowAnimations = 0 }
            statusBarColor = colors.surface
            navigationBarColor = colors.surface
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = 0
                if (colors.surface == LIGHT_SURFACE) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                decorView.systemUiVisibility = flags
            }
        }
        return Handle(dialog, content, footer, colors)
    }

    fun addSection(content: LinearLayout, title: CharSequence, colors: Colors, context: Context) {
        content.addView(TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(colors.muted)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 34)
        ).also { it.topMargin = dp(context, 8) })
    }

    fun row(context: Context, colors: Colors): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 64)
        setPadding(dp(context, 16), dp(context, 8), dp(context, 12), dp(context, 8))
        background = rounded(colors.elevated, dp(context, 4), colors.divider)
    }

    fun actionButton(
        context: Context,
        text: CharSequence,
        colors: Colors,
        emphasized: Boolean = false,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        minHeight = 0
        minimumHeight = dp(context, 44)
        setPadding(dp(context, 16), 0, dp(context, 16), 0)
        setTextColor(if (emphasized) colors.onAccent else colors.text)
        background = rounded(
            if (emphasized) colors.accent else colors.elevated,
            dp(context, 4),
            if (emphasized) colors.accent else colors.divider
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) stateListAnimator = null
        elevation = 0f
        translationZ = 0f
        setOnClickListener { onClick() }
    }

    fun textAction(
        context: Context,
        text: CharSequence,
        colors: Colors,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        minHeight = 0
        minimumHeight = dp(context, 44)
        setPadding(dp(context, 12), 0, dp(context, 12), 0)
        setTextColor(colors.accent)
        background = ColorDrawable(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) stateListAnimator = null
        elevation = 0f
        translationZ = 0f
        setOnClickListener { onClick() }
    }

    fun divider(context: Context, colors: Colors): View = View(context).apply {
        setBackgroundColor(colors.divider)
    }

    fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            if (stroke != null) setStroke(1, stroke)
        }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun navigationButton(
        context: Context,
        colors: Colors,
        icon: NavigationIcon,
        onClick: () -> Unit
    ): ImageButton =
        ImageButton(context).apply {
            setImageResource(
                if (icon == NavigationIcon.BACK) android.R.drawable.ic_media_previous
                else android.R.drawable.ic_menu_close_clear_cancel
            )
            setColorFilter(colors.muted)
            contentDescription = if (icon == NavigationIcon.BACK) "Back" else "Close"
            background = ColorDrawable(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) stateListAnimator = null
            setOnClickListener { onClick() }
        }

    private fun palette(context: Context): Colors {
        val dark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val accent = resolveAccent(context, if (dark) 0xFF6BC5E8.toInt() else 0xFF0B6E99.toInt())
        return if (dark) {
            Colors(
                surface = 0xFF1B1D20.toInt(),
                elevated = 0xFF272A2F.toInt(),
                text = 0xFFF0F3F6.toInt(),
                muted = 0xFFB6C0CB.toInt(),
                divider = 0xFF3C424A.toInt(),
                accent = accent,
                onAccent = contrastColor(accent)
            )
        } else {
            Colors(
                surface = LIGHT_SURFACE,
                elevated = 0xFFFFFFFF.toInt(),
                text = 0xFF162733.toInt(),
                muted = 0xFF607381.toInt(),
                divider = 0xFFD8E1E8.toInt(),
                accent = accent,
                onAccent = contrastColor(accent)
            )
        }
    }

    private fun resolveAccent(context: Context, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
            if (value.resourceId != 0) runCatching { context.getColor(value.resourceId) }.getOrDefault(fallback)
            else value.data
        } else {
            fallback
        }
    }

    private fun contrastColor(color: Int): Int {
        val luminance = (Color.red(color) * 0.299) +
            (Color.green(color) * 0.587) + (Color.blue(color) * 0.114)
        return if (luminance >= 150) 0xFF162733.toInt() else Color.WHITE
    }

    private const val LIGHT_SURFACE = -591113
}

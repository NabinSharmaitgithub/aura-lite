package com.aura.aura_ui.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.services.AssistantForegroundService
import com.aura.aura_ui.ui.theme.MonoArgb
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Minimal AURA keyboard: type a goal, hand it to the agent, keep the field for the user.
 * It exists so AURA can fill fields in apps that block accessibility text injection.
 */
@AndroidEntryPoint
class AuraKeyboardService : InputMethodService() {

    @Inject lateinit var settings: AuraSettings

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MonoArgb.canvas)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val hint = TextView(this).apply {
            text = "Ask AURA to do something"
            setTextColor(MonoArgb.textSecondary)
            textSize = 12f
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val field = EditText(this).apply {
            hint = "Type a goal…"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(MonoArgb.textPrimary)
        }
        val send = Button(this).apply {
            text = "Run"
            setOnClickListener {
                val goal = field.text?.toString()?.trim().orEmpty()
                if (goal.isNotBlank()) {
                    AssistantForegroundService.submit(this@AuraKeyboardService, goal)
                    field.setText("")
                }
            }
        }
        row.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(send, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val close = Button(this).apply {
            text = "Hide keyboard"
            setOnClickListener { requestHideSelf(0) }
        }
        root.addView(hint)
        root.addView(row)
        root.addView(close)
        return root
    }
}

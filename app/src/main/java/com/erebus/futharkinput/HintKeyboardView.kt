package com.erebus.futharkinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

@Suppress("DEPRECATION")
class HintKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : KeyboardView(context, attrs, defStyle) {

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = 22f
        textAlign = Paint.Align.RIGHT
    }

    // Gboard-aligned symbol order matching our LONG_PRESS_SYMBOLS map
    private val hints = mapOf(
        113 to "@",  119 to "&",  101 to "€",  114 to "4",  116 to "5",
        121 to "6",  117 to "7",  105 to "8",  111 to "9",  112 to "0",
        97  to "*",  115 to "#",  100 to "$",  102 to "%",  103 to "^",
        104 to "-",  106 to "+",  107 to "(",  108 to ")",
        122 to "~",  120 to "!",  99  to "?",  118 to "/",
        98  to ";",  110 to "'",  109 to "\""
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kb = keyboard ?: return
        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: continue
            val hint = hints[code] ?: continue
            val x = (key.x + key.width - 8).toFloat()
            val y = (key.y + hintPaint.textSize + 4).toFloat()
            canvas.drawText(hint, x, y, hintPaint)
        }
    }
}
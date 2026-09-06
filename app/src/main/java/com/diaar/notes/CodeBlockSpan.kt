package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan

class CodeBlockSpan(private val bgColor: Int) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas, paint: Paint,
        left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int
    ) {
        val bg = Paint(paint)
        bg.color = bgColor
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), bg)
    }
}

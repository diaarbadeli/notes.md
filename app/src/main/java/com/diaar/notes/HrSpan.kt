package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan

/** Draws a thin horizontal rule spanning the full line width, used for a lone "---" line. */
class HrSpan(private val color: Int) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas, paint: Paint,
        left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int
    ) {
        val rulePaint = Paint(paint)
        rulePaint.color = color
        rulePaint.strokeWidth = 2f
        val midY = (top + bottom) / 2f
        canvas.drawLine(left.toFloat(), midY, right.toFloat(), midY, rulePaint)
    }
}

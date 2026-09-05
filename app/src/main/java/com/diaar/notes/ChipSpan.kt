package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.style.ReplacementSpan

/**
 * Renders its text on a filled, soft-cornered rounded-rect chip.
 * Used for timestamps in read mode: accent-colored fill, ink-colored text,
 * one step smaller than surrounding body text — still the system typeface.
 */
class ChipSpan(
    private val bgColor: Int,
    private val textColor: Int,
    private val textSizePx: Float,
    private val horizontalPad: Float = 14f,
    private val verticalPad: Float = 5f
) : ReplacementSpan() {

    private fun chipPaint(basePaint: Paint): TextPaint {
        val tp = TextPaint(basePaint)
        tp.color = textColor
        tp.textSize = textSizePx
        return tp
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val tp = chipPaint(paint)
        val width = tp.measureText(text, start, end)
        if (fm != null) {
            val pfm = paint.fontMetricsInt
            fm.ascent = pfm.ascent
            fm.descent = pfm.descent
            fm.top = pfm.top
            fm.bottom = pfm.bottom
        }
        return (width + horizontalPad * 2).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val tp = chipPaint(paint)
        val width = tp.measureText(text, start, end)
        val rect = RectF(
            x,
            y + paint.fontMetrics.ascent - verticalPad,
            x + width + horizontalPad * 2,
            y + paint.fontMetrics.descent + verticalPad
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = bgColor
        val corner = (rect.height()) * 0.35f
        canvas.drawRoundRect(rect, corner, corner, bgPaint)
        canvas.drawText(text, start, end, x + horizontalPad, y.toFloat(), tp)
    }
}

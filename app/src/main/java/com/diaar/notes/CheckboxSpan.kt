package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import androidx.core.graphics.ColorUtils

/**
 * Draws a small rounded-square checkbox glyph in place of one placeholder character.
 * Unchecked: stroke only, accent color, no fill.
 * Checked: filled accent color, with an ink-colored checkmark drawn on top.
 */
class CheckboxSpan(
    private val checked: Boolean,
    private val inkColor: Int,
    private val accentColor: Int,
    private val sizePx: Float
) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val pfm = paint.fontMetricsInt
            fm.ascent = pfm.ascent
            fm.descent = pfm.descent
            fm.top = pfm.top
            fm.bottom = pfm.bottom
        }
        return (sizePx * 1.4f).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val boxTop = y + paint.fontMetrics.ascent + (paint.fontMetrics.descent - paint.fontMetrics.ascent - sizePx) / 2f
        // Center within the allotted (wider) box rather than flush-left: the old flush-left
        // draw put all the padding on one side, which looked fine for LTR text (gap faces the
        // following text) but squished the glyph against RTL text (gap ended up on the wrong
        // side). Centering keeps an even gap on both sides regardless of paragraph direction.
        val totalWidth = sizePx * 1.4f
        val boxLeft = x + (totalWidth - sizePx) / 2f
        val rect = RectF(boxLeft, boxTop, boxLeft + sizePx, boxTop + sizePx)
        val corner = sizePx * 0.28f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (checked) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = accentColor
            canvas.drawRoundRect(rect, corner, corner, fillPaint)

            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            checkPaint.style = Paint.Style.STROKE
            checkPaint.color = inkColor
            checkPaint.strokeWidth = sizePx * 0.13f
            checkPaint.strokeCap = Paint.Cap.ROUND
            checkPaint.strokeJoin = Paint.Join.ROUND
            val path = android.graphics.Path()
            path.moveTo(rect.left + sizePx * 0.24f, rect.top + sizePx * 0.52f)
            path.lineTo(rect.left + sizePx * 0.42f, rect.top + sizePx * 0.72f)
            path.lineTo(rect.left + sizePx * 0.76f, rect.top + sizePx * 0.30f)
            canvas.drawPath(path, checkPaint)
        } else {
            fillPaint.style = Paint.Style.STROKE
            fillPaint.strokeWidth = sizePx * 0.1f
            fillPaint.color = ColorUtils.setAlphaComponent(inkColor, 204) // same dimmed tone as the bullet dot
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
        }
    }
}

/** Small helper so nothing outside this file needs to reason about alpha blending. */
fun dimmed(color: Int, factor: Float): Int = ColorUtils.blendARGB(color, 0x00000000, 1f - factor)

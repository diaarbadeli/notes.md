package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Replaces the visual of a single "-" character with a centered bullet dot.
 * The span must cover ONLY the dash itself (never any following spaces) —
 * otherwise its reported width silently swallows whatever whitespace follows.
 */
class BulletGlyphSpan(private val color: Int) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val dotPaint = Paint(paint)
        dotPaint.color = color
        val width = paint.measureText(text, start, end)
        val dotWidth = dotPaint.measureText("•")
        canvas.drawText("•", x + width / 2f - dotWidth / 2f, y.toFloat(), dotPaint)
    }
}

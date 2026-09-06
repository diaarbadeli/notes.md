package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/** Replaces the visual of "- " with a centered bullet dot, Obsidian-style. Text underneath is untouched. */
class BulletGlyphSpan(private val color: Int) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return (paint.measureText("- ")).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val dotPaint = Paint(paint)
        dotPaint.color = color
        val width = paint.measureText("- ")
        canvas.drawText("•", x + width * 0.32f, y.toFloat(), dotPaint)
    }
}

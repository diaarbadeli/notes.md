package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.LineBackgroundSpan

/**
 * Fills the background behind a fenced code block with soft corners: rounded only
 * where the block actually starts/ends, flat where it continues across wrapped lines,
 * so a multi-line block reads as one continuous rounded card.
 */
class CodeBlockSpan(
    private val bgColor: Int,
    private val blockStart: Int,
    private val blockEnd: Int,
    private val cornerRadiusPx: Float
) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas, paint: Paint,
        left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int
    ) {
        val bg = Paint(paint)
        bg.color = bgColor
        bg.style = Paint.Style.FILL

        val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bg)

        val isTopLine = start <= blockStart
        val isBottomLine = end >= blockEnd
        if (!isTopLine) {
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), top + cornerRadiusPx, bg)
        }
        if (!isBottomLine) {
            canvas.drawRect(left.toFloat(), bottom - cornerRadiusPx, right.toFloat(), bottom.toFloat(), bg)
        }
    }
}

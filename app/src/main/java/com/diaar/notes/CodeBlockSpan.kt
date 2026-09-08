package com.diaar.notes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.style.LineBackgroundSpan

/**
 * Fills the background behind a fenced code block with soft corners: rounded only
 * where the block actually starts/ends, flat where it continues across wrapped lines,
 * so a multi-line block reads as one continuous rounded card. Insets left/right so the
 * block doesn't run edge-to-edge, and draws a small copy icon in the top-right corner
 * of the block (once, on its first line only).
 */
class CodeBlockSpan(
    private val bgColor: Int,
    private val blockStart: Int,
    private val blockEnd: Int,
    private val cornerRadiusPx: Float,
    private val horizontalInsetPx: Float = 0f,
    private val copyIcon: Drawable? = null,
    private val copyIconSizePx: Int = 0,
    private val copyIconMarginPx: Float = 0f
) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas, paint: Paint,
        left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int
    ) {
        val bg = Paint(paint)
        bg.color = bgColor
        bg.style = Paint.Style.FILL

        val insetLeft = left + horizontalInsetPx
        val insetRight = right - horizontalInsetPx
        val rect = RectF(insetLeft, top.toFloat(), insetRight, bottom.toFloat())
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bg)

        val isTopLine = start <= blockStart
        val isBottomLine = end >= blockEnd
        if (!isTopLine) {
            canvas.drawRect(insetLeft, top.toFloat(), insetRight, top + cornerRadiusPx, bg)
        }
        if (!isBottomLine) {
            canvas.drawRect(insetLeft, bottom - cornerRadiusPx, insetRight, bottom.toFloat(), bg)
        }

        if (isTopLine && copyIcon != null && copyIconSizePx > 0) {
            val iconRight = (insetRight - copyIconMarginPx).toInt()
            val iconTop = (top + copyIconMarginPx).toInt()
            copyIcon.setBounds(iconRight - copyIconSizePx, iconTop, iconRight, iconTop + copyIconSizePx)
            copyIcon.draw(canvas)
        }
    }
}

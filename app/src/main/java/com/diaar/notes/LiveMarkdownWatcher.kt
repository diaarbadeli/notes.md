package com.diaar.notes

import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.graphics.ColorUtils

/**
 * Applies live, non-destructive markdown styling directly onto the EditText's own
 * Editable while typing (Obsidian-style live preview). Only spans are added/removed —
 * the underlying raw text is never touched, so typing/cursor/undo behave normally.
 */
class LiveMarkdownWatcher(
    var inkColor: Int,
    var accentColor: Int,
    var checkboxSizePx: Float,
    var chipTextSizePx: Float
) : TextWatcher {

    private val dimMarkerColor get() = ColorUtils.setAlphaComponent(inkColor, 90)
    private val dimCheckedColor get() = ColorUtils.setAlphaComponent(inkColor, 204) // ~80%
    private val hrColor = 0xFF2A2A2A.toInt()

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        clearOurSpans(s)
        applySpans(s)
    }

    private fun clearOurSpans(s: Editable) {
        s.getSpans(0, s.length, StyleSpan::class.java).forEach { s.removeSpan(it) }
        s.getSpans(0, s.length, ForegroundColorSpan::class.java).forEach { s.removeSpan(it) }
        s.getSpans(0, s.length, BackgroundColorSpan::class.java).forEach { s.removeSpan(it) }
        s.getSpans(0, s.length, CheckboxSpan::class.java).forEach { s.removeSpan(it) }
        s.getSpans(0, s.length, BulletGlyphSpan::class.java).forEach { s.removeSpan(it) }
        s.getSpans(0, s.length, HrSpan::class.java).forEach { s.removeSpan(it) }
    }

    private fun applySpans(s: Editable) {
        val raw = s.toString()
        var idx = 0
        val lines = raw.split("\n")

        for (line in lines) {
            val lineStart = idx
            val lineEnd = idx + line.length

            val hr = MarkdownRenderer.HR_LINE.matches(line.trim())
            val checkboxMatch = if (!hr) MarkdownRenderer.CHECKBOX_LINE.matchEntire(line) else null
            val bulletMatch = if (!hr && checkboxMatch == null) MarkdownRenderer.BULLET_LINE.matchEntire(line) else null

            when {
                hr -> {
                    if (lineEnd > lineStart) {
                        s.setSpan(ForegroundColorSpan(hrColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(HrSpan(hrColor), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                checkboxMatch != null -> {
                    val indent = checkboxMatch.groupValues[1]
                    val checked = checkboxMatch.groupValues[2].equals("x", ignoreCase = true)
                    val rest = checkboxMatch.groupValues[3]
                    val glyphStart = lineStart + indent.length
                    val prefixEnd = lineEnd - rest.length

                    if (prefixEnd > glyphStart) {
                        s.setSpan(CheckboxSpan(checked, inkColor, accentColor, checkboxSizePx), glyphStart, prefixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (checked && lineEnd > prefixEnd) {
                        s.setSpan(ForegroundColorSpan(dimCheckedColor), prefixEnd, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    applyInlineSpans(s, raw, prefixEnd, lineEnd)
                }
                bulletMatch != null -> {
                    val indent = bulletMatch.groupValues[1]
                    val bulletStart = lineStart + indent.length
                    val bulletEnd = bulletStart + 1 // the dash character only — never the spaces after it
                    if (bulletEnd <= lineEnd) {
                        s.setSpan(BulletGlyphSpan(dimCheckedColor), bulletStart, bulletEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    applyInlineSpans(s, raw, bulletEnd, lineEnd)
                }
                else -> applyInlineSpans(s, raw, lineStart, lineEnd)
            }

            idx = lineEnd + 1
        }
    }

    private fun applyInlineSpans(s: Editable, raw: String, start: Int, end: Int) {
        if (end <= start) return
        val segment = raw.substring(start, end)

        val inlineRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")
        for (m in inlineRegex.findAll(segment)) {
            val bold = m.groups[1] != null
            val markerLen = if (bold) 2 else 1
            val matchStart = start + m.range.first
            val matchEnd = start + m.range.last + 1
            val innerStart = matchStart + markerLen
            val innerEnd = matchEnd - markerLen
            if (innerEnd > innerStart) {
                s.setSpan(StyleSpan(if (bold) Typeface.BOLD else Typeface.ITALIC), innerStart, innerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            s.setSpan(ForegroundColorSpan(dimMarkerColor), matchStart, innerStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(dimMarkerColor), innerEnd, matchEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (range in TimestampFormat.findTimestampRanges(segment)) {
            val a = start + range.first
            val b = start + range.last + 1
            s.setSpan(BackgroundColorSpan(accentColor), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(inkColor), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (m in MarkdownRenderer.TAG_REGEX.findAll(segment)) {
            val a = start + m.range.first
            val b = start + m.range.last + 1
            s.setSpan(BackgroundColorSpan(accentColor), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(inkColor), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

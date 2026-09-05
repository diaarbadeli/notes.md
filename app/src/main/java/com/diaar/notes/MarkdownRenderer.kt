package com.diaar.notes

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.graphics.Typeface

/**
 * Turns the raw markdown buffer into a Spannable for read mode.
 * Supports exactly what this app itself produces: **bold**, *italic*,
 * "- [ ] " / "- [x] " checkboxes, and the app's own timestamp shapes.
 * Nothing else (no headers/links/lists/code) — kept deliberately tiny.
 */
object MarkdownRenderer {

    private val CHECKBOX_LINE = Regex("""^(\s*)-\s?\[( |x|X)\]\s?(.*)$""")
    private const val CHECKBOX_PLACEHOLDER = '\u00A0' // non-breaking space, drawn over by CheckboxSpan

    fun render(
        raw: String,
        inkColor: Int,
        accentColor: Int,
        bodyTextSizePx: Float,
        chipTextSizePx: Float,
        checkboxSizePx: Float,
        onToggleCheckbox: (rawLineStart: Int) -> Unit
    ): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        var rawIndex = 0
        val lines = raw.split("\n")

        lines.forEachIndexed { i, line ->
            val lineStartInRaw = rawIndex
            val match = CHECKBOX_LINE.matchEntire(line)

            if (match != null) {
                val indent = match.groupValues[1]
                val checked = match.groupValues[2].equals("x", ignoreCase = true)
                val rest = match.groupValues[3]

                out.append(indent)
                val placeholderStart = out.length
                out.append(CHECKBOX_PLACEHOLDER)
                val placeholderEnd = out.length
                out.setSpan(
                    CheckboxSpan(checked, inkColor, accentColor, checkboxSizePx),
                    placeholderStart, placeholderEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                out.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onToggleCheckbox(lineStartInRaw)
                    override fun updateDrawState(ds: android.text.TextPaint) { /* no underline/color change */ }
                }, placeholderStart, placeholderEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.append(" ")
                appendStyledText(out, rest, inkColor, accentColor, chipTextSizePx)
            } else {
                appendStyledText(out, line, inkColor, accentColor, chipTextSizePx)
            }

            if (i != lines.lastIndex) out.append("\n")
            rawIndex += line.length + 1 // +1 for the newline consumed between lines
        }

        return out
    }

    /** Parses **bold** / *italic* out of [lineText], applies chip spans to timestamps, appends to [out]. */
    private fun appendStyledText(out: SpannableStringBuilder, lineText: String, inkColor: Int, accentColor: Int, chipTextSizePx: Float) {
        val inlineRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")
        var cursor = 0
        val plain = StringBuilder()
        val styleRanges = mutableListOf<Triple<Int, Int, Int>>() // start, end, Typeface.BOLD/ITALIC

        for (m in inlineRegex.findAll(lineText)) {
            plain.append(lineText, cursor, m.range.first)
            val bold = m.groupValues[1]
            val text = if (bold.isNotEmpty()) bold else m.groupValues[2]
            val start = plain.length
            plain.append(text)
            styleRanges.add(Triple(start, plain.length, if (bold.isNotEmpty()) Typeface.BOLD else Typeface.ITALIC))
            cursor = m.range.last + 1
        }
        plain.append(lineText, cursor, lineText.length)

        val baseOffset = out.length
        out.append(plain)

        for ((s, e, style) in styleRanges) {
            out.setSpan(StyleSpan(style), baseOffset + s, baseOffset + e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (range in TimestampFormat.findTimestampRanges(plain.toString())) {
            out.setSpan(
                ChipSpan(accentColor, inkColor, chipTextSizePx),
                baseOffset + range.first, baseOffset + range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** Toggles the checkbox at [rawLineStart] within [raw], returning the updated full text. */
    fun toggleCheckboxAt(raw: String, rawLineStart: Int): String {
        val lineEnd = raw.indexOf('\n', rawLineStart).let { if (it == -1) raw.length else it }
        val line = raw.substring(rawLineStart, lineEnd)
        val match = CHECKBOX_LINE.matchEntire(line) ?: return raw
        val checked = match.groupValues[2].equals("x", ignoreCase = true)
        val newMark = if (checked) " " else "x"
        val newLine = "${match.groupValues[1]}- [$newMark] ${match.groupValues[3]}"
        return raw.substring(0, rawLineStart) + newLine + raw.substring(lineEnd)
    }
}

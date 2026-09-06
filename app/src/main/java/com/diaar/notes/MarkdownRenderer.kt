package com.diaar.notes

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils

/**
 * Turns the raw markdown buffer into a Spannable for read mode (shown whenever the
 * keyboard is closed). Supports exactly what this app itself produces: **bold**,
 * *italic*, "- [ ] " / "- [x] " checkboxes, plain "- " bullets, a lone "---" divider,
 * #tags, fenced ``` code blocks (tap to copy), and the app's own timestamp shapes.
 * Nothing else — kept deliberately tiny.
 */
object MarkdownRenderer {

    val CHECKBOX_LINE = Regex("""^(\s*)-\s?\[( |x|X)\]\s?(.*)$""")
    val BULLET_LINE = Regex("""^(\s*)-( +)(.*)$""")
    val HR_LINE = Regex("""^-{3,}$""")
    val TAG_REGEX = Regex("""(?<![\w#])#\w[\w-]*""")
    private val CODE_FENCE_REGEX = Regex("""```[^\n]*\n([\s\S]*?)```""")
    private const val CHECKBOX_PLACEHOLDER = '\u00A0'
    private const val COPY_ICON_PLACEHOLDER = '\u00A0'
    private const val BG_COLOR = 0xFF111111.toInt()

    fun render(
        context: android.content.Context,
        raw: String,
        inkColor: Int,
        accentColor: Int,
        bodyTextSizePx: Float,
        chipTextSizePx: Float,
        checkboxSizePx: Float,
        cornerRadiusPx: Float,
        onToggleCheckbox: (rawLineStart: Int) -> Unit,
        onCopyCodeBlock: (String) -> Unit = {}
    ): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        var lastEnd = 0

        for (m in CODE_FENCE_REGEX.findAll(raw)) {
            renderLines(raw.substring(lastEnd, m.range.first), lastEnd, inkColor, accentColor, chipTextSizePx, checkboxSizePx, out, onToggleCheckbox)
            if (out.isNotEmpty() && out.last() != '\n') out.append("\n")
            renderCodeBlock(context, m.groupValues[1], inkColor, cornerRadiusPx, out, onCopyCodeBlock)
            lastEnd = m.range.last + 1
        }
        renderLines(raw.substring(lastEnd), lastEnd, inkColor, accentColor, chipTextSizePx, checkboxSizePx, out, onToggleCheckbox)

        return out
    }

    private fun renderCodeBlock(
        context: android.content.Context, code: String, inkColor: Int, cornerRadiusPx: Float,
        out: SpannableStringBuilder, onCopy: (String) -> Unit
    ) {
        val trimmed = code.removeSuffix("\n")
        val start = out.length
        out.append(trimmed)
        out.append("  ")
        val iconStart = out.length
        out.append(COPY_ICON_PLACEHOLDER)
        val end = out.length

        if (end > start) {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_copy)?.mutate()
            if (drawable != null) {
                val size = (cornerRadiusPx * 3.2f).toInt().coerceAtLeast(1)
                drawable.setBounds(0, 0, size, size)
                drawable.setTint(inkColor)
                out.setSpan(android.text.style.ImageSpan(drawable, android.text.style.ImageSpan.ALIGN_BASELINE), iconStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            out.setSpan(TypefaceSpan("monospace"), start, iconStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(CodeBlockSpan(0xFF1A1A1A.toInt(), start, end, cornerRadiusPx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = onCopy(trimmed)
                override fun updateDrawState(ds: android.text.TextPaint) { ds.color = inkColor }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        out.append("\n")
    }

    private fun renderLines(
        raw: String, baseOffset: Int, inkColor: Int, accentColor: Int, chipTextSizePx: Float, checkboxSizePx: Float,
        out: SpannableStringBuilder, onToggleCheckbox: (Int) -> Unit
    ) {
        var rawIndex = 0
        val lines = raw.split("\n")

        lines.forEachIndexed { i, line ->
            val lineStartInRaw = baseOffset + rawIndex

            when {
                HR_LINE.matches(line.trim()) -> {
                    val start = out.length
                    out.append(line)
                    out.setSpan(ForegroundColorSpan(BG_COLOR), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(HrSpan(0xFF2A2A2A.toInt()), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                CHECKBOX_LINE.matchEntire(line) != null -> {
                    val match = CHECKBOX_LINE.matchEntire(line)!!
                    val indent = match.groupValues[1]
                    val checked = match.groupValues[2].equals("x", ignoreCase = true)
                    val rest = match.groupValues[3]

                    out.append(indent)
                    val placeholderStart = out.length
                    out.append(CHECKBOX_PLACEHOLDER)
                    out.setSpan(
                        CheckboxSpan(checked, inkColor, accentColor, checkboxSizePx),
                        placeholderStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    out.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) = onToggleCheckbox(lineStartInRaw)
                        override fun updateDrawState(ds: android.text.TextPaint) {}
                    }, placeholderStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.append(" ")
                    val restStart = out.length
                    appendStyledText(out, rest, inkColor, accentColor, chipTextSizePx)
                    if (checked) {
                        out.setSpan(
                            ForegroundColorSpan(ColorUtils.setAlphaComponent(inkColor, 204)),
                            restStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

                BULLET_LINE.matchEntire(line) != null -> {
                    val match = BULLET_LINE.matchEntire(line)!!
                    val indent = match.groupValues[1]
                    val spaces = match.groupValues[2]
                    val rest = match.groupValues[3]
                    out.append(indent)
                    val bulletStart = out.length
                    out.append("-")
                    out.setSpan(BulletGlyphSpan(ColorUtils.setAlphaComponent(inkColor, 204)), bulletStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.append(spaces)
                    appendStyledText(out, rest, inkColor, accentColor, chipTextSizePx)
                }

                else -> appendStyledText(out, line, inkColor, accentColor, chipTextSizePx)
            }

            if (i != lines.lastIndex) out.append("\n")
            rawIndex += line.length + 1
        }
    }

    private fun appendStyledText(out: SpannableStringBuilder, lineText: String, inkColor: Int, accentColor: Int, chipTextSizePx: Float) {
        val inlineRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")
        var cursor = 0
        val plain = StringBuilder()
        val styleRanges = mutableListOf<Triple<Int, Int, Int>>()

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

        val plainStr = plain.toString()
        val chipRanges = TimestampFormat.findTimestampRanges(plainStr).toMutableList()
        for (m in TAG_REGEX.findAll(plainStr)) chipRanges.add(m.range)

        for (range in chipRanges.sortedBy { it.first }) {
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

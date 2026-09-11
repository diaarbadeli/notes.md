package com.diaar.notes

import android.graphics.Color

/**
 * Parses a "change4colors"/"welcome" style color directive: four lines (anywhere in the
 * file, any order) each naming one of background/copyblocks/accent/text followed by a hex
 * color. Forgiving on purpose: case-insensitive labels, tolerates one or two leading '#'
 * on the hex code (a likely typo), and doesn't care what else is in the file around it.
 */
object ColorDirectives {

    data class ParsedColors(val background: Int, val copyBlocks: Int, val accent: Int, val text: Int)

    private val LINE_REGEX = Regex(
        """(?i)\b(background|copyblocks|accent|text)\b[^#\n]{0,10}#{1,2}([0-9a-f]{6})\b"""
    )

    /** Returns the parsed colors if all four labels were found, else null (not a valid directive). */
    fun parse(content: String): ParsedColors? {
        val found = mutableMapOf<String, Int>()
        for (m in LINE_REGEX.findAll(content)) {
            val label = m.groupValues[1].lowercase()
            val hex = "#" + m.groupValues[2]
            try {
                found[label] = Color.parseColor(hex)
            } catch (_: IllegalArgumentException) { /* malformed hex — skip this match */ }
        }
        val bg = found["background"] ?: return null
        val cb = found["copyblocks"] ?: return null
        val ac = found["accent"] ?: return null
        val tx = found["text"] ?: return null
        return ParsedColors(bg, cb, ac, tx)
    }

    /** True for a file named (ignoring ".md" and case) "welcome" or "change4colors". */
    fun isMonitoredFileName(name: String): Boolean {
        val base = name.removeSuffix(".md").removeSuffix(".MD").trim().lowercase()
        return base == "welcome" || base == "change4colors"
    }
}

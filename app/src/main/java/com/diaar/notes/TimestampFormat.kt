package com.diaar.notes

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Three timestamp shapes the Date/time toolbar button can insert.
 * Obsidian-style (ISO-ish, day-first, 24-hour, no slashes/dots/AM-PM):
 *   CLOCK_24H       -> "14:32"
 *   DATE_TIME       -> "2026-09-06 14:32"
 *   SHORT_DATE_TIME -> "09-06 14:32"
 */
object TimestampFormat {

    fun format(style: Int, date: Date = Date()): String {
        val pattern = when (style) {
            DateStyle.DATE_TIME -> "yyyy-MM-dd HH:mm"
            DateStyle.SHORT_DATE_TIME -> "MM-dd HH:mm"
            else -> "HH:mm"
        }
        return SimpleDateFormat(pattern, Locale.ENGLISH).format(date)
    }

    // Longest / most specific pattern first so overlap resolution is straightforward.
    val DATE_TIME_REGEX = Regex("""\b\d{4}-\d{2}-\d{2} \d{2}:\d{2}\b""")
    val SHORT_DATE_TIME_REGEX = Regex("""\b\d{2}-\d{2} \d{2}:\d{2}\b""")
    val CLOCK_REGEX = Regex("""\b([01]\d|2[0-3]):[0-5]\d\b""")

    /** Finds non-overlapping timestamp ranges in [text], preferring longer/more specific matches. */
    fun findTimestampRanges(text: String): List<IntRange> {
        val used = BooleanArray(text.length)
        val ranges = mutableListOf<IntRange>()

        fun collect(regex: Regex) {
            for (m in regex.findAll(text)) {
                val r = m.range
                if (r.isEmpty()) continue
                var overlaps = false
                for (i in r) if (used[i]) { overlaps = true; break }
                if (!overlaps) {
                    ranges.add(r)
                    for (i in r) used[i] = true
                }
            }
        }

        collect(DATE_TIME_REGEX)
        collect(SHORT_DATE_TIME_REGEX)
        collect(CLOCK_REGEX)
        return ranges.sortedBy { it.first }
    }
}

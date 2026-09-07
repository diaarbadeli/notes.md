package com.diaar.notes

import android.content.Context
import android.net.Uri

/** Thin wrapper over SharedPreferences for the handful of settings this app needs. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    var targetFolderUri: Uri?
        get() = sp.getString(KEY_FOLDER, null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString(KEY_FOLDER, value?.toString()).apply()

    var currentFileUri: Uri?
        get() = sp.getString(KEY_CURRENT_FILE, null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString(KEY_CURRENT_FILE, value?.toString()).apply()

    /** Ordered CSV of ToolbarButton names, persisted after any drag-reorder. */
    var toolbarOrder: List<String>?
        get() = sp.getString(KEY_ORDER, null)?.split(",")?.filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_ORDER, value?.joinToString(",")).apply()

    var dateFormatStyle: Int
        get() = sp.getInt(KEY_DATE_STYLE, DateStyle.CLOCK_24H)
        set(value) = sp.edit().putInt(KEY_DATE_STYLE, value).apply()

    /** Shared text size (sp) for both edit and read mode — set via pinch-to-zoom, persists across restarts. */
    var textSizeSp: Float
        get() = sp.getFloat(KEY_TEXT_SIZE, 16f)
        set(value) = sp.edit().putFloat(KEY_TEXT_SIZE, value).apply()

    /** Curated recents shown in the Load menu — user-removable, pinnable. Encoded one-entry-per-line. */
    var recentNotes: List<RecentEntry>
        get() = sp.getString(KEY_RECENTS, null)
            ?.split("\n")
            ?.mapNotNull { line ->
                val parts = line.split("|||")
                if (parts.size == 3) RecentEntry(parts[0], parts[1], parts[2] == "1") else null
            } ?: emptyList()
        set(value) = sp.edit().putString(
            KEY_RECENTS,
            value.joinToString("\n") { "${it.uri}|||${it.name}|||${if (it.pinned) "1" else "0"}" }
        ).apply()

    companion object {
        private const val KEY_FOLDER = "target_folder_uri"
        private const val KEY_CURRENT_FILE = "current_file_uri"
        private const val KEY_ORDER = "toolbar_order"
        private const val KEY_DATE_STYLE = "date_format_style"
        private const val KEY_TEXT_SIZE = "text_size_sp"
        private const val KEY_RECENTS = "recent_notes"
    }
}

data class RecentEntry(val uri: String, val name: String, val pinned: Boolean)

object DateStyle {
    const val CLOCK_24H = 0
    const val DATE_TIME = 1
    const val SHORT_DATE_TIME = 2
}

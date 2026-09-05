package com.diaar.notes

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private enum class PendingFolderAction { NONE, SET_ONLY, CREATE_NEW_AFTER }

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var editor: EditText
    private lateinit var preview: TextView
    private lateinit var fabMode: ImageButton
    private lateinit var toolbarRecycler: RecyclerView
    private lateinit var adapter: ToolbarAdapter

    private var isPreviewMode = false
    private var suppressWatcher = false
    private var pendingFolderAction = PendingFolderAction.NONE

    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { writeCurrentFile() }

    // --- simple snapshot-based undo (coalesced so it doesn't push on every keystroke) ---
    private val undoStack = ArrayDeque<String>()
    private var lastUndoPushAt = 0L
    private var lastSnapshot = ""

    private val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.targetFolderUri = uri
            if (pendingFolderAction == PendingFolderAction.CREATE_NEW_AFTER) promptNewNoteName()
        }
        pendingFolderAction = PendingFolderAction.NONE
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* some providers won't persist; still usable this session */ }
            openFileIntoEditor(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        editor = findViewById(R.id.editor)
        preview = findViewById(R.id.preview)
        fabMode = findViewById(R.id.fabMode)
        toolbarRecycler = findViewById(R.id.toolbar)

        setupToolbar()
        setupEditor()
        fabMode.setOnClickListener { toggleMode() }

        prefs.currentFileUri?.let { uri ->
            try {
                openFileIntoEditor(uri)
            } catch (_: Exception) {
                prefs.currentFileUri = null
            }
        }
    }

    // ---------------------------------------------------------------- toolbar

    private fun defaultOrder() = listOf(
        ToolbarButton.UNDO, ToolbarButton.BOLD, ToolbarButton.ITALIC,
        ToolbarButton.CHECKBOX, ToolbarButton.DATETIME, ToolbarButton.LOAD, ToolbarButton.NEW
    )

    private fun restoreOrder(): List<ToolbarButton> {
        val saved = prefs.toolbarOrder ?: return defaultOrder()
        val byName = ToolbarButton.entries.associateBy { it.name }
        val restored = saved.mapNotNull { byName[it] }.toMutableList()
        // If a future update adds new buttons, make sure they still show up.
        for (b in defaultOrder()) if (b !in restored) restored.add(b)
        return restored
    }

    private fun setupToolbar() {
        adapter = ToolbarAdapter(
            initialOrder = restoreOrder(),
            onClick = { onToolbarClick(it) },
            onLongPressNew = {
                pendingFolderAction = PendingFolderAction.SET_ONLY
                openTreeLauncher.launch(prefs.targetFolderUri)
            },
            onSwipeUpDateTime = { showDateFormatPicker() },
            onOrderChanged = { prefs.toolbarOrder = it.map { b -> b.name } }
        )
        toolbarRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        toolbarRecycler.adapter = adapter
        adapter.touchHelper.attachToRecyclerView(toolbarRecycler)
    }

    private fun onToolbarClick(button: ToolbarButton) {
        when (button) {
            ToolbarButton.UNDO -> doUndo()
            ToolbarButton.BOLD -> wrapSelectionOrInsert("**")
            ToolbarButton.ITALIC -> wrapSelectionOrInsert("*")
            ToolbarButton.CHECKBOX -> insertCheckboxAtLineStart()
            ToolbarButton.DATETIME -> insertTimestamp()
            ToolbarButton.LOAD -> openLoadDialog()
            ToolbarButton.NEW -> {
                if (prefs.targetFolderUri == null) {
                    pendingFolderAction = PendingFolderAction.CREATE_NEW_AFTER
                    openTreeLauncher.launch(null)
                } else {
                    promptNewNoteName()
                }
            }
        }
    }

    // ---------------------------------------------------------------- editor + autosave

    private fun setupEditor() {
        if (isPreviewMode) return
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return
                maybePushUndoSnapshot()
                scheduleAutosave()
            }
        })
    }

    private fun maybePushUndoSnapshot() {
        val now = System.currentTimeMillis()
        if (now - lastUndoPushAt > 600) {
            undoStack.addLast(lastSnapshot)
            if (undoStack.size > 100) undoStack.removeFirst()
            lastUndoPushAt = now
        }
        lastSnapshot = editor.text.toString()
    }

    private fun doUndo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        suppressWatcher = true
        val cursor = previous.length
        editor.setText(previous)
        editor.setSelection(cursor.coerceIn(0, previous.length))
        suppressWatcher = false
        lastSnapshot = previous
        scheduleAutosave()
    }

    private fun wrapSelectionOrInsert(marker: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        val text = editor.text
        if (start != end) {
            val lo = minOf(start, end)
            val hi = maxOf(start, end)
            text.insert(hi, marker)
            text.insert(lo, marker)
            editor.setSelection(hi + marker.length * 2)
        } else {
            text.insert(start, marker + marker)
            editor.setSelection(start + marker.length)
        }
    }

    private fun insertCheckboxAtLineStart() {
        val cursor = editor.selectionStart.coerceAtLeast(0)
        val text = editor.text.toString()
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        editor.text.insert(lineStart, "- [ ] ")
    }

    private fun insertTimestamp() = insertAtCursor(TimestampFormat.format(prefs.dateFormatStyle))

    private fun insertAtCursor(text: String) {
        val cursor = editor.selectionStart.coerceAtLeast(0)
        editor.text.insert(cursor, text)
    }

    private fun showDateFormatPicker() {
        val group = RadioGroup(this)
        group.orientation = RadioGroup.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        group.setPadding(pad, pad, pad, pad)

        val options = listOf(
            DateStyle.CLOCK_24H to getString(R.string.format_24h),
            DateStyle.DATE_TIME to getString(R.string.format_date_time),
            DateStyle.SHORT_DATE_TIME to getString(R.string.format_short)
        )
        val buttons = options.map { (style, label) ->
            RadioButton(this).apply {
                text = label
                setTextColor(0xFFDFCBC9.toInt())
                isChecked = style == prefs.dateFormatStyle
                tag = style
            }
        }
        buttons.forEach { group.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Timestamp format")
            .setView(group)
            .setNegativeButton(R.string.cancel, null)
            .create()

        buttons.forEach { rb ->
            rb.setOnClickListener {
                prefs.dateFormatStyle = rb.tag as Int
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun scheduleAutosave() {
        autosaveHandler.removeCallbacks(autosaveRunnable)
        autosaveHandler.postDelayed(autosaveRunnable, 400)
    }

    private fun writeCurrentFile() {
        val uri = prefs.currentFileUri ?: return
        try {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(editor.text.toString().toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // File may have become inaccessible (deleted/moved outside the app) — fail silently,
            // the user's text is still safely in the editor and will save once access returns.
        }
    }

    // ---------------------------------------------------------------- open / create

    private fun openFileIntoEditor(uri: Uri) {
        val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        suppressWatcher = true
        editor.setText(text)
        editor.setSelection(text.length)
        suppressWatcher = false
        lastSnapshot = text
        undoStack.clear()
        prefs.currentFileUri = uri
        if (isPreviewMode) renderPreview()
    }

    private fun promptNewNoteName() {
        val input = EditText(this)
        input.hint = getString(R.string.new_note_hint)
        input.setTextColor(0xFFDFCBC9.toInt())
        input.setHintTextColor(0x88DFCBC9.toInt())
        val pad = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)

        AlertDialog.Builder(this)
            .setTitle(R.string.new_note_title)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val raw = input.text.toString().trim()
                val name = if (raw.isEmpty()) "Untitled" else raw
                createNewNote(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createNewNote(name: String) {
        val folderUri = prefs.targetFolderUri ?: return
        val dir = DocumentFile.fromTreeUri(this, folderUri) ?: return
        val finalName = if (name.endsWith(".md", ignoreCase = true)) name else "$name.md"
        val newFile = dir.createFile("text/markdown", finalName) ?: return

        suppressWatcher = true
        editor.setText("")
        suppressWatcher = false
        lastSnapshot = ""
        undoStack.clear()
        prefs.currentFileUri = newFile.uri
        if (isPreviewMode) renderPreview()
    }

    // ---------------------------------------------------------------- load dialog

    private fun openLoadDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_open_note, null)
        val browseRow = view.findViewById<TextView>(R.id.browseRow)
        val fileList = view.findViewById<RecyclerView>(R.id.fileList)
        val emptyLabel = view.findViewById<TextView>(R.id.emptyLabel)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_open_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        browseRow.setOnClickListener {
            dialog.dismiss()
            openDocumentLauncher.launch(arrayOf("text/markdown", "text/plain", "text/*"))
        }

        val folderUri = prefs.targetFolderUri
        val files: List<Pair<String, Uri>> = if (folderUri != null) {
            DocumentFile.fromTreeUri(this, folderUri)
                ?.listFiles()
                ?.filter { it.isFile && it.name?.endsWith(".md", ignoreCase = true) == true }
                ?.sortedByDescending { it.lastModified() }
                ?.map { (it.name ?: "untitled.md") to it.uri }
                ?: emptyList()
        } else emptyList()

        if (files.isEmpty()) {
            fileList.visibility = android.view.View.GONE
            emptyLabel.visibility = android.view.View.VISIBLE
            emptyLabel.text = if (folderUri == null) getString(R.string.no_folder_set) else getString(R.string.no_notes_here)
        } else {
            fileList.layoutManager = LinearLayoutManager(this)
            fileList.adapter = NoteListAdapter(files) { uri ->
                dialog.dismiss()
                openFileIntoEditor(uri)
            }
        }

        dialog.show()
    }

    // ---------------------------------------------------------------- read/write mode

    private fun toggleMode() {
        isPreviewMode = !isPreviewMode
        if (isPreviewMode) {
            renderPreview()
            editor.visibility = android.view.View.GONE
            preview.visibility = android.view.View.VISIBLE
            fabMode.setImageResource(R.drawable.ic_pencil)
        } else {
            preview.visibility = android.view.View.GONE
            editor.visibility = android.view.View.VISIBLE
            fabMode.setImageResource(R.drawable.ic_eye)
            editor.requestFocus()
        }
    }

    private fun renderPreview() {
        val ink = 0xFFDFCBC9.toInt()
        val accent = 0xFF072331.toInt()
        val bodySize = editor.textSize
        preview.text = MarkdownRenderer.render(
            raw = editor.text.toString(),
            inkColor = ink,
            accentColor = accent,
            bodyTextSizePx = bodySize,
            chipTextSizePx = bodySize * 0.8f,
            checkboxSizePx = bodySize * 0.85f
        ) { rawLineStart -> toggleCheckboxAndSave(rawLineStart) }
        preview.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun toggleCheckboxAndSave(rawLineStart: Int) {
        val newRaw = MarkdownRenderer.toggleCheckboxAt(editor.text.toString(), rawLineStart)
        suppressWatcher = true
        editor.setText(newRaw)
        suppressWatcher = false
        lastSnapshot = newRaw
        renderPreview()
        scheduleAutosave()
    }

    override fun onPause() {
        super.onPause()
        autosaveHandler.removeCallbacks(autosaveRunnable)
        writeCurrentFile() // make sure nothing is lost if the user backgrounds mid-debounce
    }
}

package com.diaar.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingFolderAction { NONE, SET_ONLY, CREATE_NEW_AFTER }

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var root: View
    private lateinit var editor: EditText
    private lateinit var preview: TextView
    private lateinit var toolbarRecycler: RecyclerView
    private lateinit var adapter: ToolbarAdapter

    private var suppressWatcher = false
    private var pendingFolderAction = PendingFolderAction.NONE
    private var keyboardVisible = false

    private val ink = 0xFFDFCBC9.toInt()
    private val accent = 0xFF072331.toInt()

    private val autosaveHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { writeCurrentFile() }

    private val undoStack = ArrayDeque<String>()
    private var lastUndoPushAt = 0L
    private var lastSnapshot = ""

    private val openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.targetFolderUri = uri
            if (pendingFolderAction == PendingFolderAction.CREATE_NEW_AFTER) createNewNote(defaultNoteName())
        }
        pendingFolderAction = PendingFolderAction.NONE
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* some providers won't persist; still usable this session */ }
            openFileIntoEditor(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        root = findViewById(R.id.root)
        editor = findViewById(R.id.editor)
        preview = findViewById(R.id.preview)
        toolbarRecycler = findViewById(R.id.toolbar)

        setupToolbar()
        setupEditor()
        setupKeyboardVisibilityTracking()

        preview.setOnClickListener {
            editor.visibility = View.VISIBLE
            preview.visibility = View.GONE
            editor.requestFocus()
            showKeyboardOn(editor)
        }

        prefs.currentFileUri?.let { uri ->
            try { openFileIntoEditor(uri) } catch (_: Exception) { prefs.currentFileUri = null }
        }
        renderPreview()
        editor.visibility = View.GONE
        preview.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- keyboard-driven mode

    private fun setupKeyboardVisibilityTracking() {
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val r = Rect()
                root.getWindowVisibleDisplayFrame(r)
                val screenHeight = root.rootView.height
                val keypadHeight = screenHeight - r.bottom
                val isVisible = keypadHeight > screenHeight * 0.15

                if (isVisible != keyboardVisible) {
                    keyboardVisible = isVisible
                    onKeyboardVisibilityChanged(isVisible, keypadHeight)
                }
            }
        })
    }

    private fun onKeyboardVisibilityChanged(visible: Boolean, keypadHeightPx: Int) {
        if (visible) {
            editor.visibility = View.VISIBLE
            preview.visibility = View.GONE
            toolbarRecycler.visibility = View.VISIBLE
            val lp = toolbarRecycler.layoutParams as android.widget.FrameLayout.LayoutParams
            val margin = (12 * resources.displayMetrics.density).toInt()
            lp.bottomMargin = keypadHeightPx + margin
            toolbarRecycler.layoutParams = lp
        } else {
            renderPreview()
            editor.visibility = View.GONE
            preview.visibility = View.VISIBLE
            toolbarRecycler.visibility = View.GONE
        }
    }

    private fun showKeyboardOn(view: View) {
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    // ---------------------------------------------------------------- toolbar

    private fun defaultOrder() = listOf(
        ToolbarButton.UNDO, ToolbarButton.NEW, ToolbarButton.LOAD, ToolbarButton.CHECKBOX,
        ToolbarButton.DATETIME, ToolbarButton.ITALIC, ToolbarButton.BOLD, ToolbarButton.CODEBLOCK
    )

    private fun restoreOrder(): List<ToolbarButton> {
        val saved = prefs.toolbarOrder ?: return defaultOrder()
        val byName = ToolbarButton.entries.associateBy { it.name }
        val restored = saved.mapNotNull { byName[it] }.toMutableList()
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
            onSwipeUp = { button, view -> onToolbarSwipeUp(button, view) },
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
            ToolbarButton.CODEBLOCK -> insertCodeBlock()
            ToolbarButton.LOAD -> openDocumentLauncher.launch(arrayOf("text/markdown", "text/plain", "text/*"))
            ToolbarButton.NEW -> {
                if (prefs.targetFolderUri == null) {
                    pendingFolderAction = PendingFolderAction.CREATE_NEW_AFTER
                    openTreeLauncher.launch(null)
                } else {
                    createNewNote(defaultNoteName())
                }
            }
        }
    }

    private fun onToolbarSwipeUp(button: ToolbarButton, anchor: View) {
        when (button) {
            ToolbarButton.NEW -> showNewNamePopup(anchor)
            ToolbarButton.LOAD -> showLoadListPopup(anchor)
            ToolbarButton.DATETIME -> showDateFormatPopup(anchor)
            else -> {}
        }
    }

    // ---------------------------------------------------------------- anchored popups

    private fun showNewNamePopup(anchor: View) {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_new_name, null)
        val input = view.findViewById<EditText>(R.id.nameInput)
        val confirm = view.findViewById<ImageButton>(R.id.confirmBtn)
        input.setText(defaultNoteName())
        input.setSelection(input.text.length)

        val popup = AnchoredPopup.showAbove(this, anchor, view)
        val create = {
            val raw = input.text.toString().trim()
            popup.dismiss()
            if (prefs.targetFolderUri == null) {
                pendingFolderAction = PendingFolderAction.CREATE_NEW_AFTER
                openTreeLauncher.launch(null)
            } else {
                createNewNote(if (raw.isEmpty()) defaultNoteName() else raw)
            }
        }
        confirm.setOnClickListener { create() }
        input.setOnEditorActionListener { _, _, _ -> create(); true }
    }

    private fun showLoadListPopup(anchor: View) {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_note_list, null)
        val fileList = view.findViewById<RecyclerView>(R.id.fileList)
        val emptyLabel = view.findViewById<TextView>(R.id.emptyLabel)

        val folderUri = prefs.targetFolderUri
        val files: List<Pair<String, Uri>> = if (folderUri != null) {
            DocumentFile.fromTreeUri(this, folderUri)
                ?.listFiles()
                ?.filter { it.isFile && it.name?.endsWith(".md", ignoreCase = true) == true }
                ?.sortedByDescending { it.lastModified() }
                ?.map { (it.name ?: "untitled.md") to it.uri }
                ?: emptyList()
        } else emptyList()

        val popup = AnchoredPopup.showAbove(this, anchor, view)

        if (files.isEmpty()) {
            fileList.visibility = View.GONE
            emptyLabel.visibility = View.VISIBLE
            emptyLabel.text = if (folderUri == null) getString(R.string.no_folder_set) else getString(R.string.no_notes_here)
        } else {
            fileList.layoutManager = LinearLayoutManager(this)
            fileList.adapter = NoteListAdapter(files) { uri ->
                popup.dismiss()
                openFileIntoEditor(uri)
            }
        }
    }

    private fun showDateFormatPopup(anchor: View) {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_date_format, null)
        val popup = AnchoredPopup.showAbove(this, anchor, view)
        val rows = mapOf(
            R.id.opt24h to DateStyle.CLOCK_24H,
            R.id.optDateTime to DateStyle.DATE_TIME,
            R.id.optShort to DateStyle.SHORT_DATE_TIME
        )
        for ((id, style) in rows) {
            view.findViewById<TextView>(id).setOnClickListener {
                prefs.dateFormatStyle = style
                popup.dismiss()
            }
        }
    }

    // ---------------------------------------------------------------- editor + autosave

    private fun setupEditor() {
        editor.addTextChangedListener(LiveMarkdownWatcher(ink, accent, editor.textSize * 0.85f, editor.textSize * 0.8f))
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
        editor.setText(previous)
        editor.setSelection(previous.length.coerceIn(0, previous.length))
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

    private fun insertCodeBlock() {
        val cursor = editor.selectionStart.coerceAtLeast(0)
        val insert = "```\n\n```"
        editor.text.insert(cursor, insert)
        editor.setSelection(cursor + 4) // land inside the fences, on the blank line
    }

    private fun insertTimestamp() = insertAtCursor(TimestampFormat.format(prefs.dateFormatStyle))

    private fun insertAtCursor(text: String) {
        val cursor = editor.selectionStart.coerceAtLeast(0)
        editor.text.insert(cursor, text)
    }

    private fun defaultNoteName(): String {
        val now = Date()
        val day = SimpleDateFormat("dd", Locale.ENGLISH).format(now)
        val month = SimpleDateFormat("MMM", Locale.ENGLISH).format(now).lowercase(Locale.ENGLISH)
        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(now)
        val time = SimpleDateFormat("HHmm", Locale.ENGLISH).format(now)
        return "$day-$month-$year-$time"
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
        } catch (_: Exception) { /* fails silently; text remains safe in the editor */ }
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
        if (!keyboardVisible) renderPreview()
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
        editor.requestFocus()
        showKeyboardOn(editor)
    }

    // ---------------------------------------------------------------- read mode render

    private fun renderPreview() {
        val bodySize = editor.textSize
        preview.text = MarkdownRenderer.render(
            raw = editor.text.toString(),
            inkColor = ink,
            accentColor = accent,
            bodyTextSizePx = bodySize,
            chipTextSizePx = bodySize * 0.8f,
            checkboxSizePx = bodySize * 0.85f,
            onToggleCheckbox = { rawLineStart -> toggleCheckboxAndSave(rawLineStart) },
            onCopyCodeBlock = { code -> copyToClipboard(code) }
        )
        preview.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
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
        writeCurrentFile()
    }
}

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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
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
import kotlin.math.abs

private enum class PendingFolderAction { NONE, SET_ONLY, CREATE_NEW_AFTER }

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var root: View
    private lateinit var editor: EditText
    private lateinit var preview: TextView
    private lateinit var previewScroll: android.widget.ScrollView
    private lateinit var toolbarRecycler: RecyclerView
    private lateinit var unsavedBanner: TextView
    private lateinit var wordCountPill: TextView
    private lateinit var adapter: ToolbarAdapter

    private var suppressWatcher = false
    private var pendingFolderAction = PendingFolderAction.NONE
    private var keyboardVisible = false
    private var currentTextSizeSp = 16f
    private lateinit var liveWatcher: LiveMarkdownWatcher
    private lateinit var scaleDetector: ScaleGestureDetector

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
        previewScroll = findViewById(R.id.previewScroll)
        toolbarRecycler = findViewById(R.id.toolbar)
        unsavedBanner = findViewById(R.id.unsavedBanner)
        wordCountPill = findViewById(R.id.wordCountPill)

        currentTextSizeSp = prefs.textSizeSp
        applyTextSize()
        applyTypography()

        setupToolbar()
        setupEditor()
        setupGestures()
        setupKeyboardVisibilityTracking()

        preview.setOnClickListener { enterEditMode() }

        prefs.currentFileUri?.let { uri ->
            try { openFileIntoEditor(uri) } catch (_: Exception) { prefs.currentFileUri = null }
        }
        updateUnsavedBanner()
        renderPreview()
        editor.visibility = View.GONE
        previewScroll.visibility = View.VISIBLE
    }

    private fun updateUnsavedBanner() {
        unsavedBanner.visibility = if (prefs.currentFileUri == null) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- keyboard-driven mode

    private fun setupKeyboardVisibilityTracking() {
        // windowSoftInputMode is "adjustResize", so the window itself already shrinks to
        // make room for the keyboard — we only need to know show/hide, never the keyboard's
        // pixel height (adding that on top of an already-resized window was the earlier bug:
        // it double-counted the keyboard and pushed the pill drastically out of place).
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val r = Rect()
                root.getWindowVisibleDisplayFrame(r)
                val screenHeight = root.rootView.height
                val keypadHeight = screenHeight - r.bottom
                val isVisible = keypadHeight > screenHeight * 0.15
                if (isVisible != keyboardVisible) {
                    keyboardVisible = isVisible
                    onKeyboardVisibilityChanged(isVisible)
                }
            }
        })
    }

    private fun onKeyboardVisibilityChanged(visible: Boolean) {
        if (visible) {
            editor.visibility = View.VISIBLE
            previewScroll.visibility = View.GONE
            wordCountPill.visibility = View.GONE
            val lp = toolbarRecycler.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            toolbarRecycler.layoutParams = lp
            showToolbarAnimated()
        } else {
            renderPreview()
            editor.visibility = View.GONE
            previewScroll.visibility = View.VISIBLE
            hideToolbarAnimated()
        }
    }

    private fun showToolbarAnimated() {
        if (toolbarRecycler.visibility == View.VISIBLE) return
        toolbarRecycler.visibility = View.VISIBLE
        toolbarRecycler.alpha = 0f
        toolbarRecycler.scaleX = 0.85f
        toolbarRecycler.scaleY = 0.85f
        toolbarRecycler.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun hideToolbarAnimated() {
        toolbarRecycler.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(120)
            .withEndAction { toolbarRecycler.visibility = View.GONE }.start()
    }

    private fun enterEditMode() {
        editor.visibility = View.VISIBLE
        previewScroll.visibility = View.GONE
        wordCountPill.visibility = View.GONE
        editor.requestFocus()
        showKeyboardOn(editor)
    }

    private fun exitEditModeToRead() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(editor.windowToken, 0)
        // the keyboard-visibility listener (IME insets or heuristic) takes it from here
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
            onClick = { button, view -> onToolbarClick(button, view) },
            onLongPress = { button, view -> onToolbarLongPress(button, view) },
            onSwipeUp = { button, view -> onToolbarSwipeUp(button, view) },
            onOrderChanged = { prefs.toolbarOrder = it.map { b -> b.name } }
        )
        toolbarRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        toolbarRecycler.adapter = adapter
        adapter.touchHelper.attachToRecyclerView(toolbarRecycler)
    }

    private fun onToolbarClick(button: ToolbarButton, anchor: View) {
        when (button) {
            ToolbarButton.UNDO -> doUndo()
            ToolbarButton.BOLD -> wrapSelectionOrInsert("**")
            ToolbarButton.ITALIC -> wrapSelectionOrInsert("*")
            ToolbarButton.CHECKBOX -> insertCheckboxAtLineStart()
            ToolbarButton.DATETIME -> insertTimestamp()
            ToolbarButton.CODEBLOCK -> insertCodeBlock()
            ToolbarButton.LOAD -> showLoadListPopup(anchor)
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

    // New: long-press renames the currently open file; swipe-up changes the target folder.
    // Load: tap opens the curated recents list; long-press opens the system file browser.
    // Date: long-press and swipe-up both open the format menu (redundant on purpose).
    private fun onToolbarLongPress(button: ToolbarButton, anchor: View) {
        when (button) {
            ToolbarButton.NEW -> showRenamePopup(anchor)
            ToolbarButton.LOAD -> openDocumentLauncher.launch(arrayOf("text/markdown", "text/plain", "text/*"))
            ToolbarButton.DATETIME -> showDateFormatPopup(anchor)
            else -> {}
        }
    }

    private fun onToolbarSwipeUp(button: ToolbarButton, anchor: View) {
        when (button) {
            ToolbarButton.NEW -> {
                pendingFolderAction = PendingFolderAction.SET_ONLY
                openTreeLauncher.launch(prefs.targetFolderUri)
            }
            ToolbarButton.DATETIME -> showDateFormatPopup(anchor)
            else -> {}
        }
    }

    // ---------------------------------------------------------------- anchored popups

    private fun showRenamePopup(anchor: View) {
        val uri = prefs.currentFileUri
        if (uri == null) {
            Toast.makeText(this, "No note open to rename", Toast.LENGTH_SHORT).show()
            return
        }
        val currentName = try {
            DocumentFile.fromSingleUri(this, uri)?.name?.removeSuffix(".md") ?: ""
        } catch (_: Exception) { "" }

        val view = LayoutInflater.from(this).inflate(R.layout.popup_new_name, null)
        val input = view.findViewById<EditText>(R.id.nameInput)
        val confirm = view.findViewById<ImageButton>(R.id.confirmBtn)
        input.setText(currentName)
        input.setSelection(input.text.length)

        val popup = AnchoredPopup.showAbove(this, anchor, view)
        val confirmRename = {
            val raw = input.text.toString().trim()
            popup.dismiss()
            if (raw.isNotEmpty()) renameCurrentFile(raw)
        }
        confirm.setOnClickListener { confirmRename() }
        input.setOnEditorActionListener { _, _, _ -> confirmRename(); true }
    }

    private fun renameCurrentFile(newNameRaw: String) {
        val uri = prefs.currentFileUri ?: return
        val finalName = if (newNameRaw.endsWith(".md", ignoreCase = true)) newNameRaw else "$newNameRaw.md"
        val oldUriString = uri.toString()

        try {
            // Prefer reaching the file through its granted tree — some providers only allow
            // rename/delete via a document reached through the tree they were granted access
            // to, not a bare single-document reference, even with read+write flags on that URI.
            val treeUri = prefs.targetFolderUri
            val treeAnchored = treeUri?.let { t ->
                DocumentFile.fromTreeUri(this, t)?.listFiles()?.firstOrNull { it.uri == uri }
            }
            val docFile = treeAnchored ?: DocumentFile.fromSingleUri(this, uri)

            if (docFile != null && docFile.renameTo(finalName)) {
                prefs.currentFileUri = docFile.uri
                val recents = prefs.recentNotes.toMutableList()
                val idx = recents.indexOfFirst { it.uri == oldUriString }
                if (idx >= 0) recents[idx] = recents[idx].copy(uri = docFile.uri.toString(), name = finalName)
                prefs.recentNotes = recents
            } else if (treeAnchored == null && treeUri != null) {
                Toast.makeText(
                    this,
                    "Can't rename — this file isn't in your target folder. Open it via Load's swipe-up list, or move it into your target folder first.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Couldn't rename this file", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't rename this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToRecents(uri: Uri, name: String) {
        val current = prefs.recentNotes.toMutableList()
        current.removeAll { it.uri == uri.toString() }
        current.add(0, RecentEntry(uri.toString(), name, false))
        val pinned = current.filter { it.pinned }
        val unpinned = current.filter { !it.pinned }.take(30)
        prefs.recentNotes = pinned + unpinned
    }

    private fun showLoadListPopup(anchor: View) {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_note_list, null)
        val fileList = view.findViewById<RecyclerView>(R.id.fileList)
        val emptyLabel = view.findViewById<TextView>(R.id.emptyLabel)
        lateinit var popupHandle: AnchoredPopupHandle

        fun refresh() {
            val entries = prefs.recentNotes.sortedWith(compareByDescending<RecentEntry> { it.pinned })
            if (entries.isEmpty()) {
                fileList.visibility = View.GONE
                emptyLabel.visibility = View.VISIBLE
                emptyLabel.text = "No recent notes yet — open or create one"
            } else {
                fileList.visibility = View.VISIBLE
                emptyLabel.visibility = View.GONE
                fileList.layoutManager = LinearLayoutManager(this)
                fileList.adapter = NoteListAdapter(
                    entries = entries,
                    onPick = { entry ->
                        popupHandle.dismiss()
                        openFileIntoEditor(Uri.parse(entry.uri))
                    },
                    onTogglePin = { entry ->
                        val current = prefs.recentNotes.toMutableList()
                        val idx = current.indexOfFirst { it.uri == entry.uri }
                        if (idx >= 0) current[idx] = current[idx].copy(pinned = !current[idx].pinned)
                        prefs.recentNotes = current
                        refresh()
                    },
                    onRemove = { entry ->
                        prefs.recentNotes = prefs.recentNotes.filterNot { it.uri == entry.uri }
                        refresh()
                    }
                )
            }
        }
        refresh()
        popupHandle = AnchoredPopup.showAbove(this, anchor, view)
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
        liveWatcher = LiveMarkdownWatcher(ink, accent, editor.textSize * 0.85f, editor.textSize * 0.8f)
        editor.addTextChangedListener(liveWatcher)
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

    // ---------------------------------------------------------------- text size (shared, persisted, pinch-to-zoom)

    private fun applyTextSize() {
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSizeSp)
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSizeSp)
    }

    private fun applyTypography() {
        // Slightly increased line-height and letter-spacing for easier reading
        // (helps with ADHD/dyslexia/autism-friendly layouts); identical in both
        // modes so nothing visually shifts when the keyboard opens or closes.
        for (tv in listOf<TextView>(editor, preview)) {
            tv.setLineSpacing(0f, 1.3f)
            tv.letterSpacing = 0.01f
        }
    }

    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentTextSizeSp = (currentTextSizeSp * detector.scaleFactor).coerceIn(12f, 28f)
                applyTextSize()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                prefs.textSizeSp = currentTextSizeSp
                liveWatcher.checkboxSizePx = editor.textSize * 0.85f
                liveWatcher.chipTextSizePx = editor.textSize * 0.8f
                liveWatcher.afterTextChanged(editor.text)
                if (!keyboardVisible) renderPreview()
            }
        })

        editor.setOnTouchListener(swipeAndPinchListener(editor, onEdgePullToggle = { exitEditModeToRead() }))
        previewScroll.setOnTouchListener(swipeAndPinchListener(
            previewScroll,
            onEdgePullToggle = { enterEditMode() },
            onSwipeRight = { showWordCount() }
        ))
    }

    private fun showWordCount() {
        val text = editor.text.toString()
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val chars = text.length
        Toast.makeText(this, "$words words · $chars characters", Toast.LENGTH_SHORT).show()
    }

    /**
     * One listener per view handling two independent gestures without stepping on normal
     * scrolling/editing: two-finger pinch (text size) and a browser-style pull — swipe down
     * while already scrolled to the very top toggles read/write. Both only ever *observe*
     * single-finger drags (always returns false for them) so ordinary scrolling is untouched;
     * only an active two-finger pinch is consumed.
     */
    private fun swipeAndPinchListener(
        view: View,
        onEdgePullToggle: () -> Unit,
        onSwipeRight: (() -> Unit)? = null
    ): View.OnTouchListener {
        var startX = 0f
        var startY = 0f
        var atTopOnDown = false
        var atBottomOnDown = false
        var triggered = false
        return View.OnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    atTopOnDown = !v.canScrollVertically(-1)
                    atBottomOnDown = !v.canScrollVertically(1)
                    triggered = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!triggered && !scaleDetector.isInProgress) {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        if (atTopOnDown && dy > 130f && abs(dy) > abs(dx)) {
                            triggered = true
                            onEdgePullToggle()
                        } else if (atBottomOnDown && dy < -130f && abs(dy) > abs(dx)) {
                            triggered = true
                            onEdgePullToggle()
                        } else if (onSwipeRight != null && dx > 130f && abs(dx) > abs(dy)) {
                            triggered = true
                            onSwipeRight()
                        }
                    }
                }
            }
            scaleDetector.isInProgress
        }
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
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't save — check the file is still accessible", Toast.LENGTH_SHORT).show()
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
        val name = try { DocumentFile.fromSingleUri(this, uri)?.name ?: "untitled.md" } catch (_: Exception) { "untitled.md" }
        addToRecents(uri, name)
        updateUnsavedBanner()
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
        addToRecents(newFile.uri, finalName)
        updateUnsavedBanner()
        editor.requestFocus()
        showKeyboardOn(editor)
    }

    // ---------------------------------------------------------------- read mode render

    private fun renderPreview() {
        val bodySize = editor.textSize
        val cornerRadiusPx = 10 * resources.displayMetrics.density
        preview.text = MarkdownRenderer.render(
            context = this,
            raw = editor.text.toString(),
            inkColor = ink,
            accentColor = accent,
            bodyTextSizePx = bodySize,
            chipTextSizePx = bodySize * 0.8f,
            checkboxSizePx = bodySize * 0.85f,
            cornerRadiusPx = cornerRadiusPx,
            codeBlockInsetPx = 8 * resources.displayMetrics.density,
            onToggleCheckbox = { rawLineStart -> toggleCheckboxAndSave(rawLineStart) },
            onCopyCodeBlock = { code -> copyToClipboard(code) }
        )
        preview.movementMethod = LinkMovementMethod.getInstance()
        updateWordCountPill()
    }

    private fun updateWordCountPill() {
        val text = editor.text.toString()
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        wordCountPill.text = if (words == 1) "1 word" else "$words words"
        wordCountPill.visibility = View.VISIBLE
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

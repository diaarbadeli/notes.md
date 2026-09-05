# notes — project spec

A single-purpose, extremely light Android app for editing one markdown file at a time,
purpose-built to live inside an Obsidian vault folder and handle quick daily notes
without opening Obsidian itself.

Package ID: `com.diaar.notes`
Min SDK: 28 · Target/Compile SDK: 34 · Language: Kotlin · UI: Android Views (no Compose)
Dependencies: AndroidX core-ktx, appcompat, recyclerview, material (for ripple/dialog only), documentfile.
No markdown library — rendering is hand-rolled (regex + custom Spans) to keep the APK tiny.

---

## 1. Screen & layout

One screen, one Activity (`MainActivity`). No settings screen, no menus.

```
┌─────────────────────────────┐
│  [toolbar: reorderable row] │  ← RecyclerView, 48dp tall
├─────────────────────────────┤
│                              │
│   EditText (edit mode)       │
│      or                      │
│   TextView (read/preview)    │  ← fills remaining space
│                              │
│                         [●]  │  ← small round FAB, bottom-right
└─────────────────────────────┘
```

- Background: `#111111`
- Text & icons: `#DFCBC9`
- Filled chips (checked checkbox, timestamps) & FAB: `#072331`
- Font: system default typeface and system default text size everywhere (no custom
  font, no fixed `sp` override — respects the user's OS font-scale setting).

## 2. Toolbar

A horizontal `RecyclerView` so buttons can be **drag-reordered**. Buttons, in default order:

| # | Button | Tap | Long-press | Swipe up |
|---|--------|-----|------------|----------|
| 1 | Undo | undo last text change | reorder | — |
| 2 | Bold | wraps/inserts `**`  | reorder | — |
| 3 | Italic | wraps/inserts `*` | reorder | — |
| 4 | Checkbox | inserts `- [ ] ` at line start | reorder | — |
| 5 | Date/time | inserts formatted timestamp at cursor | reorder | opens format picker |
| 6 | Load | opens "open note" dialog | reorder | — |
| 7 | New | creates a new note in the target folder | **sets/changes target folder** | — |

Reordering: long-pressing **any button except New** starts a drag (standard
`ItemTouchHelper` long-press-to-drag, horizontal only). New is excluded from dragging
entirely — its long-press is reserved for setting the vault folder. The chosen order is
persisted (`SharedPreferences`, CSV of button IDs) and restored on next launch.

Icons are small (20dp), thin single-stroke line icons in `#DFCBC9`, styled after the
Claude "asterisk" mark and Obsidian's own toolbar — geometric, no fill, rounded caps.

### Date/time button detail
- **Tap**: inserts a timestamp at the cursor using the currently-selected format.
- **Swipe up**: opens a small popup with three format choices. Selecting one sets it
  as the default for future taps (does not itself insert anything):
  1. `24h clock` → `14:32` *(default)*
  2. `Date + time` → `6 Sep 2026 14:32`
  3. `Short date + time` → `6 Sep 14:32`
- No slashes, dots, or `AM/PM` — always 24-hour, space-separated, day-first.
- Inserted timestamps are rendered in read mode as a filled chip: `#072331`
  rounded-rect background, `#DFCBC9` text, one step smaller than body text, still
  system font.

### Checkbox rendering (read mode)
- Unchecked: a small rounded-square outline, stroke only, `#072331`, no fill.
- Checked: same rounded square, filled `#072331`, with a `#DFCBC9` checkmark glyph
  drawn on top.
- Tapping a checkbox glyph in read mode toggles `- [ ]` ↔ `- [x]` directly in the
  underlying markdown text and autosaves — no separate edit step needed.

## 3. Read / write mode toggle

A small floating round button, bottom-right, `#072331` fill, roughly the same physical
size as Claude's own send button (≈36dp at 500 dpi ≈ the same visual footprint) — much
smaller than a standard Material FAB. Icon swaps between a pencil (edit mode) and an
eye (read/preview mode). No third "source" mode.

- **Edit mode**: plain `EditText`, raw markdown text, normal typing/caret.
- **Read mode**: `TextView` rendering of the same text — bold/italic applied, checkbox
  and timestamp chips rendered — built from lightweight regex + custom `Span`s, not a
  full markdown engine. Supported syntax is intentionally limited to exactly what this
  app produces: `**bold**`, `*italic*`, `- [ ]` / `- [x]` checkboxes, and the app's own
  timestamp formats. No headers, lists, links, images, or code blocks — keeping the
  renderer tiny and fast per your "as light as possible" instruction.

## 4. Storage: living inside the Obsidian vault

Uses the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) rather than
`MANAGE_EXTERNAL_STORAGE`, so no special permission screen/dance is needed — grant
access to your vault (or a subfolder of it, e.g. `daily/`) once, and the app holds a
persisted read/write URI permission on that tree forever after (survives reboots).

- **Setting the target folder**: long-press **New** at any time → system folder
  picker → grant → saved. This is also triggered automatically the first time you tap
  **New** if no folder has been set yet.
- **New note**: tap **New** → small filename prompt (defaults to `Untitled`, `.md` is
  appended automatically if omitted) → file is created directly inside the target
  folder and opened for editing immediately.
- **Load — both list and full browser, as requested**:
  - Tapping **Load** opens a dialog that lists every `.md` file already inside the
    target folder (tap one to open it instantly) —
  - — plus a **"Browse files…"** row pinned at the top of that same dialog, which opens
    the full system file browser (`ACTION_OPEN_DOCUMENT`) for picking any markdown file
    anywhere on the device, not just the target folder.
- **Autosave**: a `TextWatcher` debounced ~400ms after the last keystroke writes the
  full buffer back to the open file's URI (`openOutputStream(uri, "wt")`, i.e. a full
  overwrite — no separate save button, ever).

## 5. Build & delivery — GitHub Actions, not local Play/Gradle tooling

Per your request, there's no local Android SDK build step. `.github/workflows/build.yml`
does it in the cloud on every push:

1. Checks out the repo.
2. Installs JDK 17 + the required Android SDK platform/build-tools.
3. Runs `gradle assembleDebug` (using `gradle/actions/setup-gradle`, no committed
   Gradle wrapper binary needed).
4. Uploads the resulting `.apk` as a workflow artifact you download from the Actions
   tab in your phone's browser.

This is a **debug-signed APK** (Android's default debug keystore, auto-generated at
build time) — fine for personal sideloading, not for Play distribution (which you've
said you don't want anyway).

## 6. File map

```
notes/
├── PROJECT_SPEC.md                 ← this file
├── settings.gradle.kts
├── build.gradle.kts                ← root
├── gradle.properties
├── .github/workflows/build.yml     ← cloud build → APK artifact
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/diaar/notes/
        │   ├── MainActivity.kt      ← screen, mode toggle, file I/O, autosave
        │   ├── Prefs.kt             ← SharedPreferences wrapper
        │   ├── ToolbarAdapter.kt    ← reorderable toolbar RecyclerView + drag logic
        │   ├── MarkdownRenderer.kt  ← raw text → Spannable (bold/italic/checkbox/chips)
        │   ├── CheckboxSpan.kt      ← custom drawn checkbox glyph + tap target
        │   └── ChipSpan.kt          ← rounded-rect background span for timestamps
        └── res/
            ├── layout/activity_main.xml, toolbar_item.xml, dialog_open_note.xml
            ├── drawable/ ic_undo, ic_bold, ic_italic, ic_checkbox, ic_datetime,
            │            ic_load, ic_new, ic_eye, ic_pencil, bg_fab, bg_chip_outline
            ├── values/colors.xml, themes.xml, strings.xml
            └── mipmap-anydpi-v26/ic_launcher.xml
```

## 7. Decisions & trade-offs (so nothing gets relitigated later)

- **SAF over `MANAGE_EXTERNAL_STORAGE`**: you don't want Play distribution, so either
  would work permission-wise, but SAF needs zero special-permission screens and Android
  treats the persisted tree grant as permanent — simpler and just as seamless in
  practice for a single target folder.
- **No markdown library**: keeps APK size and startup time minimal; the renderer only
  needs to understand the four things this app itself produces.
- **Timestamp chip detection is pattern-based, not tag-based**: the renderer chip-styles
  any text matching the three known timestamp shapes, so a manually-typed `14:32`
  elsewhere in a note would also get chip-styled. Flagged here rather than hidden —
  acceptable given the app's daily-note use case, but worth knowing.
- **Debug-signed APK**: no release keystore was set up (adds setup steps for no benefit
  in a personal-use, non-Play app). Fine to add later if you ever want it.
- **One filename prompt, no folder browser for "New"**: New always creates inside the
  single saved target folder (that's the point — no per-note folder picking). Only
  long-press changes that target.

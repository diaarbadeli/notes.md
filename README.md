# notes — build & install

Read `PROJECT_SPEC.md` first for what this app actually does. This file is just the
"get an APK onto my phone" steps, entirely from a phone browser + Termux/any shell —
no Android Studio required.

## 1. Push this project to GitHub

```
cd notes
git init
git add -A
git commit -m "initial"
git branch -M main
git remote add origin https://github.com/<you>/notes-app.git
git push -u origin main
```
(Create the empty `notes-app` repo on GitHub first — private is fine.)

## 2. Let GitHub build the APK

Pushing to `main` triggers `.github/workflows/build.yml` automatically. Open your
repo on GitHub (works fine from a phone browser) → **Actions** tab → the "Build APK"
run → wait ~2–3 minutes → download the **notes-app-debug** artifact at the bottom of
the run page → unzip it to get `app-debug.apk`.

You can also trigger a rebuild any time without a code change via the **Run workflow**
button (the workflow has `workflow_dispatch` enabled).

## 3. Install it

Transfer the `.apk` to your phone if it isn't already there, open it, and allow
"install unknown apps" for whichever app you opened it with if prompted. This is a
debug-signed build (fine for personal sideloading).

## 4. First run

- The app shows a welcome note on first launch — read it, it covers the toolbar and
  gestures below in more detail.
- Swipe up on **New** (or long-press it before a folder is set) → pick your Obsidian
  vault folder (or a subfolder like `daily/`) → grant access. This only needs doing
  once; the permission persists.
- Tap **New** from then on to create a note directly in that folder. Long-press New to
  rename whatever note is currently open.
- Tap **Load** for a curated recents list (pin notes to keep them there permanently, ✕
  to remove ones you don't want cluttering it). Long-press **Load** to open the system
  file browser for anything not in that list.

## What is this app? (the in-app welcome note)

On first launch the app shows this note in an unsaved buffer — the same text is also
auto-saved as `welcome.md` in your chosen folder the first time you pick one, so it's
never lost even if you move on to another note without saving it yourself:

> **Welcome**
>
> This is a simple app built to replace the constant need to open Obsidian for every
> quick task. Obsidian is too powerful to fully replace, but since it can take a few
> seconds to load, this app is here for your daily needs — grocery lists, to-dos, quick
> notes, and the ideas that slip away if you don't catch them in time.
>
> Your main control panel is the **toolbar** above the keyboard. Hold any icon to
> rearrange it — except the four with their own hold action: **Undo** (hold to redo),
> **New** (hold to rename the current note, swipe up to change its target folder),
> **Load** (tap for your recents list, hold to open the system file browser), and
> **Date** (hold to change the timestamp format).
>
> There are also a few **gestures**:
> - Pull down or pull up to switch between read and write mode. No keyboard means read mode.
> - Pinch to resize the text.
> - Swipe left-to-right in read mode for a quick word and character count; a small
>   word-count pill also stays visible whenever you're reading.
>
> You can **customize colors** right in this file — just edit the four lines below and
> save. You can delete this file afterward. To change colors again later, create a new
> note named **change4colors** with the same four lines; you can delete that one too
> once it's taken effect.
>
> ```
> background #111111
> copyblocks #1e1e1c
> accent #072331
> text #dfcbc9
> ```
>
> I hope you have a nice ride.
> #Diaar

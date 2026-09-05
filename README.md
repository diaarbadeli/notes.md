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

- Long-press **New** in the toolbar → pick your Obsidian vault folder (or a subfolder
  like `daily/`) → grant access. This only needs doing once; the permission persists.
- Tap **New** from then on to create notes directly in that folder.
- Tap **Load** to see a list of `.md` files already there, or use **Browse files…**
  inside that same dialog to open any markdown file anywhere on the device.

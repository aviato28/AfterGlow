# Afterglow — rules for every session

Afterglow is a free, public, ad-free home screen (launcher) for Google TV / Android TV. Goal: fast and light on old,
low-memory TVs. Read `docs/ROADMAP.md` first; build sessions implement exactly one spec in `docs/specs/`.

## How we work
- The Opus master chat plans, keeps the roadmap, writes specs, reviews, merges and releases. It never builds itself: it delegates
  each build to a subagent (Sonnet; Haiku for tiny edits) that implements one spec, doesn't redesign, and lists anything it
  couldn't do in the spec's "Not done" section.
- "Discussion only" means: edit docs, commit with `[skip ci]`, push, don't build.
- No pull requests unless asked. Develop on the branch the session names.
- The owner tests on a TCL 65Q6C (Google TV) and has no PC; anything needing a computer must have an in-app path.

## Stack
- Kotlin, Jetpack Compose, single `:app` module (keep it small; split only if a spec says so). minSdk 30 (Android 11+), target latest.
- No Hilt, no Room, no Retrofit: plain constructors, DataStore, `HttpURLConnection`/OkHttp only if needed. Every
  dependency must earn its place in APK size and startup time.
- R8 + resource shrinking on, baseline profile for the home screen.
- Package `com.nbansal.afterglow`.

## TV rules (learned the hard way on Penumbra+)
- Layout on a fixed 1920x1080 canvas: `Density(widthPx / 1920f, fontScale = 1f)` at the root. Every Dialog/popup goes
  through one helper that re-applies that density (a plain `Dialog` ignores it).
- One focus system (`Modifier.tvFocus`): ring always drawn on the focused item, small lift, focus remembered when you
  come back, never "nothing selected" (use `focusSoon()` retry when a screen opens). Ring is never clipped: rows leave room.
- Many TCL remotes never send Menu to apps: long-press OK opens options everywhere; Menu is a bonus.
- Look: follow `docs/design/` (home.svg, logo.svg) and the Design section of the roadmap.
- Text at least 20 sp; 96 dp / 54 dp safe margins.
- Images: small memory cache (~10%), decode at the size shown, clear on `onTrimMemory`.
- Crash safety: a launcher that crashes can strand the user. Catch uncaught errors, log them, restart Home once
  (not in a loop), and show the last problem in Settings → About.

## Never
- Never ads, analytics, trackers or network calls other than GitHub Releases (updates).
- Never install, update or sideload other apps (Netflix, YouTube…). Point to the Play Store only.
- Never disable Play Store, Play Services, Settings, TV input framework, Widevine/DRM or anything on the
  "protected" list in `takeover/` (see `docs/specs/M2-takeover-in-app.md`). Every disable must be undoable from the app, and nothing may be disabled without first proving the TV stays reachable after a restart (`docs/TAKEOVER-TCL.md`).
- Never commit a keystore or secret. Release signing: GitHub secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_PASSWORD`.
  Never replace the key once created (installed copies could no longer update).
- No code or names from Penumbra/Vanta streaming features; only the generic TV patterns above.

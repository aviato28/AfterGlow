# M0 — Foundation

Goal: an installable, signed Afterglow APK that shows the empty home shell (glow, logo, clock, greeting) and a CI
pipeline that publishes releases to GitHub. No app list yet (that's M1).

Read `CLAUDE.md` and `docs/ROADMAP.md` (Design section) first. Look: `docs/design/home.png`, `docs/design/logo.svg`.

## 1. Project
- Gradle wrapper is already committed (Gradle 8.14.3). Add `settings.gradle.kts`, root `build.gradle.kts`,
  `gradle/libs.versions.toml`, `gradle.properties` (`android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx4g`,
  `kotlin.code.style=official`), `.gitignore` (build/, .gradle/, local.properties, *.jks, *.keystore, .idea/).
- Versions known to work together: AGP 8.11.1, Kotlin 2.2.0 (+ compose compiler plugin), Compose BOM 2025.07.00,
  activity-compose 1.10.1, lifecycle 2.9.2, datastore-preferences 1.1.7, coroutines 1.10.2, junit 4.13.2,
  robolectric 4.14.1. Newer stable is fine if it builds.
- One module `:app`, package/namespace `com.nbansal.afterglow`, minSdk 30, compile/target SDK 36, Java/JVM 17.
- Dependencies only: compose ui/foundation/material3 (for text + basic shapes only), activity-compose,
  lifecycle-runtime-compose, datastore-preferences, coroutines-android, profileinstaller. Test: junit, robolectric,
  compose ui-test-junit4. No androidx.tv, no Hilt, no Coil yet.
- versionName from Gradle property `afterglowVersion` (default `0.0.1-dev`); versionCode from property
  `afterglowCode` (default 1). CI passes both.
- Release build: `isMinifyEnabled = true`, `isShrinkResources = true`, signed when env `SIGNING_KEYSTORE_PATH` +
  `SIGNING_PASSWORD` are set (alias `afterglow`), otherwise unsigned/debug-signed so local builds work.
- Font: bundle Inter variable (`res/font/inter.ttf`, from github.com/rsms/inter releases, OFL — add
  `app/src/main/assets/licenses/inter-OFL.txt`). If downloading isn't possible, use `FontFamily.SansSerif` and note it.

## 2. Manifest
- `HomeActivity` (exported, `singleTask`, landscape, `configChanges` for orientation|screenSize|screenLayout|
  smallestScreenSize|keyboardHidden|density|uiMode|navigation|keyboard), intent filters:
  MAIN + LEANBACK_LAUNCHER; MAIN + HOME + DEFAULT. `android:banner` and `android:icon` from the logo.
- `uses-feature` leanback required=false, touchscreen required=false.
- Theme: no action bar, window background `#0B0908` (no white flash on start).

## 3. Logo assets
- Convert `docs/design/logo.svg` to vector drawables (gradients are fine in VectorDrawable on API 24+):
  `ic_launcher` (adaptive: background `#0B0908`, foreground = sun + horizon lines), and `banner` 320x180 dp:
  logo mark left + "afterglow" wordmark (text drawn as path or a PNG rendered from `docs/design/logo.png` + text;
  simplest that looks crisp).

## 4. Code (all in `app/src/main/kotlin/com/nbansal/afterglow/`)
- `ui/Canvas.kt` — `TvCanvas { }`: root that sets `LocalDensity` to `Density(widthPx / 1920f, fontScale = 1f)` so
  every TV lays out on 1920x1080 dp. `TvDialog(onDismiss) { }`: the only allowed popup; re-applies the same density
  inside the dialog window.
- `ui/Theme.kt` — `object Ag` with colors: Base `#0B0908`, Ink `#F3E9DF`, Muted `#B8ADA3`, Amber `#FFB070`,
  Rose `#E8607A`, Violet `#5B3F8C`, Ring `#FFD9B0`, PillFill Ink@8%, PillLine Ink@14%. Text styles (sp): Greeting 84
  Light, Clock 44 Light, Date 26, Label 24, SectionCaps 24 SemiBold letterSpacing 2, Body 24. Margins 96 x 54 dp.
- `ui/Focus.kt` — `Modifier.tvFocus(shape, onClick, onLongClick = null)`: when focused, scale 1.06 (animate ≤150 ms),
  4 dp `Ring` border and an amber blurred glow behind (draw a blurred rounded rect in `drawBehind`, or a pre-made
  9-patch; must not cost per-frame work when not focused). Ring/glow drawn OUTSIDE the clip. OK/Enter = click on key
  up; OK held (repeatCount ≥ 1) = `onLongClick` once, and then key up does not click. Menu key = `onLongClick`.
  `suspend fun FocusRequester.focusSoon()`: retry `requestFocus()` 20× every 50 ms.
- `ui/Glow.kt` — `AfterglowBackground(phase)`: Base fill + one radial gradient centred below the bottom edge
  (as home.svg). `phase` from hour: Morning 5–11 (peach, softer), Day 11–17 (faint), Evening 17–21 (full, as
  mockup), Night 21–5 (dim ember, mostly amber at low alpha). Re-evaluated every 15 min via a ticking flow;
  no continuous animation.
- `home/HomeScreen.kt` — logo mark + "afterglow" top-left, clock top-right (system 12/24 h setting, updates on the
  minute — use `ACTION_TIME_TICK` receiver or a coroutine aligned to the minute), greeting ("Good morning" 5–12,
  "Good afternoon" 12–17, "Good evening" 17–21, "Good night" 21–5) + date ("Saturday, 18 October", device locale).
  A placeholder "All apps" pill and settings pill bottom-right, both focusable with `tvFocus`, settings opens
  `android.settings.SETTINGS` (fallback `Settings.ACTION_SETTINGS`). Initial focus on "All apps".
- `HomeActivity.kt` — `TvCanvas { AfterglowBackground; HomeScreen }`. Back on home does nothing (a launcher must
  not exit). `onTrimMemory` hook (empty for now, comment for M1 image cache).
- `core/CrashGuard.kt` — installed from `AfterglowApp : Application`: uncaught exception → write
  `filesDir/last-problem.txt` (time, thread, stack ≤ 6000 chars), then restart `HomeActivity` unless the previous
  crash was < 15 s ago (then hand to the default handler, no loop). `lastProblem()` / `clearProblem()` for later.

## 5. CI (`.github/workflows/`)
- `build.yml` — on push to any branch (ignore commits with `[skip ci]`, which GitHub does natively) and PRs:
  JDK 17, `./gradlew testDebugUnitTest assembleRelease` (unsigned ok). Upload the APK as a workflow artifact
  (retention 7 days). Gradle cache via `gradle/actions/setup-gradle`.
- `release.yml` — `workflow_dispatch` with input `version` (e.g. `0.1.0`) so the owner can release from the GitHub
  phone app: refuse if secrets missing; decode keystore; versionCode = `github.run_number + 100`; build signed
  release; rename to `afterglow-<version>.apk`; create tag `v<version>` and a GitHub Release (body = auto-generated
  notes, or `docs/CHANGELOG.md` section if present) with the APK attached (use `softprops/action-gh-release@v2`,
  `permissions: contents: write`). Also attach `afterglow-<version>.apk.sha256`.
- `create-signing-key.yml` — one-time, `workflow_dispatch`:
  - Refuse if `SIGNING_KEYSTORE_BASE64` already exists.
  - Needs secrets the owner adds by hand first: `AFTERGLOW_BOT_TOKEN` (fine-grained PAT, this repo, Secrets: write)
    and `KEY_BACKUP_PASSPHRASE` (any long phrase the owner keeps).
  - `keytool -genkeypair` PKCS12, alias `afterglow`, RSA 4096, validity 10000, random 32-char password (masked).
  - `gh secret set SIGNING_KEYSTORE_BASE64` and `SIGNING_PASSWORD` with the bot token.
  - Backup: THIS REPO IS PUBLIC, so the artifact must be encrypted: `gpg --symmetric --batch --pinentry-mode loopback
    --passphrase "$KEY_BACKUP_PASSPHRASE"` over a tar of the keystore + README (password, alias). Upload only the
    `.gpg` file, retention 1 day. Never echo the password.
- `docs/RELEASING.md` — 10 lines for the owner: add the two secrets (where in GitHub settings), run "One-time: create
  signing key", download + keep the backup, then "Release" with a version.

## 6. Tests (Robolectric, fast, no emulator)
- Greeting + glow phase for boundary hours (4:59, 5:00, 11:59, 12:00, 16:59, 17:00, 20:59, 21:00).
- HomeScreen renders at 1920x1080 qualifier (`@Config(qualifiers = "w1920dp-h1080dp-xhdpi")` and a 960x540 density-2
  case) and "All apps" has focus after first frame.
- `tvFocus`: OK short press clicks once; OK held fires long-click once and no click.

## Acceptance
- `./gradlew testDebugUnitTest assembleRelease` passes locally if the Android SDK is available; otherwise push and
  make sure the Build workflow is green (fix until green).
- Release APK < 3 MB.
- Do NOT run the release or signing-key workflows; the owner does that.
- Update `docs/ROADMAP.md`: mark M0 done, and add a "Not done" line for anything skipped.
- Commit trailers per session instructions; push to `claude/afterglow-tv-launcher-g6ow3l`.

## Not done
(build session fills this in)

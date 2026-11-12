# M11 — Phone companion (ROADMAP task 11)

Read `CLAUDE.md` (network rule: a local-network server is allowed only while the companion is switched on), ROADMAP task 11, mockups
`docs/design/v2/companion-tv.png` and `companion-phone.png`. Read only `keys/` (service pattern), `takeover/AdbShell.kt` (+ whitelist), `data/Prefs.kt`,
`core/AppModel.kt`, the settings navigation/pages you touch, `HomeActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, tests.

## Server (`companion/`)
- Plain `ServerSocket` HTTP/1.1 server (no web framework), port 8765, in a foreground service `CompanionService` (low-importance silent notification), started
  at boot and from HomeActivity while the switch is on; `START_STICKY`. One small thread pool (2 threads). Requests > 64 KB rejected.
- Accept only clients from private LAN ranges (10/8, 172.16/12, 192.168/16, link-local); everything else gets 403.
- Pairing: TV shows a QR of `http://<tv-ip>:8765/?pin=NNNN` and the 4-digit PIN (new random PIN each time the pairing screen opens, valid 10 min). `POST /api/pair {pin}`
  returns a random 32-byte token (hex); tokens stored in DataStore (max 5 phones). Every `/api/*` call except pair needs `Authorization: Bearer <token>`.
  Settings: "Pair a phone" (shows the QR screen), "Unpair all phones", switch "Phone companion" (off by default; stays on once switched on, no auto-off).
- QR: dependency `io.nayuki:qrcodegen:1.8.0` (tiny, MIT; add its licence to `assets/licenses/`); draw modules on a Canvas.
- Endpoints (JSON): `POST /api/text {text}` -> types into the focused field on the TV via the existing loopback adb: ASCII only, escape for `input text`
  (space -> `%s`, shell-quote the rest), send in chunks of 8 characters with 150 ms between chunks (the TV keyboard drops fast input; owner verified this);
  non-ASCII characters are skipped and reported back. `POST /api/key {key}` with key in {up,down,left,right,ok,back,home,del,enter,panel} -> `input keyevent`
  (panel = open the Afterglow quick panel directly). `GET/POST /api/favourites` (order), `POST /api/tmdbkey {key}` (trim, validate like Look page),
  `GET /api/backup` (JSON of all Afterglow prefs; TMDB key only when `?withKey=1`), `POST /api/restore` (validate, then apply; never restores takeover state
  or disabled packages: those stay TV-only actions).
- Add the needed `input keyevent <n>` / `input text <escaped>` forms to `AdbWhitelist` with strict patterns + tests (no other shell access).

## Web page (`assets/companion/index.html`, one file + `manifest.json` + icon)
- Matches `companion-phone.png`: tabs Type / Remote / Home / Backup, Outfit-like system font fallback, dark glass, cream active tab. Type tab: textarea + Backspace,
  Space, Clear, Send; D-pad + OK, Back, Home, Panel. Home tab: drag-to-reorder favourites (touch drag), TMDB key paste. Backup tab: Save (downloads JSON),
  Restore (file picker). Token kept in `localStorage`, so after the first scan the page just works; `manifest.json` with `display: standalone` so "Add to Home
  screen" gives an app icon. No external URLs, no CDN, no analytics.

## Tests
Pairing (bad PIN, expired PIN, token check, 5-phone cap), LAN filter, text escaping + chunking, key mapping, backup/restore round trip (no takeover state).

## Also
Update `CLAUDE.md`'s network rule line only if not already updated. Acceptance: no SDK here, be compile-careful; push once (pull --no-edit if rejected);
no PR, no release; one ROADMAP edit (task 11 -> built, awaiting test).

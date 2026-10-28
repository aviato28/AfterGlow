# M6 — Lite home revamp (Afterglow 2, part b)

Read `CLAUDE.md`, `docs/ROADMAP.md` (task list item "Afterglow 2"). Visual reference: `docs/design/v2/home-lite.png` and `docs/design/v2/lite.html` + `base.css`
(1 CSS px = 1 dp on the 1920 canvas). Read only `home/`, `ui/`, onboarding/settings files you touch, `HomeActivity.kt`, tests. No new dependencies.

- **Background:** base `#0B0908`, softer glow from the bottom (as `lite.html`: ellipse 70% x 55% at 50% 112%, amber .50 -> rose .28 -> violet .14 -> 0) plus a faint violet
  glow top-right. Keep the time-of-day phases (Morning/Day/Evening/Night) as strength/tint changes of this glow.
- **Top bar:** logo + "afterglow" left. Right: one glass pill with the clock (30 sp light) and two round icon buttons **All apps** (grid icon) and **Settings** (gear),
  both `tvFocus`. These replace the old bottom-right text pills.
- **Greeting:** 76 sp light, "Good evening, <name>" when a name is set, else "Good evening"; date 28 sp muted below. Name: optional field in onboarding and in
  Afterglow settings (DataStore), max 20 chars.
- **Favourites shelf:** frosted glass panel (translucent gradient fill, 1 dp light border, inset highlight, big soft shadow; NO real blur), radius 40, 72 dp side
  margins, caption "FAVOURITES", 6 banner cards 16:9 radius 24 with the apps' own banners; focused card lifts 8% with the cream ring + amber glow (existing
  `tvFocus`). Focused app's label below the shelf.
- **All apps peek:** below the shelf, the first row of All apps (app banners/icons, radius 22) fading out with a vertical mask. Down from Favourites moves into it;
  it's the real All apps grid scrolled into view (or opens All apps; pick the simpler that keeps focus rules).
- Keep everything else (long-press options, recents, focus memory, performance: no per-frame work when idle).
- Tests: greeting with/without name; HomeScreen renders at 1920x1080 and 960x540; initial focus on the first favourite.

## Acceptance
`./gradlew testDebugUnitTest` once at the end (say if no SDK). Push once to `claude/afterglow-tv-launcher-g6ow3l`. No PR, no release. One ROADMAP edit (part b done,
awaiting TV test).

## Not done
- Not compiled or tested: no Android SDK in the build container (`./gradlew testDebugUnitTest` not run; CI builds on push).
- All apps peek is the first 9 visible apps as real focusable tiles (Down from the shelf lands in it; OK opens the app, long-press OK gives options). It does not scroll the All apps grid.
- Favourites shelf shows 6 cards per screen and scrolls sideways when there are more; the focused app's name appears under the shelf.
- Name entry reuses the Rename text dialog (settings row "Your name", and a row on the onboarding Welcome step); no new onboarding step.
- The background glow now uses the lite.html ellipses; phases scale it relative to Evening (Evening = the mockup). The clock screensaver is unchanged.

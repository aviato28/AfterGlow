# M12 — Feel and polish pass (ROADMAP task 12)

Read `CLAUDE.md`, ROADMAP task 12, mockup `docs/design/v2/settings-v2.png` (+ existing v2 mockups). Read only `ui/`, `settings/`, `home/` screens you change,
`panel/QuickPanel.kt`, `HomeActivity.kt`, tests. No new dependencies. Don't change behaviour, only look and motion.

1. **Settings v2:** one Settings screen with a left rail of icon sections (Home & look, Remote & panel, Phone companion (if present), Screensaver, Speed up my TV,
   About) and a glass content panel of rows (as the mockup): titles 40 sp light, subtitle muted, rows radius 24, value text on the right, switches drawn as
   the mockup's pill. Existing pages move into these sections unchanged in function. Back from content returns to the rail.
2. **One accent everywhere:** expose the current accent (Showcase artwork accent when Showcase is on, else the Lite time-of-day glow colour) as a
   CompositionLocal; focus ring/glow, switches, active rail item, quick panel focused tile and the screensaver settings use it. Default cream/amber unchanged.
3. **Motion:** focus lift uses a spring (`spring(dampingRatio = 0.8f, stiffness = 400f)`) instead of a tween; screen changes (Home <-> All apps <-> Settings,
   Settings sections) use a 180 ms slide+fade (`AnimatedContent`); the quick panel slides in from the right (200 ms). Nothing animates while idle.
4. **Icons:** one consistent outline icon set (24 dp viewBox paths, 2 dp stroke) for the rail, pills and panel tiles.

Tests: existing screen tests still pass; add one Robolectric test that Settings opens with the rail focused on the first section.
Acceptance: no SDK here, be compile-careful; push once (pull --no-edit if rejected); no PR, no release; one ROADMAP edit (task 12 -> built, awaiting test).

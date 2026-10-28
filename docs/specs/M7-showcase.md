# M7 — Showcase mode: TMDB artwork home (Afterglow 2, part c)

Read `CLAUDE.md` (network rule: TMDB only while Showcase is on), `docs/ROADMAP.md` (Afterglow 2 item), `docs/specs/M6-lite-home-v2.md`. Visual reference:
`docs/design/v2/home-showcase.png` and `showcase.html`. Read only `home/`, `ui/`, settings files you touch, `HomeActivity.kt`, tests. No new dependencies
(`HttpURLConnection`, `org.json`, `BitmapFactory`).

## Settings
- Afterglow settings > **Look**: Lite (default) / Showcase. Showcase is offered only if total RAM >= 2.8 GB (`ActivityManager.MemoryInfo.totalMem`); otherwise the row
  explains why. Showcase needs a **TMDB key**: text field (v3 API key, 32 hex chars, recommended; a v4 read token starting with `eyJ` also works -> Bearer header).
  Stored in DataStore only, never logged, never in the repo. "Test key" button shows OK / the error.
- **Open titles with:** list of installed apps that resolve `ACTION_VIEW https://www.themoviedb.org/movie/1` (add a `<queries>` intent for it); pick one, saved.
  None -> "No app on this TV opens TMDB links" (then Watch shows that message).

## Data (`showcase/Tmdb.kt`, unit-tested with fixture JSON)
- `GET https://api.themoviedb.org/3/trending/all/week?language=en-US` (10 s timeouts). Keep movie/tv items with a `backdrop_path`, first 10. Genres from
  `/3/genre/movie/list` + `/3/genre/tv/list` (cached 30 days). Fields: id, media type, title/name, year, first genre, `vote_average` (one decimal), overview.
- Refresh at most every 6 h; cache JSON in `filesDir`; offline -> last cache; no cache/no key/error -> fall back to the Lite home silently (error shown in Settings).
- Images: `https://image.tmdb.org/t/p/w1280<backdrop_path>`, disk cache in `cacheDir` (max 10 files), decode at screen size with `RGB_565`, keep only current + next
  bitmap in memory, drop both on `onTrimMemory`.

## Home (Showcase)
- Full-bleed backdrop, left and bottom dark gradients (as `showcase.html`), the glow tinted toward the art (average color of the bottom third, cheap sample).
- Left: title (96 sp bold, letter-spaced), meta row "year · genre · ★ 7.8", overview (2 lines max, muted), buttons **Watch** (focused first; sends
  `ACTION_VIEW https://www.themoviedb.org/{movie|tv}/<id>` to the chosen app with `setPackage`) and **Next** (next title; the mockup's "More like this" is out of scope).
  Dots for the 10 titles; credit "Trending this week · TMDB" bottom right of the hero.
- Rotation every 15 s with a 400 ms crossfade, only while Home is resumed and focus is in the hero; never while in the shelf. (Exception to "nothing moves on its own",
  approved by the owner.)
- Same top bar and glass Favourites shelf as Lite (M6), placed at the bottom (as the mockup).
- About page: "This product uses the TMDB API but is not endorsed or certified by TMDB."

## Acceptance
`./gradlew testDebugUnitTest` once at the end (say if no SDK). Push once to `claude/afterglow-tv-launcher-g6ow3l`. No PR, no release. One ROADMAP edit (part c done,
awaiting TV test).

## Not done
- Not compiled or tested: no Android SDK in the build container (`./gradlew testDebugUnitTest` not run; CI builds on push).
- Unit tests cover parsing, URL/key handling, formatting, RAM gate, cache freshness, sample size and average colour (`TmdbTest`). There is no Compose test for the Showcase hero, and no test of the network/image code.
- Showcase is chosen in Settings > Look (new page, plus an "Open titles with" picker page). The key is typed in the shared text box (up to 600 characters) and shown only as "Set / Not set".
- "Test key" calls `/3/configuration`; v3 keys go in the query string, v4 tokens (`eyJ...`) in a Bearer header. Errors are reduced to short texts so the URL/key is never stored or shown.
- Watch with no app chosen shows "Choose an app in Settings > Look > Open titles with" under the buttons; with no TMDB-capable app it shows "No app on this TV opens TMDB links".
- The hero sits over the normal background; if the cache/network/key fails, or the first image is still loading, the screen shows the Lite background (the Lite home is used whenever there are no titles). While Showcase is on the Lite greeting and All apps peek are not shown.
- Glow tint is the average of a 12 x 4 sample of the bottom third. The title font has no Bold weight, so Bold maps to the nearest (SemiBold). Titles over 14 characters use a smaller size to stay on one line.
- Shelf cards are 240 dp wide in Showcase (shelf 260 dp tall at y 760) to fit the mockup.

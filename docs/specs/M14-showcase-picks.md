# M14 — Showcase fixes + better picks (ROADMAP task 14)

Read `CLAUDE.md`, ROADMAP task 14, `docs/specs/M7-showcase.md`, `docs/specs/M9-showcase-v2.md`. Read only `showcase/`, `home/ShowcaseHome.kt`,
`settings/LookPage.kt`, tests. No new dependencies. Network only to api.themoviedb.org / image.tmdb.org, only while Showcase is on.

Implement task 14 a-e exactly. Notes:
- a. After decoding a logo, sample its outer 4% border: if more than 60% of those pixels have alpha > 200, reject it and try the next candidate logo (keep the
  candidate list from the details call); none left -> styled text title. Unit-test with synthetic bitmaps.
- b. Rotation timer independent of focus (still only while Home is resumed). Preload next backdrop + logo when a title is shown. 700 ms crossfade.
- c/d. Pool builder: fetch `/3/watch/providers/movie?watch_region=IN` once a week (cache), map installed apps to provider ids by name (Netflix ->
  "Netflix"; com.amazon.amazonvideo.livingroom -> "Amazon Prime Video"; in.startv.hotstar -> name containing "Hotstar"; com.sonyliv -> "Sony Liv";
  com.graymatrix.did -> "Zee5"; match case-insensitively, ignore missing). With providers: `/3/discover/movie` and `/3/discover/tv` with
  `watch_region=IN&with_watch_providers=<ids joined by |>&sort_by=popularity.desc` (2 pages each) + trending day + trending week; without providers:
  trending day/week + popular + now_playing (region IN) + top_rated. Dedupe, drop titles without backdrop, shuffle with a daily seed, keep 50, skip titles
  shown in the last 3 days (store id -> lastShown in DataStore, prune > 7 days), and "Not interested" ids forever. Badge "On <service>" when the title came
  from a provider query (store which). Keep the 6 h refresh; details/logos fetched lazily for the next 3 titles only (not all 50).
- e. Look page: "More of" / "Less of" genre chips (multi-select, from the genre lists) and languages (`with_original_language`, multi-select: en, hi, ko, ja,
  es, ta, te, ml), used as discover params and as a post-filter on trending. Long-press OK on the hero (tvFocus onLongClick) -> small menu "Not interested".
- Unit tests: logo rejection, pool dedupe/shuffle determinism with a seed, 3-day repeat rule, provider-name mapping, language/genre params.
Acceptance: no SDK here, be compile-careful; push once (pull --no-edit if rejected); no PR, no release; one ROADMAP edit (task 14 -> built, awaiting test).

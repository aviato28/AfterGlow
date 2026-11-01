# M9 — Showcase v2 + night mode (ROADMAP tasks 4, 5)

Read `CLAUDE.md`, ROADMAP tasks 4-5, `docs/specs/M7-showcase.md`. Visual reference: `docs/design/v2/showcase2-dune.png`, `showcase2-batman.png`,
`showcase2-kenburns.gif`, `showcase2-dune.html`, `showcase2-bat.html`. Read only `showcase/`, `home/ShowcaseHome.kt`, `ui/Glow.kt` (phases), tests.
No new dependencies (no Palette library: write the color pick yourself).

Implement ROADMAP task 4 a-d and task 5 exactly as written there. Notes:
- Details + logos: one `/3/{type}/{id}?append_to_response=images,release_dates,content_ratings&include_image_language=en,null` call per title (TMDB supports
  `append_to_response`), fetched for the 10 trending titles after the trending list, cached in the same 6 h JSON cache. Parse logo (`images.logos`, prefer
  iso_639_1 "en", then null; highest vote), certification (IN, else US; movies: first non-empty `certification` in `release_dates`; tv: `rating`), runtime
  (`runtime` or first `episode_run_time`), genres (names, max 2), release date for the "New on streaming" badge (<= 60 days old).
- Logo image `https://image.tmdb.org/t/p/w500<file_path>` in the same disk cache (count it in the 10-file cap -> raise cap to 24), decode ARGB_8888 at display size.
- Accent: downscale backdrop to 16x9, choose the pixel with the highest saturation among those with value 0.35-0.95; fallback `#FFB070`. Apply to focus
  ring/glow, score ring, dots, shelf tint, bottom glow (pass it down as a parameter or CompositionLocal; Lite stays unchanged).
- Ken Burns: `graphicsLayer { scaleX/scaleY; translationX/Y }` driven by one `Animatable` per title; no recomposition per frame beyond the layer.
- Night mode (glow phase Night, 21-05): backdrop alpha/dim ~40% darker, drift duration doubled.
- Unit tests: details parsing (fixture JSON), logo choice, certification fallback, "new" badge rule, accent pick on a tiny synthetic bitmap (Robolectric).

## Acceptance
No SDK here: be compile-careful. Push once to `claude/afterglow-tv-launcher-g6ow3l`, no PR, no release. One ROADMAP edit (tasks 4-5 -> built, awaiting test).

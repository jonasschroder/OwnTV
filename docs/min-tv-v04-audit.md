# v0.4 reuse audit (before presentation changes)

Baseline: `76f9280527f938211b4255a16b0ce7d819c162fb`, Core
`adca2bcd653f19e6e5d659c2722309e6aef4ec15`. Read `extras/USER_GUIDE.md` first.

| Requirement | Existing implementation | v0.4 approach |
|---|---|---|
| Movies/series layouts | Layout setting: Separate panels or Cinematic; `MoviesScreen`, `SeriesScreen`, `VodStage` | Improve shared presentation, keep both modes and stored choices |
| Large libraries | Room/Paging, stable entity keys, visible-poster enrichment, per-category scroll state | Retain queries, paging and grids; no whole-library preload |
| Search/categories/sort | Existing toolbar, category sheet, sort menu in both screens | Keep controls and keyboard/remote behavior |
| Details and episodes | Shared details/hero, Series episode view, season selection | Reuse provider/TMDB details and existing episode/playback flow |
| Resume | Profile-specific ProgressDao/history, HomeViewModel continuation lists; player heartbeat | Use real progress only, no duplicate database or preview player |
| Metadata | Core MetadataRepository/TmdbProvider, override store, Room match/cache tables | Preserve modes, manual Set TMDB name/Refetch and attribution |
| Matching | Separate movie/TV endpoints, known-ID paths, localized/original-title scoring, title/year normalization | Test existing limitations; no speculative replacement service |
| Metadata traffic | Focus debounce 700 ms, visible-poster work, season mutex, worker budgets, positive/negative caching | Preserve bounds and offline/provider fallback |
| Sports | Opt-in ShlRepository, shared persistent 12-request/2-hour budget, strict reader, cached schedule/table | Diagnose response-to-UI transitions; no additional polling |
| Crash evidence | Core CrashRecorder persists original stack; existing More → error-log export includes it | Reuse handler; add bounded safe sports context and sanitized export |
| Navigation | StageRail layouts/shortcuts; Home explicit section targets; stable competition-qualified fixture IDs | Keep rail behavior, remove competing focus restoration on dynamic favorites |
| Updates/data | Protected permanent signing, independent package channels, Core DB/profile storage | No signing/update infrastructure changes, reset or clean installation |

The large Cinematic top region is explicitly allocated in both screens: `122.mpx +
heroH`, where heroH is a saved percentage or 464.mpx. Even when no title is selected,
this space is reserved. This is a source-verified layout problem, not a performance
measurement. Respect the user's existing details-height setting and Separate mode.

Sports fixture IDs already include competition ID; duplicated cross-league IDs are
not established as the crash cause. Reader/cache work already runs on IO. A successful
HTTP 200 does not prove successful parsing or rendering, but neither is parser failure
established by the owner's report. No physical crash stack is available yet.

Potential navigation hazards found: automatic lazy favorites focus restoration runs
alongside explicit selected-channel restoration; channel search completion unconditionally
focuses its first result; several Matchcenter transient states survive profile changes.
Regression tests must cover these transitions before making claims about the physical crash.

Metadata limitations observed: the pinned normalizer removes all bracket contents and
standalone years; the matcher chooses the highest accepted score without an ambiguity
margin. Avoid claiming these are fixed by changing artwork presentation. Provider metadata
and manual corrections remain available without an API key.

Physical checks required: the reported arrival crash with original stack/export, IPTV
preview/promotion, decoder/audio/subtitles, actual large provider libraries, remote focus,
and update-in-place preserving profiles/favorites/history. Emulator fixtures cannot verify
provider access, Chromecast RAM/decoder performance or permanent owner-key updates.

## Implementation following this audit

The app decorates the existing metadata provider with conservative search filtering
(0.92 minimum title score, 0.12 ambiguity margin, exact supplied year and media type).
The destructive-normalization guard is scoped to new searches only: positive stored
matches, known-ID details and cache-only artwork/episode reads remain available.
Provider data, manual corrections and historical positive caches are preserved.
No Core revision, database schema, metadata service or dependency was replaced.

Home now uses existing real profile continuation; hidden legacy hero/trending
enrichment is not eagerly run when that content is absent. Cinematic grid geometry
is bounded without rewriting saved preferences. See [the implementation report](min-tv-v0.4.md)
and PR #6 for final tests, performance evidence and physical limitations.

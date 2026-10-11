# Min TV v0.4: implementation and verification

Target: Chromecast with Google TV 4K, Android 14. Baseline is the owner's merged
v0.3 (`76f9280527f938211b4255a16b0ce7d819c162fb`).
Core remains pinned to `adca2bcd653f19e6e5d659c2722309e6aef4ec15`.
See the [audit completed before visual changes](min-tv-v04-audit.md).

## Sports: findings and limits

The reported physical exit after Färjestad's schedule arrives has **not been
reproduced**, and its exact cause remains **unconfirmed**. HTTP 200 and the
subsequent fixture display do not establish a parser crash. No physical exception
or stack trace has been supplied. This PR fixes source-verified focus hazards and
adds evidence collection; it does not claim to fix an unidentified crash.

- Dynamic favorites previously combined lazy automatic focus restoration with
  explicit selected-channel restoration. The competing automatic restorer is
  removed; saved selected IDs still determine restoration.
- Channel lookup completion previously requested the first result's focus on
  every result/state change. It now leaves ongoing remote navigation alone.
- Matchcenter transient tabs, channel matching and focus requesters are scoped
  to the profile. Fixture IDs already include competition identity; standings
  now use competition/team identity rather than mutable rank as the list key.
  Requesters for removed fixtures are pruned.
- Successful result publication retains its original exception and stack. It
  cannot silently become a fabricated cache/network failure. Request failures
  record safe exception classes and source frames; deadline and cancellation
  retain terminal states. Cancellation is distinguished from verified data in
  the diagnostic journal.
- Existing opt-in, visibility/lifecycle cancellation, strict validation, cached
  results, source restrictions and the shared 12-request/two-hour budget remain.
  No new sports endpoint, scraper, polling service or broadcast guess is added.

### Export evidence after the next physical failure

1. On the existing installation, note whether Home or Matchcenter is visible,
   selected league/tab, remote action and whether the app exited or restarted.
2. After an **owner-approved permanently signed update**, reproduce by opening
   Home with the selected teams, entering Matchcenter while loading, changing
   tabs and moving focus as verified fixtures arrive. Also try Back to Home,
   leaving/reopening during loading, cached reopening and switching profiles.
3. Reopen the app and use **More → Error log → Export crash report**
   (**Mer → Fellogg → Exportera kraschrapport** in Swedish).
4. Find the newest `mintv-sports-crash-report` text file in Downloads. Android
   may suffix repeated exports; use its date/report header to select the latest.
   Share this sanitized report, not IPTV URLs, credentials or the original raw
   error-log export. If no Java exception was recorded, the report still includes
   Android's recent process-exit reasons, including status and reported PSS/RSS.

The existing Core crash handler continues to persist the original exception and
delegate to Android's handler. The new export preserves exception class names and
source frames but removes arbitrary exception messages, URLs and tokens. A bounded
48-line sports ring is also coalesced into an app-private atomic file on IO using
the existing app coroutine scope; no database migration, worker or service.
It reads at most 8 KiB from the previous process. The last events may be lost if
the process dies before a write completes. Native exits and low-memory kills do
not necessarily have a Java stack. R8-obfuscated release frames require that exact
build's mapping to retrace; neither a log nor the synthetic emulator tests proves
the physical cause.

## Existing functionality reused

The User Guide/settings audit found both Cinematic and Separate panel layouts,
saved details heights/panel widths, category/search/sort controls, Room/Paging,
metadata caches/overrides, per-profile watch history, season/episode views and
the shared StageRail/Stage components. These implementations remain in use.
No new catalog, player, decoder, WebView, background service or dependency.

### Home and shared presentation

- Shared navy backgrounds, cyan focus accents and rounded Stage components;
  a cheap gradient improves the existing video/fallback hero. The actual muted
  preview, EPG/current-next information, favorite channels and existing fullscreen
  promotion are preserved.
- A conditional **Continue Watching / Fortsätt titta** row uses existing profile
  progress, accessible movie/episode IDs and positions. It deduplicates stable
  keys, shows at most eight recent items and is absent without real history.
  Focus loads still artwork only. OK stops the live preview and uses the existing
  movie/episode playback path.
- Home section navigation skips absent rows. Stable section keys keep external
  apps focused when a resume row is inserted. The rail's reserved width is used
  for focused/expanded navigation instead of covering Home content.
- Existing rail destinations, styles, profile switching and shortcuts remain.
  Twitch authentication, secure storage and external-app launching are unchanged.
  Hidden legacy Home hero/trending metadata is no longer eagerly requested on
  every feed load; no invented discovery content replaces it.

### Series and Movies

- Cinematic mode retains the focused provider-accessible title as its feature;
  it does not label arbitrary titles as trending or popular. Compact headings,
  a bounded feature region and responsive columns bring the paged grid upward.
  The rendered hero is capped at 240 scaled pixels and further constrained by
  available viewport height. Stored layout percentages remain unchanged.
- Grids choose 1–7 columns from available width (normally 5–7 on a wide TV
  layout), retain stable entity keys and per-category scroll restoration. Actual
  provider ratings are labelled; shared featured ratings identify provider/TMDB.
  Movie cards show existing watch progress/watched state.
- Cinematic, unfiltered browsing conditionally includes a profile-specific
  movie/series resume row. Search/category views and Separate mode retain their
  existing workflows. Search, category/genre folders, sort and favorites/history
  remain the existing provider-backed controls, not a new whole-catalog genre
  index or speculative recommendations.
- Movie poster OK opens the reused details screen with Play and Back actions.
  Provider-only/offline/ambiguous titles can still open details and play. Resume
  uses the existing saved-position policy; returning restores the selected ID.
- Series keeps existing season selection, episode details, progress, audio,
  subtitles, next-episode behavior and playback controls. Shared presentation
  changes do not replace the player or build a second episode implementation.

### Conservative metadata matching

The pinned Core already stores matches/TMDB IDs and cached details by type and
language; known-ID details and manual corrections are reused. The existing
authorized TMDB provider, personal/service-key settings, attribution/disclaimer,
budgeting, debounce, season mutex and positive/negative caches remain in place.

New search filtering makes one existing upstream request and considers at most
40 returned results. It requires correct movie/TV type, positive ID, exact year
when supplied, a title/original-title score of at least 0.92 and a 0.12 margin over
another eligible distinct ID. Ambiguous results abstain; transport `null` stays
`null` so a failed service is not converted into a negative-cache hit. Known-ID
provider calls delegate unchanged.

A separate guard prevents automatic app enrichment when the pinned normalizer
would discard meaningful bracketed words or numeric title content. Provider
titles are never rewritten. This is deliberately conservative: numeric titles
such as *1917* and some unfamiliar filename decorations keep provider artwork
and details until a manual **Set TMDB name / Refetch** correction is made. It is
not a complete replacement title normalizer. Existing positive caches are not
automatically purged or revalidated; historical wrong matches may still need
Refetch/manual correction. No speculative IMDb scraping or new external-ID
lookup is introduced.

## Reusable build and checks

Use JDK 21 and the Android SDK/platform/build tools required by the checked-in
Gradle configuration. `tools/prepare-core.sh` obtains and validates the exact
clean Core revision; do not substitute its moving main branch.

```bash
bash tools/prepare-core.sh
./gradlew :app:testQaDebugUnitTest :app:testStandardDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest \
  :app:compileX86_64DebugAndroidTestKotlin \
  :app:lintQaDebug :app:lintStandardDebug \
  :app:assembleQaDebug :app:assembleStandardDebug \
  :app:assembleQaRelease :app:assembleStandardRelease \
  :app:lintQaRelease :app:lintStandardRelease --max-workers=4
python3 -m unittest discover -s tools/tests -v
python3 tools/i18n/check_hardcoded_strings.py verify --bootstrap
python3 tools/i18n/check_number_locale.py
python3 tools/i18n/check_text_overflow.py
```

On a disposable accelerated Android 14 TV emulator, run:

```bash
./gradlew :app:connectedX86_64DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=tv.own.owntv.home.HomeRegressionSuite
```

Normal PR CI requires all 17 named tests to actually execute without errors,
failures or skips. It also runs separate disposable-key Android 14 upgrade/data
persistence and native signing fixtures. CI uploads synthetic component
screenshots as `mintv-v04-presentation-test-fixtures`; they contain neutral test
titles, not a real IPTV library or a claim of physical Chromecast testing.

### Actual local results (2026-10-11)

| Check | Result |
|---|---|
| QA JVM tests | 331 passed; zero failures/errors/skips |
| Production JVM tests | 331 passed; zero failures/errors/skips |
| Core / player-core JVM tests | 939 / 271 passed; zero failures/errors/skips |
| Android test Kotlin compilation | Passed |
| QA/Production debug and optimized release packaging | Passed |
| All four lint variants | Passed; 134 debug / 131 release warnings and 22 hints per variant, no errors |
| Python policy/security tests | 45 passed |
| i18n inventory, number locale, text overflow, actionlint, whitespace | Passed; hardcoded baseline unchanged |
| Optimized ARM APK inspection | Both IDs/labels isolated; no HOME, ARM32/ARM64 only, non-debuggable, release pseudolocales absent |
| Public Twitch Client ID in final optimized DEX | Verified in both release APKs |

Phase A was committed and tested before presentation changes. Its CI
[38160803507](https://github.com/jonasschroder/OwnTV/actions/runs/38160803507)
passed all 14 then-required TV tests and the disposable-key upgrade tests;
[38160803521](https://github.com/jonasschroder/OwnTV/actions/runs/38160803521)
passed i18n and native signing checks. Full v0.4 final CI/emulator results and
artifact links are recorded in the PR once executed; compilation alone is not
an emulator pass. No local accelerated emulator is available in this environment.

Comparable local unsigned release sizes (same Core/toolchain/ARM ABIs):

| Variant | v0.3 bytes | v0.4 bytes | Change |
|---|---:|---:|---:|
| QA | 63,666,984 | 63,684,008 | +17,024 (+0.027%) |
| Production | 63,665,972 | 63,682,996 | +17,024 (+0.027%) |

These are local verification outputs, not permanently signed installers. RAM,
startup time, physical scrolling latency and decoder performance have **not**
been measured. Source bounds include eight continuation cards, existing Paging
and sized Coil requests, 40 search candidates and 48 diagnostic lines. No
unrestricted library enrichment or new decorative animation is introduced.

## Physical acceptance checklist and safe update

The owner reviews/merges manually, then uses the existing protected permanent
QA signing/distribution and in-app update process. No signing environments,
keys, certificates, package IDs, update channels/checks or version counters were
changed. Default display version becomes 0.4; protected workflows still supply
their explicit version name/code. QA and Production keep separate data.

**Do not install CI debug APKs or locally unsigned release APKs on the populated
Min TV Test installation. Do not uninstall, clear data or perform a clean install.**
Use the established backup and approved same-certificate update process. This PR
does not publish a Release or dispatch protected signing/distribution.

- Capture the actual sports arrival exit/stack or Android exit reason as above;
  repeat loading, failure → success, timeout, cache, league/team/profile and
  foreground/background transitions with real configured teams.
- Navigate all six remote keys rapidly through rail styles, hero/favorites,
  Matchcenter, movie/series search/category/sort, grids, details, episodes and
  Settings. Verify focus/scroll restoration after Back/playback/profile changes.
- Test 6,965-movie / 1,936-series provider libraries, all metadata modes, offline
  fallback and manual corrections, panel height/width settings and other TV sizes.
- Confirm real muted single preview, EPG, promotion, favorite playback, movies,
  episode resume/next-episode, audio/subtitles and external-player preferences.
- Check Swedish system language, English/other locales and large text from
  normal viewing distance. Confirm SVT/SmartTube and Twitch/device authorization.
- Verify the owner-approved signed update preserves profiles, sources, imported
  data, favorites, history, progress and settings in each package independently.

External-data status: this phase's tests use HTTP-free synthetic fixtures; they
do not reconfirm provider access, future fixtures or a live website's structure.
Physical Chromecast acceptance and the unidentified crash remain outstanding.

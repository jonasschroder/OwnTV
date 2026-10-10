# Min TV v0.2 — personal profiles and sports

Work continues on PR #3; neither package identity, launcher registration nor the
pinned Core revision changes. No app is installed, removed, cleared or merged by
this work. The existing Min TV Test installation contains real IPTV data: read
[the safe QA update procedure](min-tv-safe-qa-update.md) before using a new APK.

## Phase A — channel matching, language and short first run

Commit `06d65024` is pushed to PR #3. Its exact committed source was independently
built/tested in a temporary archive: 253 app unit tests per variant, no failures.

TV4 Sport Live 2 HD SE / TV4 Sport Live 2 SE resolve to the exact broadcaster
identity. Country/quality wrappers are removed only at the edges; channel numbers
and TV4 Hockey identities remain distinct. Multiple real variants stay separate
in a compact picker. Unrelated PPV/no-event entries need exact-fixture EPG evidence
to become suggestions. Category browsing and logos work without typing; text
search remains an optional advanced action. Opening a fixture never starts video.

The existing LocaleStore default follows the system. Existing manual language
choices are preserved. All Min TV copy has English fallback and Swedish resources;
dates and numbers follow the active locale, while fixture time uses the real
Europe/Stockholm timezone (including DST). Other languages fall back to English
for these new strings. System-language changes and manual overrides still require
Chromecast acceptance testing.

Fresh installs acknowledge the existing necessary notice, create a normal default
profile and reach Home. Add source opens phone/QR/PIN LAN setup, backup import or
advanced direct entry; after source import/EPG decisions it returns to Home.
Display, language, profiles, content, backup and advanced source options remain
in Settings; the traditional full wizard implementation is retained. Existing
and restored profiles continue through the original PIN/profile gate. Creating
another profile uses the existing editor, not the full first-run wizard.

## Phase B — Mina lag / My teams

Home → Choose teams, or Settings → Profiles → My teams, edits **the authenticated
active profile only**. Browse sport → competition → club using D-pad/OK, toggle
multiple teams, make a team primary, move it up/down, remove it, hide/show the
Home section or hide/show result snapshots. Back moves up one picker level and
then returns to its entry. The team picker pauses sports requests; opening or editing it is local-only.

Club IDs include the sport, never the league/season. AIK football and ice hockey
are distinct; a promotion/relegation does not erase a stored choice. The bundled
hockey membership catalogue was checked against the public 2026/27 tables on
10 October 2026 and needs updating when future league membership changes. Football
is a selectable club catalogue, **not a claimed current Allsvenskan roster**.
Unknown valid club IDs are retained in storage across future catalogue changes.

Existing profiles receive the previous hardcoded Färjestad choice once, without
enabling either experimental reader. New/later restored profiles start with no
teams. Choices are private, ordered, profile-keyed and bound to profile creation
time; a reused database ID cannot inherit an old deleted viewer's preferences.
The Core database schema/pin and IPTV favorites are unchanged. These small new
preferences are outside Core's existing IPTV backup: after a separately authorized
restore/migration, reselect My teams and the companion opt-ins. An ordinary
same-signer in-place update preserves private preferences.

Home's **My sport / Min sport** remains compact: the next followed fixture on
ordinary days and up to three separate favorite match cards today. The matchcenter
shows the remaining fixtures, upcoming pages, tables and followed teams per
competition. Hide results conceals fixture scores; deliberately opening a table
still exposes standings/points. No live period, lineup or invented live score is
shown. Each match action uses the existing player or the visual channel picker.
Manual mappings keep exact fixture/profile/source/name/expiry checks.

## Phase C — source feasibility and actual availability

| Source | Evidence on 10 October 2026 | Implementation |
| --- | --- | --- |
| Swehockey SHL | Public index/table HTTP 200; exact season ID 20961 | Existing off-by-default experimental adapter |
| Swehockey HockeyAllsvenskan | Index, schedule and table HTTP 200; season ID 20962 | Same isolated opt-in reader with separate cache and exact league checks |
| Swedish football / Degerfors | Requests to `svenskfotboll.se/robots.txt` and `allsvenskan.se/robots.txt` failed at the environment proxy: `Tunnel connection failed: 403 Forbidden` | Club selection only; no fixture/table/broadcast network implementation |
| TVmatchen SHL broadcasts | Previously verified production reader resolves the exact FBK–Malmö fixture; terms private-use clause is not an explicit scraper/API licence | Existing separate optional experiment, with unchanged robots/terms hash/budgets/access gates |
| HA/football broadcast metadata | Permitted exact-fixture automatic data access not verified | No new scraper or guessed broadcaster; local EPG/manual channel matching remains |

The football failures occurred **before contacting the origin**. They do not prove
that either site forbids access. A documented permitted API/licence, robots rules,
response schema and reliable club/fixture identity must be verified before adding
a football adapter. No private/authenticated API, access workaround or fabricated
fixtures are used.

Swehockey's `/robots.txt` returned 404. No documented API or explicit automated
reuse licence has been established; public availability alone is not permission.
The user-authorized personal experiment remains off by default, clearly labelled,
replaceable and stoppable. Selecting BIK only adds its competition if the existing
experimental hockey opt-in is enabled. An access refusal or future disallow rule
stops HTTP; robots denial is not circumvented. For publicly distributed automatic
data, obtain provider permission or a supported licensed API first.

An offline JVM probe ran the **production Kotlin parser/domain adapter** against
the actual saved HA pages: 364 distinct fixtures, 52 BIK Karlskoga fixtures and
14 table rows; all club identities, 2026/27 season bounds and Stockholm instants
passed. This verifies the retrieved HTML, not ongoing live-source/device behavior.
Unexpected markup or league/season identity fails safely, leaving cached data
labelled with its fetch time rather than presenting new guessed results.

## Resource and request limits

No player/decoder, WebView, background service, image feed or dependency is added.
Only the active profile's required competitions are requested while their content
is visible; no teams, hidden Home, background, the team picker, Settings without fixture content, disabled opt-in or unsupported
football means no sports HTTP. Opening My teams is local-only. Leaving the app
cancels the existing cancellable OkHttp request path.

Hockey schedules are cached six hours, one hour on a league matchday; tables six
hours per competition/season. Two bounded schedule caches retain at most 400 rows
each; HTML responses are capped at 600 KB. Requests share a persisted maximum of
12 GETs per two hours across both hockey leagues, reserved before dispatch so
process recreation cannot reset it. Visible failures back off to six hours; there
is no verified live feed and no live-score polling interval. Timeouts use the
existing eight-second request path. Cached/source update times are separate.

TVmatchen retains its existing six GET / three fixture-page cap per two hours,
robots six hours, terms 24 hours, assignments two hours, bounded journal/cache and
permanent stops on 401/403/429 or changed terms/robots. At most three visible Home
favorite cards are resolved using bounded local channel/EPG queries; metadata
lookups share that budget and cached mappings. Playback is never blocked by a lookup.

## Device acceptance still required

- Back up the installed **Min TV Test** privately; verify preview/profile/source/
  favorite counts, passphrase and a restore copy in a disposable test environment.
- Check installed QA signing certificate/version against the candidate before update.
- Preserve v0.1 and QA data, PIN/profile isolation, Xtream/M3U/Stalker imports/EPG.
- D-pad/OK/Back, long lists, picker focus and return from fullscreen/external apps.
- Multiple profiles with FBK, BIK and Degerfors; independent order/primary/team removal.
- Muted favorite preview, single decoder, fullscreen promotion, channel variants,
  actual EPG match evidence, provider limits and external shortcuts.
- Swedish/English system changes, explicit overrides, dates/numbers and small-screen text.
- Opt-in off/no favorites/background: zero sports traffic; no verified lineup/live period.
- Process recreation/clock changes/cache expiry and overlapping favorite fixtures.
- Measure RAM/startup/playback on the Chromecast; cloud builds cannot establish those.

## Actual cloud checks

- Read-only update gate: correctly **blocked** the retained run-14 QA APK versus
  the local candidate because their verified certificates differ. This is a
  negative safety check, not a verification of the actual installed Chromecast APK.
- Phase A exact archive: 253 app unit tests for each variant, all passing.
- Final app: **270 tests per variant**, zero failures/errors/skips; pinned Core
  **939** and player **271** passing tests. Python update gate: **6** passing checks.
- Both ARM debug APKs and both instrumented test APKs compile. Instrumentation
  was **not run** on hardware/emulator.
- Both debug lint reports: **0 errors, 105 warnings, 22 hints**. Seven additional
  style warnings concern deliberate checked SharedPreferences commits; data-store
  persistence failure is not silently discarded for the KTX quickfix.
- Regular/QA packaged identities, providers, permissions, external schemes, nine
  icon entries and ARM ABIs pass; neither declares Android HOME. Debug
  pseudolocales, number formatting, text overflow and the unchanged merge-base
  literal baseline (45 entries) pass.
- Native payload: **all 26 libraries unchanged**, ARM64 plus ARMv7; no runtime
  dependency or player change. Clean local QA APK: **89,594,168 bytes**, +201,332 bytes (**0.23%**) versus
  the retained pre-broadcast QA reference (89,392,836 bytes). The initial
  incremental ZIP contained unused space; clean packaging removes that overhead. Real Chromecast
  RAM, startup, decoder and source/playback performance still require testing.

## Validation commands

Run `bash tools/prepare-core.sh`, then use JDK 21 and Android SDK/Build Tools
37.0.0 (Core remains pinned to `adca2bcd653f19e6e5d659c2722309e6aef4ec15`):

```bash
python3 -m unittest discover -s tools/tests -v
./gradlew :app:testStandardDebugUnitTest :app:testQaDebugUnitTest :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest
./gradlew :app:lintStandardDebug :app:lintQaDebug
./gradlew :app:assembleStandardDebug :app:assembleQaDebug :app:assembleStandardDebugAndroidTest :app:assembleQaDebugAndroidTest
./gradlew :app:assembleStandardRelease
python3 tools/i18n/check_number_locale.py
python3 tools/i18n/check_text_overflow.py
```

Actions additionally enforces the merge-base literal baseline, packaged identity/
authority/permission/deep-link/ARM/no-HOME checks and debug/release pseudolocales.
Instrumented APK compilation is not device execution. Reports and QA APK remain
workflow artifacts; no public Release or automatic merge is performed.

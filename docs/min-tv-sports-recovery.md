# Sports data recovery after the physical build 23 test

Keep the populated **Min TV Test** installation and regular Min TV v0.1 intact.
This change does not install, uninstall, clear data, register Android HOME, change
system settings or change the IPTV player. A new Actions APK is a validation build:
[verify the real backup and installed signing identity first](min-tv-safe-qa-update.md).
Never remove the working app to overcome a signature mismatch.

## What failed and what changed

The official pages retrieved on 10 October 2026 returned HTTP 200 for both leagues,
but the old repository required a literal `<h2>SHL</h2>` or
`<h2>HockeyAllsvenskan</h2>`. Their actual competition heading is `<h1>`, with a
separate `SHL - 2026-27` / `HockeyAllsvenskan - 2026-27` season label. That guard
rejected the response before the schedule/table parser ran. Older parser-only
probes therefore did not prove that the full repository could load these pages.

The replacement guard verifies the actual heading, current Stockholm season label
and the selected schedule/table route together. Unrelated selected Live/Overview
menus and duplicated mobile/desktop selectors do not masquerade as the active
competition. It validates every fixture's current-season date and both club
identities, and requires the complete, correctly identified 14-club table. A wrong
league, season, route or changed structure fails safely without replacing a good
snapshot. League IDs are discovered from the official index, never inferred from
a time, guessed from a previous league or hardcoded into production fetching.

Sport activation previously also depended on IPTV favorite focus and preview
LOADING. Now resumed Home/Matchcenter visibility determines it independently of
preview decoder/focus state. Home requests only leagues needed by the active
profile; Matchcenter requests only its selected supported competition. Settings,
team selection, background, fullscreen, disabled experiments and unsupported
football do not initiate schedule/table requests. Leaving visible content cancels
its coroutine and the existing OkHttp call. No player, service, WebView, decoder or
runtime dependency is added. A failed HA fetch cannot invalidate a good SHL
fixture's broadcaster/EPG matching, or vice versa.

The old silent failure path waited two hours after the first schedule error and
cleared table data on failure/re-entry. Verified schedule and table snapshots now
have separate persistent caches and actual fetch timestamps. Transient failures,
parse errors and access blocks leave them intact. Returning to the screen or
recreating the repository does not refetch a fresh cache or reset retry/budget
state. Cache data outside the current season or for another competition is not
shown as current. The two leagues share discovery and robots checks; requests at
the same timestamp also reuse that check.

## States, retry and access policy

Home and centered Matchcenter panels distinguish loading, genuine no-scheduled
fixtures, unavailable network/HTTP, unverifiable format, stopped access, disabled
experiment, request limits, cache I/O and unsupported football. A valid retained
snapshot is labeled with its own last successful update time. Empty caches never
produce a misleading “saved data” or “updated unavailable” timestamp. Results
remain snapshots; this does not claim a live score, lineup or period feed.

**Försök igen** is offered after the persistent cooldown. It re-evaluates the
visible request; it cannot bypass a fresh cache, robots/access block, Retry-After
or the shared **12 GET / two-hour** hard budget. Requests are reserved durably
before dispatch, including failed/cancelled requests. Network/HTTP/cache failures
start at two minutes, markup failures at ten minutes, with increasing cooldowns
capped at thirty minutes. HTTP 429 honors Retry-After with a fifteen-minute minimum.
Schedule cache TTL is one hour on matchdays, otherwise six hours; tables and
robots/discovery use six hours. There is no background polling.

401/403 and robots denial remain persistent access stops. Existing legacy
`shl-access-blocked` flags are retained, including flags an older build may have
set for 429. They are not silently cleared by this update, toggling opt-in or
retry. If the device shows stopped access, inspect **Min sport → Matchcenter →
Inställningar → Diagnostik för sportdata**: it displays league, last HTTP status,
request stage, time, last schedule/table failure and shared request count. This
information contains no IPTV URL/password, token or response body. Do not clear
app data or bypass restrictions to recover access; a legacy persistent block
requires a separate policy/source review. New 429 responses use a timed limit.

The personal Swehockey experiment remains explicitly off by default and
replaceable. Its robots endpoint returned 404 during this investigation; that is
not a licence. No documented API or explicit automated-reuse permission has been
established. Existing TVmatchen opt-in and independent policy/budget remain;
lineup/live-period sources and unverified football fetching remain disabled.
Degerfors and other football clubs can be followed, with a clear unsupported-data
message. Public/production automatic data distribution still needs provider
permission or a supported licensed source.

## TV layout

Home content starts below the measured shell status/clock cluster, with a small
gap. Padding reserves the viewport outside the scrolling list, and that viewport
clips scrolling content. Programme details use a constrained weighted column;
long EPG descriptions are limited to three lines with ellipsis. This prevents a
scrolling description from travelling under the top-right clock/status area.
Empty/error Matchcenter panels occupy the remaining area and use readable TV
text. The integration test checks the production viewport/text components with
long content and actual lazy scrolling on a 1080p emulator hardware profile.

## Verification and reproducible build

Core remains pinned to `adca2bcd653f19e6e5d659c2722309e6aef4ec15`. With JDK 21 and
the repository's Android SDK/Build Tools requirements:

```bash
bash tools/prepare-core.sh
python3 -m unittest discover -s tools/tests -v
./gradlew :app:testStandardDebugUnitTest :app:testQaDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest \
  :app:lintStandardDebug :app:lintQaDebug \
  :app:assembleStandardDebug :app:assembleQaDebug \
  :app:assembleStandardDebugAndroidTest :app:assembleQaDebugAndroidTest
```

QA output: `app/build/outputs/apk/qa/debug/app-qa-debug.apk`. Actions stages
`MinTV-v0.2-QA.apk` with this guide, checksum, public certificate, backup/signing
instructions and the read-only update checker. Ordinary PR builds use ephemeral
debug keys and receive no signing secrets. See the PR's current-head check results
and artifact link for the exact distributed APK, version and signer.

Local verification: **291 app unit tests per variant, 939 Core, 271 player and
6 Python checks** pass with no failures/skips. Both ARM debug variants and their
instrumentation APKs build; lint has **0 errors, 117 warnings and 22 hints** per
variant. Package/authority/permission/link isolation, absence of HOME and debug
pseudolocale packaging pass. The new saved-page and repository
regressions exercise the real captured October 2026 source markup, including the
full validation guard, Frölunda's next fixture, HA/BIK identities, cache recreation,
wrong-season/league rejection, cancellation, access denial, 429, persistent hard
budget, short failure backoff and table preservation. Public source excerpts and
provenance are test-only resources; they do not ship in either runtime APK.

A separate live probe on 10 October used the actual production `HockeyReader`,
`HockeyHttpPages`, `CompanionHttp` and OkHttp path against the official source,
with opt-in test storage and without redirects/access workarounds:

| Request | HTTP | Verified output |
| --- | --- | --- |
| `/robots.txt` | 404 | No robots policy served; no licence inferred |
| `/` | 200 | SHL 20961, HockeyAllsvenskan 20962 |
| `/ScheduleAndResults/Schedule/20961` | 200 | 364 SHL fixtures |
| `/ScheduleAndResults/Schedule/20962` | 200 | 364 HA fixtures |
| `/ScheduleAndResults/Standings/20961` | 200 | All 14 SHL clubs/ranks |
| `/ScheduleAndResults/Standings/20962` | 200 | All 14 HA clubs/ranks |

It verified **IF Björklöven–Frölunda HC, 10 October 2026 at 18:00 Europe/Stockholm
(16:00 UTC)**, viewed at 17:06 Stockholm. The full reader used six requests, sharing
robots/discovery across leagues. This establishes current cloud-source/parser
compatibility, not access or behavior on the Chromecast's own network.

The required Android CI suite now has six executed cases: two D-pad navigation
cases, the existing favorite/storage test, production Android AtomicFile/preferences
+ OkHttp/parser cache/failure/access integration, per-profile team persistence/
migration/ID reuse, and EPG viewport/ellipsis/scroll geometry. It rejects missing,
skipped or failed cases. Android HTTP integration serves saved official HTML
through an interceptor; it does not repeatedly fetch the live sports site.
The 1080p hardware profile is an emulator harness, not Google TV firmware.

## Physical acceptance still required, only after the safe update gate

1. Verify the encrypted real IPTV backup privately and compare the candidate APK
   with the actual installed QA APK using the read-only update checker. If the
   signer differs, stop and keep both apps installed; do not uninstall or clear.
2. Open the existing QA profile with Frölunda, enable the personal experiment if
   desired, and verify today's/next genuine fixtures and the complete SHL table.
   Check loading/failure/retry states and actual cache timestamps; inspect Settings
   diagnostics if access is stopped.
3. Follow BIK Karlskoga too, switch SHL/HA, add Degerfors and verify its explicit
   unsupported message. Switch profiles and back; no other profile's choices should
   appear, and repeated opening should not consume extra fresh-cache requests.
4. Keep the moving muted preview running or waiting for playback while opening
   sports. Test Back/background/return, complete Home D-pad traversal and the
   long EPG description near the clock on the physical 1080p output.
5. Verify Färjestad/local EPG/manual broadcaster matching, existing authorized
   channels, one muted preview and fullscreen promotion, external-app shortcuts,
   favorites and provider connection limits. Confirm regular Min TV v0.1 still
   has its original data. No automatic device update or migration is performed.

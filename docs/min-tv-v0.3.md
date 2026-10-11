# Min TV v0.3: sports reliability and TV presentation

This work starts at `main` commit `98e6dd9f6cd1ba3d090615019d8561ed64392943`.
Reliability changes are committed before the presentation changes. The owner's
Chromecast runs Android 14 and `0.2.0-beta.2`; it is not connected to this workspace.

## Phase 1: evidence and fixes

The owner reports indefinite “Hämtar…” on Home/Matchcenter, occasional exits while
navigating, and a last-response diagnostic of SHL/SCHEDULE/HTTP 200. No physical
exception or logcat was available. An HTTP result records transport success, not
successful competition, season, table or club validation.

Confirmed code defects:

- Unrequested schedules and tables initially appeared to be loading. An empty
  table was explicitly presented with `loading = true` even after work ended.
- Cache reads preceded the fetch error handler; failures there could escape the
  UI effect. A nullable repository result did not establish a terminal state.
- Switching Home/Matchcenter or standings/My Teams keyed effects to presentation
  rather than the actual competition request, unnecessarily cancelling/restarting
  the same work. This consumes the budget if a request was already dispatched.
- Lazy-list focus requests could execute after their targets were removed.
  Concurrent D-pad scroll jobs compounded this. `runCatching.isSuccess` also
  confused a refused focus request (`false`) with successful focus acquisition.
- Parsing/validation failures lost their specific reason and HTTP 200 context,
  leaving only the last transport result in diagnostics.

The fixes establish terminal states on success, verified empty data, failure and
lifecycle cancellation; wrap cache and fetch together; impose an 8-second per-page
and 35-second whole-operation/mutex deadline; preserve verified cached content;
retain persistent reader backoff and the shared 12-request/two-hour budget; key
effects to actual request scope; and safely handle detached/refused focus targets.
Back/background cancellation propagates to the existing HTTP transport and is not
misreported as a server failure. Only the latest Home scroll/focus job remains.

These defects are reproducible in tests/code inspection. They do **not** prove
which exception caused the owner's observed physical exit. No observed data has
been invented to fill the screen.

### Source format checked on 11 October 2026

Bounded manual investigation used only the existing experimental Swehockey source:

| Public path | Result | Verified current format |
| --- | --- | --- |
| `/robots.txt` | 404 | Existing reader's 404 policy; no restriction bypass |
| `/` | 200 | SHL overview ID 20961; HockeyAllsvenskan live ID 20962 |
| `/ScheduleAndResults/Schedule/20961` | 200 | SHL, season 2026–27, selected schedule route; 364 fixtures |
| `/ScheduleAndResults/Schedule/20962` | 200 | HockeyAllsvenskan, season 2026–27, selected route; 364 fixtures |
| `/ScheduleAndResults/Standings/20961` | 200 | SHL season/route and named standings table; 14 rows |
| `/ScheduleAndResults/Standings/20962` | 200 | HockeyAllsvenskan season/route and named standings table; 14 rows |

The full current pages passed the parser and reader season/club validation locally,
including all selected route filtering. A source-format change was **not reproduced**. Committed recorded
10 October real-format fixtures cover both leagues' complete 364-match schedules
and 14-team tables. Derived header-only empty and deliberately damaged responses
test failure/empty handling without representing them as real sports results.

Validation stays strict: competition, season, route, headers, unique games, both
club identities and standings membership. Only a verified table with the expected
headers can be empty. Unexpected HTML, changed headers or wrong leagues fail safely
and keep the previous valid snapshot. Diagnostics record a bounded reason code,
not raw HTML, URLs with tokens, credentials or stack traces in the viewing UI.

The experimental source stays off by default, manually enabled, visible-only,
cached and conservatively limited. A reachable public page does not establish a
licensed API. No new provider, robots exception, access workaround, lineup/live
period source or unverified football feed was introduced.

### Checks and remaining verification

New unit coverage exercises success/empty/error final states, HTTP 200 with wrong
HTML, persisted validation reasons, transport/whole-operation timeouts, backoff,
parent cancellation and reopening. Existing profile and recorded-format suites
remain. Production Matchcenter tabs are reused by new Android remote-event tests
for repeated navigation during fetch, Settings cancellation, Back/focus return,
reopening, detached focus and timeout recovery. CI requires all eight Home/sports
regression test cases to execute and pass; failure XML is printed even if the
instrumentation step fails. These component tests do not claim whole-app physical
Chromecast coverage.

Phase 1 local validation, 11 October 2026:

| Check | Result |
| --- | --- |
| `:app:testQaDebugUnitTest` | 318 passed; zero failures/errors/skips |
| `:app:testStandardDebugUnitTest` | 318 passed; zero failures/errors/skips |
| `:app:compileX86_64DebugAndroidTestKotlin` | Passed; instrumentation execution awaits CI |
| `:app:lintQaDebug :app:lintStandardDebug` | Passed; no errors/fatal issues, 133 warnings per variant |
| `python3 -m unittest discover -s tools/tests -v` | 42 policy/update/distribution tests passed |
| Android workflow actionlint; i18n merge-base ratchet; `git diff --check` | Passed |

Phase 1 full CI passed in [run 38127321119](https://github.com/jonasschroder/OwnTV/actions/runs/38127321119),
including all eight required Home/sports Android TV cases, Core/player tests,
packaging and the unchanged Android 14 signing-upgrade fixture tests. The i18n
workflow also passed. These emulator tests do not reproduce the owner's complete
Chromecast installation.

## Phase 2: Home and Matchcenter presentation

The existing Home content is refreshed inside the existing configurable OwnTV
shell/rail; no destination or rail setting is replaced. The first section groups
the existing 16:9 muted preview with its channel logo/name, verified current EPG,
progress, next programme and explicit Watch/Live/Guide/Settings actions. The clock
keeps its reserved viewport. Long descriptions are bounded. Empty channels,
unavailable streams and missing EPG keep useful existing fallback actions.

Favorites use readable horizontal cards and current programme/progress. At most
six visible cards read existing local EPG in one bounded batch; this does not
request a new provider feed or create a decoder. Hidden/background/Settings views
stop this work. The existing preview engine, delayed selection, single-stream
policy, DRM surface choice and fullscreen promotion remain in use. Moving to the
hero's Watch action retains the current preview; rendering a card never starts
playback. SmartTube/SVT and Twitch stay compact, with technical data in Settings.

Matchcenter uses compact dark panels, readable fixture cards, verified broadcast
assignments/manual fallback and shorter standings rows. All selected profile teams
appear in profile order, with their table snapshot time. Verified club names use
text monograms; no unofficial logo downloads were added. Unfetched data is distinct
from a verified empty snapshot. Diagnostic reason/HTTP details stay out of match
cards. Minor Live changes are limited to programme typography and progress width.

Two production-presentation Android tests cover hero geometry outside the clock,
remote OK/Right callbacks, no rendering-triggered playback, expired EPG fallback,
and compact failed sports content without diagnostic leakage. Two more exercise
actual Twitch onboarding with a fake authorization boundary, not external HTTP.
CI requires all 12 individual Home/sports/layout/profile/Twitch tests to execute
and pass, and still runs the existing Android 14 signing/update fixture suite.
Final results are available in this PR's checks; hardware behavior remains a
separate owner follow-up.

## Twitch: automatic public-client configuration

The owner's registered **public** Twitch Client ID is the project default in
`gradle.properties`. It is intentionally public, not a Client Secret or token.
Both QA and Production obtain `BuildConfig.TWITCH_CLIENT_ID` through the same
`defaultConfig`, for debug and release. A nonblank `MINTV_TWITCH_CLIENT_ID` overrides
the default; otherwise `-Pmintv.twitchClientId=PUBLIC_ID` or the project property
applies. An empty GitHub variable no longer masks the project default. Both the
ordinary CI and existing protected signing workflow already bind the repository
variable `MINTV_TWITCH_CLIENT_ID`; the protected workflow itself is unchanged.

For an unconnected account, **Anslut Twitch** immediately starts the existing
public-client device flow and displays Twitch's validated activation URL/code.
No remote Client ID entry or second Connect press is required. Close/Back or
backgrounding cancels onboarding. After a failure, Retry and **Avancerat: ändra
Client ID** remain available; a manual public-ID override takes precedence.
Connected-account settings retain disconnect and advanced editing. Tokens remain
in the unchanged AES-GCM Android Keystore-backed no-backup store. QA/Production
keep separate Android sandboxes. No secret, new token storage or authentication
protocol change was introduced.

Reproduce release checks without signing credentials:

```sh
bash tools/prepare-core.sh
./gradlew :app:testQaDebugUnitTest :app:testStandardDebugUnitTest \
  :app:compileX86_64DebugAndroidTestKotlin :app:lintQaDebug :app:lintStandardDebug
./gradlew :app:assembleQaRelease :app:assembleStandardRelease --max-workers=4
python3 tools/verify-twitch-apk.py --apk app/build/outputs/apk/qa/release/app-qa-release-unsigned.apk
python3 tools/verify-twitch-apk.py --apk app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk
python3 -m unittest discover -s tools/tests -v
```

The APK check searches actual packaged DEX string tables after R8, not generated
BuildConfig/debug files or resource text. CI now builds both optimized ARM release
variants and verifies the exact configured public ID survives in each. Three
verifier tests cover secondary DEX, exact matching/resource-only rejection and
truncation. The production UI tests confirm first-click use and advanced override
precedence. Real Twitch account completion/API access still needs a physical test.
These local/ordinary CI unsigned release outputs are verification artifacts, not
installers for the owner's permanently signed apps.

## Release and device safety

Permanent signing/distribution workflows, keys/environments/certificates,
authenticated update verification, package identities and protected version-code
allocation are unchanged. No signing/distribution dispatch, publication, merge,
installation, uninstall, data clear, restore or Android system modification is
part of this task. Ordinary CI builds/checks do not upload installers.

Before a later owner-approved QA update, use the existing permanent QA channel;
do not replace the installed app with an ephemeral debug certificate. Existing
IPTV data, favorites and per-profile sports preferences stay in their existing
private stores; no schema migration is needed.

Physical follow-up must cover the real FBK fetch/standings, rapid D-pad/OK/Back,
Home return, foreground/background and process recreation, profile switching,
IPTV favorites/EPG, muted preview/fullscreen/Back/audio, channel matching and
external-app handoff. Hardware decoding, casting and the reported device-only
exit cannot be certified by JVM/component/emulator tests.

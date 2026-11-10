# Sport v0.19 — phase 1 stabilization, 2026-11-08

Draft PR #40 only. This is **not the completed v0.19 hub or a release approval**.
The calendar, team/league pages, all targeted sports/providers and expanded Watch Match view
remain in scope for the same v0.19 delivery, through reviewable implementation workstreams
after this CI stabilization, as [the owner's revised roadmap](../../sport/v019-hub-roadmap.md) requires.

## Reported CI failures and fixes

- The two `NativeV011PresentationTest` failures expected Down to reach Today immediately.
  The new, legitimate My teams rail now precedes Today. Tests explicitly traverse that rail,
  check deduplication, continue through Today/upcoming, repeat Right rapidly, and recover after
  fixtures disappear. They retain exact focus assertions; no waits or checks were weakened.
- The new Choose teams button was below the primary buttons but registered as a horizontal
  neighbor. All hero actions now share a visible horizontal row, with space for scaled text.
- During cold entry, loading exposed only Choose teams. The shell could accept that entry
  before the existing recovery action appeared, leaving focus on a different action from
  the settled screen. Local settings/channel browsing now remains reachable throughout
  loading, independent of schedule/catalogue hydration. Existing entry keys stay stable.
- Back from the team picker previously requested the first hero requester. It now invokes
  Sport's existing selected-key entry callback, guarded by current profile and activity.
  This restores Choose teams instead of jumping to Play/settings.

Runtime changes are confined to `NativeSportDashboard`, the picker dismissal in
`MinTvCompanions`, and a technical dialog tag in `SportsTeamPicker`. Existing PR #40
grouping/preferences/fixtures are reused. Core, playback engines, storage schema, source
authorization, package/version/signing policy and the 0.18 navigation/cache optimizations
are unchanged. The Android workflow only **adds** the new mandatory regression result.

## Validation

- QA debug app + instrumentation build and Android lint: passed.
- `SportsModelsTest`: 11 passed (cross-competition following, deduplication, hero suppression,
  empty preferences and existing sports policies).
- `NativeV011PresentationTest`: 17 passed, including both original failing cases, rapid
  D-pad repeats, disappearing fixtures, loading/empty/profile entry, long OK and 720p at
  140% text size. The same production APK was used throughout; only the startup test was
  subsequently extended.
- Production `Application`/Koin/Room/`MainActivity` cold-start and activity-recreation
  journey: passed at 1080p and 720p. It now opens the actual local picker with the remote,
  follows Färjestad through Ice hockey → SHL, returns through Back, repeats to undo the
  choice and verifies the original per-profile preferences survive. No HTTP opt-in is added.
- Four matched production navigation journeys and before/after populated-shell journeys
  passed, including real emulator decoder output, source picker and playback return.
- i18n: 45 existing baseline + 2454 safe literals; number locale, text overflow and strict
  device-test execution inventory passed. The new case is required by CI, not skipped.

See [raw results](tests/), [unit summary](tests/unit-summary.json), and
[artifact/APK hashes](provenance.json). GitHub's **exact-head checks on PR #40** are the
authoritative full-suite results; local targeted tests alone do not satisfy that gate.

## Native visuals — synthetic fixtures, not actual broadcasts

These are Android screenshots, not HTML. Fixtures/scores/channel evidence in the test
images are deliberately synthetic and prove no real match-to-channel assignment.
Full production shell:

| Resolution | 0.18 baseline | Candidate |
| --- | --- | --- |
| 1920 × 1080 | [Before](before/SPORT-1920x1080.png) | [After](after/SPORT-1920x1080.png) |
| 1280 × 720 | [Before](before/SPORT-1280x720.png) | [After](after/SPORT-1280x720.png) |

The production Sport composable with an additional followed fixture demonstrates the
new personal rail: [1080p](after/MY-TEAMS-1920x1080.png),
[720p](after/MY-TEAMS-1280x720.png). The buttons' left-to-right geometry and remote
reachability are also asserted at 140% text size. Home/TV/VOD are not redesigned here.

## Comparable 0.18 performance check

Android 14 ARM64 disposable `mintv-v016`/`emulator-5588`, SwiftShader, 4 cores, 2 GiB RAM.
Same final instrumentation APK, synthetic 12,000-channel profile, Swedish, density 320 for
both benchmark resolutions, weather/sports HTTP/previews/update checks disabled. Two
warmup cycles, then 20 cycles per resolution/variant in one process each, plus resume cases.
Section entry measures header selection and Compose idle, **not populated sports-feed
readiness, photons, real-channel TTFF or Chromecast performance**. Populated screenshots
and decoder tests run separately after all timing runs (720p screenshot density 213).
Both timing variants retained the startup fixture's +60 minute EPG offset, before any
performance EPG fixture was populated. An initial baseline capture then failed its late-EPG
assertion because that picture fixture assumes zero offset. It is excluded. Both final
capture journeys use a separate fresh disposable data image with the default zero offset;
the persistence fixture is preserved and no assertion is skipped or relaxed.

Baseline main `29144b0373fb9dd2b283bb000bab2ee84995c38e` has exactly the same Git tree as
the previously verified `2f664b829ce80188972cc4a034f4214b0686ed04` binary; its SHA-256 is
checked before reuse. Candidate sources are identified by [subtree hashes](source-trees.json).
Both local binaries use the existing build helper's `0.14.0-dev` fixture stamp; this does
not change a release version or identify the owner's installed QA app.

| Sport entry, ms | Before median / p95 | After median / p95 |
| --- | ---: | ---: |
| 1080p | 197.9 / 314.2 | 199.3 / 232.6 |
| 720p | 183.6 / 216.4 | 197.1 / 217.4 |

p95 uses nearest rank. Every measured run retains 62 catalogue-page queries, 92 version
probes, 45 guide-summary reads and 0 instrumented public HTTP calls. These counters do
not cover every DAO or arbitrary networking. Process CPU: 42,694 → 39,873 ms at 1080p,
37,893 → 37,767 ms at 720p. Sampled maximum PSS: 272,346 → 264,996 KiB and
268,361 → 265,072 KiB respectively. Samples are not peak memory/leak evidence.

**No general speedup claim.** The 720p Sport median rises 13.5 ms; p95 is essentially flat.
One process per configuration cannot establish significance or physical-device performance.
All sections, frame samples and other measurements remain in [raw JSON](performance/) and
[the comparison](comparison.json); software-rendered frame samples are not a real-TV FPS claim.

## Remaining gates / physical QA

- Exact-head full CI must pass; keep PR #40 draft and do not merge/sign/distribute.
- Owner's Chromecast: existing QA data, opt-in/offline, team selection, repeated directions,
  loading → available transitions, multiple profiles and Back after actual playback.
- Check real source revocation and the authorized channel picker against actual provider data.
  This phase does not verify real Färjestad broadcast assignments or expand provider coverage.
- Calendar/local-date navigation, team/league details, the targeted competitions and the
  confidence-ranked Watch Match experience are not implemented by this stabilization commit;
  they remain active v0.19 scope, without deprioritizing NHL or non-hockey sports.

No Chromecast action or installation, production-data mutation, Core pin change, release
workflow dispatch or paid service is part of this work. See [prepared Swedish notes](../../release-notes/v019-sport.sv.md).

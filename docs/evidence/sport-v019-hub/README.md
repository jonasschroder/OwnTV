# Native Sport hub — review evidence

Integrated on PR #41 (`4a08a3f28df1ab8aab14a37566bbbfb402c20b05`), including PR #40.
See [implementation and source limits](../../sport/v019-native-hub.md).

## Method

Only the approved disposable Android TV 14 emulator `mintv-v016` / `emulator-5588` was used.
All native UI images are unedited Android captures. UI scenarios, channels, profile, fixtures and
loopback playback are synthetic, explicitly separate from the actual public NHL probe.
`0.14.0-dev` is the existing local build-fixture stamp, not a released version or signer.
No owner's Chromecast, Stable, QA data or private IPTV source was accessed.

Fullscreen confirmation windows must detach before remote input. The existing finite Back
hierarchy hides the player HUD, exits playback, then restores the exact selected channel.
The new hub now waits for its schedule and authorized channel owners to hydrate before focus
restoration; returning to the overview also waits for the original Sport owner. A temporary
empty/loading composition must not overwrite the saved focus with the toolbar.

Compare the actual 0.18 baseline [1080p](../sport-v019/before/SPORT-1920x1080.png) /
[720p](../sport-v019/before/SPORT-1280x720.png) with the candidate images here. Baseline binary
provenance is inherited from [the matched baseline](../sport-v019/provenance.json).
Dates and synthetic fixtures vary with capture time; these pictures are not live sports evidence.

## Public runtime proof

`public-nhl.json` is produced by the actual Android transport/parser/cache stack, not a mocked
HTTP response. Its UTC clock was checked against the host before instrumentation. It contains
only source identity, timestamps, count, cache/coverage/error state. All 58 explicit-UTC NHL
records were retained; Android's Canadian timezone rules disagree on local corroboration,
shown honestly as `TIME_CONFLICT`. This is not proof that the provider's UTC is incorrect.
No team artwork, raw feed, token or private URL is redistributed.

## Limitations

The reviewed source contracts and quotas still apply. League/team/Calendar navigation does not
make incomplete scores or tables current. New public APIs supply no verified linear broadcast
mapping; manual confirmation is not broadcast evidence. No physical Chromecast acceptance or
official signed upgrade has been performed. Full CI and owner review gate any later release.

## Focused validation and performance

18 final hub JVM cases pass; the earlier focused model/broadcast/recall runs covered another
44 cases. The inherited 31 source tests and source CI are green in #41. Local QA compile/lint,
i18n literal ratchet, locale formatting and text overflow checks pass. Five native hub cases,
profile storage, probable-vs-confirmed playback and original Sport focus cases pass. Full
production-shell and hub route captures exercise the real MainActivity at both sizes, including
synthetic decoded playback and exact Back focus. Required CI adds these cases, never removes one.

Fresh separate synthetic userdata; old emulator images retained. Same 0.18 APK provenance,
candidate APK, instrumentation, 12,000 channels, 2 warmups + 20 measured cycles per resolution.
No concurrent Gradle build during timings. Display density 320 for both matched variants;
visual acceptance separately uses the existing 1080p/720p density policy. Timings use the
pre-polish APK identified in provenance. Final polish only clips neutral decoration in the new
hub dialogs/empty tiles/public-only hero, outside the measured empty-public-follow journey.
These timings are not claimed as an exact final-APK speed measurement; mandatory CI measures
the final APK separately. Full native captures
run after timings. The final capture-only helper waits for Android window animations; measured
journey code is unchanged. Raw records and nearest-rank summaries are committed separately.

| Sport entry, ms | 0.18 median / p95 | Candidate median / p95 |
|---|---:|---:|
| 1920 × 1080 | 247.6 / 316.2 | 249.7 / 283.6 |
| 1280 × 720 | 249.4 / 300.0 | 250.0 / 299.5 |

All four runs: CATALOG 62, CATALOG_VERSION 92, GUIDE_SUMMARIES 45, PUBLIC_HTTP 0.
These results support preserved bounded read work and approximately unchanged median entry,
not a proven general speedup. Frame/PSS/CPU samples are emulator observations, not physical
Chromecast peak memory, decoder latency or real-provider time-to-first-frame. All other screen
measurements and frame callback-loss counts remain available in `comparison.json` / raw files.

## Native review images

| View | 1080p | 720p |
|---|---|---|
| Sport overview | [image](native/SPORT_OVERVIEW-1920x1080.png) | [image](native/SPORT_OVERVIEW-1280x720.png) |
| League discovery | [image](native/SPORT_HUB_DISCOVERY-1920x1080.png) | [image](native/SPORT_HUB_DISCOVERY-1280x720.png) |
| Team | [image](native/SPORT_HUB_TEAM-1920x1080.png) | [image](native/SPORT_HUB_TEAM-1280x720.png) |
| Calendar | [image](native/SPORT_HUB_CALENDAR-1920x1080.png) | [image](native/SPORT_HUB_CALENDAR-1280x720.png) |
| Source opt-in | [image](native/SPORT_HUB_SOURCES-1920x1080.png) | [image](native/SPORT_HUB_SOURCES-1280x720.png) |
| Manual confirmation | [image](native/SPORT_HUB_MANUAL_CONFIRM-1920x1080.png) | [image](native/SPORT_HUB_MANUAL_CONFIRM-1280x720.png) |
| Exact playback return | [image](native/SPORT_HUB_PLAYBACK_RETURN-1920x1080.png) | [image](native/SPORT_HUB_PLAYBACK_RETURN-1280x720.png) |

Provenance records the timing/probe/overview and final-clipped APK identities separately.

The opt-in real-HTTP diagnostic is preserved separately in [tools/sporthub](../../../tools/sporthub/README.md), outside mandatory test discovery. CI executes deterministic fixtures and every native regression without spending third-party quotas.

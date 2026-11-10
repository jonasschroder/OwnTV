# Min TV 0.19 — Sport hub, My teams and reliable match-to-channel experience

Status: **Phase 1 stabilized in draft PR #40; data contracts in draft #41; integrated native hub/Watch Match implemented for review**. See [native implementation and exact remaining source blockers](v019-native-hub.md). All workstreams below remain **in scope and equally prioritized**. Missing permitted/reliable data is explicitly unavailable; this is not a complete release candidate.

Phase 1 stabilization: hero actions share their actual horizontal focus order; local recovery
stays available during loading; Back from Choose teams restores the selected Sport action.
Native tests, 1080p/720p comparisons and the 0.18 performance comparison are recorded in
[the phase 1 evidence](../evidence/sport-v019/README.md). Exact-head CI must pass before continuing implementation.

## Product intent

Use the upstream OwnTV Sports experience as a *reference for flows*, not a wholesale fork replacement.
Min TV already has profile-bound My teams (including Färjestad BK), SHL/HockeyAllsvenskan
fixtures, local EPG matching, source selection and cautious external-channel evidence.
Upstream OwnTV has richer scores/calendar/leagues/team pages and a match-specific Watch Live
experience based on an ESPN-led sports catalog. **Upstream does not include SHL in its
current ESPN SportsCatalog**; do not silently substitute NHL or assume ESPN lists SHL matches.

Reference upstream files:
- https://github.com/ahXN00/OwnTV/blob/main/app/src/main/java/tv/own/owntv/features/sports/SportsHubScreen.kt
- https://github.com/ahXN00/OwnTV/blob/main/app/src/main/java/tv/own/owntv/features/sports/SportsSettingsScreen.kt
- https://github.com/ahXN00/OwnTV_Core/blob/main/core/src/main/java/tv/own/owntv/core/sports/SportsCatalog.kt
- https://github.com/ahXN00/OwnTV_Core/blob/main/core/src/main/java/tv/own/owntv/core/sports/WatchMatcher.kt

## Scope decision — full Sport experience, no sport or league deferred (2026-11-08)

**Build the full experience now.** SHL, HockeyAllsvenskan (HA), European hockey (start with Liiga, DEL, Swiss National League; assess Czech Extraliga and ICEHL), NHL, international hockey **and non-hockey sports** (football including Allsvenskan/Premier League and other supported upstream leagues) are all part of the same v0.19 delivery. The list is **not** a ranked priority list. In particular, **NHL and other sports are NOT lower priority** than SHL or HA.

The same functional bar applies to each *verified supported* league: league discovery, search/select/follow teams, match calendar, live or final result only when data supports it, team/league detail, real standings where available, and Watch Live with clearly graded EPG/TV metadata evidence. New UI must support switching competitions in one common Sport hub.

**Engineering sequencing is not feature deprioritization.** Fix the red PR #40 focus/startup tests immediately. Then implement all workstreams in coordinated, reviewable PRs so there are no unbounded changes or silent regressions. SHL/HA should reuse our existing Swedish reader, NHL/international sports can reuse or adapt upstream ESPN integration with explicit compatibility review, and other European leagues need individually validated permitted data adapters. A league lacking a lawful/working feed must be *listed with honest unavailable/limited capability*, never fabricated as operational.

Preserve existing per-profile favorites including Färjestad, hockey + football club separation, all existing IPTV data, and performance baseline. No implicit Core pin or app-signing changes. **All target leagues and Watch Live are in scope before 0.19 is considered complete**; if a source cannot be legally/reliably integrated, document its exact blocker and ship the supported leagues only with explicit owner scope approval.

### Workstream A — league adapters and canonical data model (active 0.19 scope)

- Existing SHL and HA remain full-featured using the authorized source strategy and its fail-closed, robots, request budget and 403/429 logic.
- Investigate and integrate, after verifying access/terms and actual fixture coverage: Finnish Liiga, German DEL, Swiss National League, Czech Extraliga, and ICE Hockey League.
- Include NHL and international hockey via supported upstream provider integration, **plus football and other supported sports**. Do not assume ESPN supports SHL/HA.
- Normalize provider, sport, league, season, stage, team IDs, fixture IDs, kickoff UTC, status, score, verification time, source and data freshness. Make stable IDs across promotion/relegation and avoid hockey-vs-football collisions.
- Each adapter has opt-in rules, bounded refresh, offline/cached/failure states, honest availability, and regression tests.

### Workstream B — complete uniform Sport UX (active 0.19 scope)

- D-pad first: Sport overview, Find leagues/teams, My teams, league page, team page, match calendar with date navigation, verified scores, standings and detail pages.
- Display next fixture for Färjestad quickly without hardcoding it as the only supported favorite; work equally for other SHL/HA/european/NHL teams and football clubs.
- Maintain focus/Back/fullscreen-return behavior, accessibility and measured UI response on 720p/1080p Chromecast.

### Workstream C — Watch Live and match-to-channel evidence (active 0.19 scope)

- Combine match IDs, aliases, actual kick-off windows, EPG title/description, authorized channel metadata and user-confirmed manual choices. Reuse the existing conservative EPG safeguards for Swedish hockey.
- Distinguish exact match-specific EPG evidence, event-name evidence, likely broadcaster, remembered choice and unknown. TV4 Sport Live N is not proof of a specific SHL match.
- Never auto-play an uncertain channel, bypass profile permissions, or invent broadcaster mapping. Make channel choice functional with explicit evidence and restore focus after playback.

## Phase 1 — first-class personalized Sport entry (this PR)

- Keep existing `SportsPreferences` storage, profile owner verification, hidden-score setting
  and full `SportsTeamPicker` workflow. Never reset a followed Färjestad/SHL selection.
- Show an always-reachable **Choose teams** action directly on Sport's main hero, even if
  sports network data or channels have not arrived. Open the existing local-only picker.
- Put followed clubs' real fixtures into a distinct **My teams** match rail ahead of other
  today/upcoming rows; remove duplicated cards and the featured fixture.
- The fixture cards still open the existing match detail or a currently resolved/playable
  channel. This PR does not infer channel availability, change IPTV playback or add polling.
- Pure grouping tests cover followed teams in different competitions, hero suppression,
  duplicate fixtures and an unconfigured profile.

## Workstream B details — scores, day calendar, team and league pages (v0.19)

- Refactor `MatchcenterTabs` into clear **Matches / Calendar / My teams** routes,
  with a protected migration for existing saved `MatchcenterSection` values and focus.
- The calendar groups *actual supplied match dates* in Europe/Stockholm; date navigation
  must be functional on a D-pad. Do not create fixtures for missing dates, or show result
  snapshots as live scores.
- Introduce lightweight team pages (last verified result, next verified fixture,
  relevant competition) and standings only where the source actually supplies them.
  All views must show cached/fresh/unavailable state explicitly; a failed source must
  never be presented as confirmed current standings or a confirmed broadcaster.
- Enforce bounded cached state and cancellation on profile/section transitions;
  no blocking I/O from Compose focus handlers.

## Workstream A details — additional leagues and searchable teams (v0.19)

- Keep SHL + HockeyAllsvenskan under the existing verified Swedish-hockey source strategy.
- Include ESPN-backed supported competition providers (NHL, international hockey, Premier League,
  Allsvenskan and other verified supported sports), alongside independently validated European
  hockey adapters (Liiga, DEL, National League, Czech Extraliga, ICEHL), through explicit adapters in a separate module / appropriate own Core.
  Check upstream terms, endpoints, and live coverage before enabling a competition.
- Never hardcode a team or a league as live-supported because its slug exists in ESPN.
- Introduce stable per-provider team identities; keep existing `hockey:...` preference
  IDs unchanged or migrate with an explicit tested mapping. Do not merge hockey and
  football clubs sharing a name (e.g. AIK).
- Search and suggestions should work without loading all leagues simultaneously; cache
  by league, bound requests, backoff on 403/429, and allow safe offline access.

## Workstream C details — match → Watch live/channel evidence (v0.19)

- Show **Verified in EPG**, **Likely broadcaster**, **Remembered manual choice** and
  **No verified channel** distinctly. The real code currently represents uncertainty
  through `HockeyChannelAvailability` and `hockeyCardBroadcast`; reuse it.
- Search only within currently accessible, profile-authorized channels. On every Play,
  re-resolve rights, source membership and stream state. One match may map to multiple
  candidate channels, so support clear manual source selection.
- A general `TV4 Sport Live N` channel name is not match-specific broadcast proof.
  A tentative fixture match must not be auto-labelled as confirmed.
- A long-press may remember a choice only when explicitly chosen by user. No speculative
  playback/auto-fallback loops, no arbitrary polling of provider sites.
- Preserve EPG guide, fullscreen/Back focus restoration and last-played channel behavior.

## Hard constraints

- No signing/key/package identifier changes; Stable and QA remain separate.
- No changes to pinned OwnTV_Core version by accident. A future Core upgrade needs
  isolated compatibility work (public APIs, overlays, build+license audit, local QA and
  profile migration tests).
- Keep Sports disabled/opt-in and network demand scoped to visible sections/current
  profile. Respect explicit robots/provider policy, 403/429 backoff and local user data.
- No fabricated broadcasts, standings, times, scores or successful streams.

## QA and acceptance gates

1. Unit tests for each pure feed/fixture/profile mapping and schedule-boundary condition.
2. Device focus tests: **Sport → Choose teams → select Färjestad → Back → Sport**;
   rapid D-pad/long-press; tab switching and returning from playback. Test at 1080p/720p.
3. Recheck that imported profiles, saved preferences, favorites, source visibility and
   signed upgrades retain state.
4. Measure old vs new navigation medians, p95 and long-list jank on 12k synthetic data,
   then verify with a real Chromecast and actual authorized IPTV data. Avoid regressions
   against the already optimized 0.18 baseline.
5. No auto merge, production publish or release claim from this roadmap. Owner signs off on
   the QA trial before Stable promotion.

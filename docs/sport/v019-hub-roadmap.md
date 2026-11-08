# Min TV 0.19 — Sport hub, My teams and reliable match-to-channel experience

Status: **Phase 1 implementation in a draft PR**. The remaining phases below are explicitly **not implemented**. This is not a release candidate.

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

## Phase 2 — scores, day calendar, team and league pages (follow-up)

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

## Phase 3 — extra supported leagues and searchable teams (follow-up)

- Keep SHL + HockeyAllsvenskan under the existing verified Swedish-hockey source strategy.
- Evaluate optional ESPN-backed supported competition providers (NHL, Premier League,
  Allsvenskan) through an explicit adapter in a separate module / appropriate own Core.
  Check upstream terms, endpoints, and live coverage before enabling a competition.
- Never hardcode a team or a league as live-supported because its slug exists in ESPN.
- Introduce stable per-provider team identities; keep existing `hockey:...` preference
  IDs unchanged or migrate with an explicit tested mapping. Do not merge hockey and
  football clubs sharing a name (e.g. AIK).
- Search and suggestions should work without loading all leagues simultaneously; cache
  by league, bound requests, backoff on 403/429, and allow safe offline access.

## Phase 4 — match → Watch live/channel evidence (follow-up)

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

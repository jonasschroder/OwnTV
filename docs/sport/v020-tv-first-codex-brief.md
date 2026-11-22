# Min TV 0.20 — SPORT FIRST: full TV-first Sport UX + playback reliability overhaul

**Status:** Owner-requested implementation brief, NOT completed product code.  
**Source:** `main` at `2ec1bb7dbc250e82efba9ab3f5e28256df91bfad` (Sport 0.19 merged and signed as QA build 1000028).  
**Product owner feedback from physical Chromecast:** **"Sport is not working particularly well; it is easier to go into TV and manually choose sports channels than to use the Sport tab."** Treat this as the actual failure signal; don't mark 0.19 as successful simply because CI passed.

## Your mission — BUILD A COMPLETE FIX, not a plan

Make **Sport the fastest and most pleasant way to watch sport** on Chromecast with Google TV 4K. Rework information architecture, entry page, active sports guide, matches, channel selection, data loading, D-pad navigation, visual design, test evidence and implementation quality. The owner wants a **substantial end-to-end refactor**. Use the existing Sport 0.19 feature work and upstream OwnTV UX as references, but do not simply reskin it, copy code wholesale, ship a mock UI, or hide the old problems one level deeper.

**Core product principle: SPORT IS FOR WATCHING FIRST; exploring leagues, results and calendars second.** A user can always watch an authorized sports channel with no trusted fixture data, no ESPN, no provider opt-in, and no EPG. Live match metadata is an optional enhancement layered on top of working live TV, NOT a prerequisite for playback.

### Reality to audit in current main (do not rely solely on this brief)

- `app/src/main/java/tv/own/owntv/features/home/NativeSportDashboard.kt`: Sport opens to a large hero; match rails and an authorized sports-channel rail appear later; channels are capped at 48. `onExplore` opens a *separate* public hub. Inspect how a real user reaches watchable TV and how focus starts.
- `app/src/main/java/tv/own/owntv/features/home/MinTvCompanions.kt`: old SHL/HA collector, authorized `liveVm.homeSportsChannels("", favorites, profileId)`, EPG, match availability and channel picker. Important: `available`/local guide logic depends on selected Swedish teams, some source flags start disabled, and the dashboard has distinct loading paths.
- `app/src/main/java/tv/own/owntv/features/sporthub/SportHubScreen.kt`: Discover → Sports / Sources → League → Team/Calendar → Match → Watch; data fetch is demand-driven, provider consent profile-scoped, EPG confidence graded. `DISCOVER` mostly starts from league cards, not something watchable now; Calendar without a context can default to SHL. Multiple back stacks may add friction. Inspect actual layout, focus order and the number of OK presses.
- `app/src/main/java/tv/own/owntv/features/sporthub/NativeSportHubPage.kt`: repeated generic three-column tiles + 4-item toolbar. Verify whether this provides appropriate 10-foot TV hierarchy; do not assume a uniform tile grid is optimal for watching.
- `app/src/main/java/tv/own/owntv/features/sporthub/HubSourceReader.kt`: public providers opt-in, daily SportsEvents24 quotas, strict rate/denial; expensive network must not block local playback or start on focus. The sports catalogue is finite; some leagues/standings/scores are unavailable.
- `app/src/main/java/tv/own/owntv/features/sporthub/HubWatchEvidence.kt`, `HockeyEpgGuide.kt`, `ShlBroadcasts.kt`, `ShlChannelOverrides.kt`, `HubSourceReader.kt`: the event/EPG/manual/likely/unknown distinction already exists. Preserve its trustworthy semantics; never infer a particular TV4 Sport Live N from a generic channel label.
- Compare the **actual TV route** and `Sports` category/search/source selection in the current TV UI against Sport. Identify and reuse the fastest proven channel selection/playback path, not a second divergent player stack.
- Upstream reference: `ahXN00/OwnTV_Core` `SportOnChannels.kt`, `WatchMatcher.kt`, `WatchReader.kt`; compare concepts, not necessarily dependencies. Swedish hockey/HA cannot be silently replaced by ESPN.

The above are investigation starting points, not claims that every user-visible issue has been reproduced. Record real UI behavior, focus journeys, data failures and currently installed QA version with screenshots/tests before changing architecture.

## Required resulting experience

### New SPORT landing: one obvious watchable screen

Prioritize a 10-foot, remote-first hierarchy (adapt to data availability):

1. **Live now / På sportkanalerna nu** — immediately populated from the **authorized local IPTV channel library and stored EPG**, independent of any match API. Show useful cards: channel logo/name, programme title and start/end/progress only if EPG actually supplies it; a clear primary **Spela kanal** action. Do not make the user first open leagues or opt into SportsEvents24. A sports channel with no EPG remains playable with its actual name, never a made-up event.
2. **Next up / Börjar snart** — programmes starting soon from EPG and actual upcoming followed-team matches where known. Distinguish *scheduled match* vs *on-channel scheduled programme* and confirmed vs possible mappings. Do not label a fixture Live from kickoff time alone.
3. **My teams / Mina lag** — e.g. Färjestad BK; immediate team-specific next real game and a direct path to channels if broadcasting evidence exists. Preserve SHL, HockeyAllsvenskan and other saved follows. Show a friendly quick "Välj lag" if none.
4. **Sportkanaler** — always accessible near the top, with real category/list browsing, searchable channels, favorites/recent channels, source/quality selection using the same existing TV logic. An explicit fallback if no match data. Do not artificially truncate at 48 with no visible "Visa alla".
5. **Discover / Ligor, Kalender, Resultat** — NHL, SHL, HA, Liiga, DEL, Swiss National League, CHL, Extraliga, ICEHL, Allsvenskan, Premier League, NFL and other verified supported sports. Keep all genuinely supported leagues equally available. They must not push watching behind four clicks.

Suggested at-a-glance large-screen shape (you may improve based on 720p and 1080p screenshots):

```
SPORT                                                   [Mina lag] [Kalender] [Ligor]
┌─────────────────────────────────────────────────────────────────────────────┐
│ LIVE NU / SPORT PÅ TV                                                         │
│ [CHANNEL LOGO] TV4 Sport Live 3      SHL: ... (only if known from EPG)          │
│ [Spela kanal]  [Andra sportkanaler]  [Se matchinfo] (only if grounded)         │
└─────────────────────────────────────────────────────────────────────────────┘
  Sportkanaler: [TV4 Sport Live 1] [Live 2] [Live 3] [other real channels…]
  Mina lag:     [Färjestad — next fixture / verified stream] [followed clubs…]
  Börjar snart: [EPG programmes + source-labelled actual matches…]
  Ligor & kalender: [SHL] [HockeyAllsvenskan] [NHL] [Liiga] [Football] ...
```

Treat this as information hierarchy, not pixel-perfect implementation. Use existing Min TV visual language, better spacing, typography, horizontal hierarchy, strong selected-focus state and channel logos. Avoid giant empty hero blocks, generic cards with repeated text, unexplained placeholders, technical status walls, scroll traps and excessive settings buttons. The first screen should look compelling even with **zero permitted external sports APIs**.

### First-class sports channel browsing (critical)

- Bring the real sports TV path **inside Sport**: selected profile's authorized sources, channel categories, Swedish channel grouping, favorites, recently viewed, duplicates/quality variants, fast source picker, search and the same playback/MPV fallback as TV.
- Build reusable channel selector/service/Composable shared with TV where suitable. Do not duplicate parsing, channel eligibility or player pipelines. Protect hidden channels, source membership, kids profiles, expired source revisions and restored focus.
- Show **Now/Next** programmes from *stored* guide when available. Source label, local time and optional progress bar; do not present a programme as being on the air if stale or outside its actual interval.
- Use EPG sport genre/title/team evidence where safe to group live/soon broadcasts. Consider upstream `SportOnChannels`; exact matching must be conservative. Sport category detection must not accidentally turn a generic entertainment programme or TV4 Play streaming label into a validated match.
- A single selection of a real channel should **play the linear channel directly**. An uncertain match-to-channel claim may require confirmation, but selecting a plain channel **must not** trigger a warning about unverified fixture identity. This is a crucial distinction.
- Always show **Öppna alla sportkanaler** or equivalent when a channel subset/limit is used. Prefer deterministic sorting and stable cards while remote focus is active.

### Match → channel journey (critical)

- On a fixture card, display at most one obvious primary action: **Se matchen** if a verified match-specific EPG/event or explicit user choice exists; otherwise **Hitta sändning** and offer ranked authorized candidate channels with plain-language uncertainty, plus direct **Alla sportkanaler** escape hatch.
- Order evidence: verified match-specific EPG; verified matching event channel; explicit per-fixture manual selection; likely broadcaster (clearly unverified); unknown. Preserve established code-level confidence and expiry rules.
- Do **not** equate a generic `TV4 Sport Live 1/2/3` to an individual SHL/HA fixture. Do not infer watch availability from cached results, broadcaster brand, potential rights owner or the fact that one recently selected channel showed another match.
- Revalidate channel, source, profile and current fixture on Play. Keep candidate source/quality options where the user can see them, and allow immediate fallback to plain sport-channel list if a proposed match lookup fails or external provider unavailable.
- Manual selection should be understandable: **"Det här kanalvalet gäller den här matchen"** (if saved per fixture), time-bounded, never promoted to EPG-confirmed. Display remembered channel without false confirmation.

### UX/navigation and structure

- Make one coherent Sport entry rather than "Sport overview" + "Explore hub" + another team picker with different navigation metaphors. Consolidate through shared routes/state when needed. Reduce nested navigation and duplicate content rather than only renaming buttons.
- Make the selected remote action logical when entering Sport (first **live watchable channel** / **Live nu** action if available). Maintain exactly one visible focus ring, no double-focus or jump on guide refresh, loading completion, clock tick or opt-in change.
- Back after playback returns to the **exact Sport card** and scroll offset, not default hero/TV. Back from nested page returns to its parent without losing filters or favorite team. Escape from modal search / virtual keyboard / manual confirm always works.
- A genuine `Mina lag` landing should surface followed matches from *all* leagues where data has actually been loaded, and meaningful placeholders/links for others; profile with only NHL teams must not show unrelated Färjestad as the featured fixture.
- Include simple top-level sport filters (Hockey, Fotboll, Övrigt) that are easy with D-pad. League drill-down is optional and never gates playable sports channels.
- Transparent sources: if a public sports-data provider needs explicit opt-in, explain it **only when relevant to that league**, with a simple enable path. Opt-in must not obstruct local TV channels or SHL/HA EPG. Show cached/partial data and genuine unavailability without exposing raw HTTP jargon to non-technical users.
- Keep Swedish UI copy natural. Correct locale, Europe/Stockholm match day boundaries, TV-safe font sizes and contrast. Account for font scaling, 720p/1080p and system reduced-motion settings as applicable.

## Performance and data correctness

- Instrument and compare **Sport → playable channel** and **TV → playable sports channel** on the SAME populated profile and same source library; report action counts, time-to-first-ready-focus, time-to-player, median/p95 and exact environment. Do not invent numbers. Main KPI: **Sport becomes at least as convenient as TV for a normal live sports channel**.
- Remote interaction targets: after entering Sport, **at most 2 additional OK presses** to play a prominent current sports channel (with D-pad moves recorded separately); for a verified featured match, similarly no more than 2 additional OK presses. The plain-channel route must never require external sports-provider approval. Avoid unnecessary modal confirmation.
- Measure on 12k+ channel synthetic library and typical real device profile. Don't introduce channel scans on composition, repeated FTS per focus, a synchronous database query on the UI thread or a whole-league network fetch on every entry. Cache/snapshot local sports guide and channel rows; make local channels visible immediately, then progressively enhance.
- Prevent re-ranking while the user is focused on a channel. Coroutines cancellation on profile/source changes; bounded EPG scans, throttles/caches/persistence, 403/429 and robots compliance. No boot-time polling, no periodic HTTP refresh of all leagues.
- Explicit current season/source binding; no old results as live, no fabricated scores/standings, no stale broadcaster claims. All provider rights and beta limits in `docs/sport/v019-data-sources.md` remain relevant.
- Preserve old customizations, profiles and `hockey:...` follow IDs; test migration of additional 0.19 hub preferences and **backup/export/import** if compatible with the existing `.own` format. Never reset a profile because a new field is malformed.

## Delivery work packages — implement code and ship reviewable evidence

**M0 — Instrument and audit (short):** Reproduce owner complaint in QA 0.19, capture screen navigation diagrams and press-counts for (a) selecting TV4 Sport Live N via TV, (b) selecting it through Sport, (c) watching a known Färjestad match, (d) absent/blocked sports data. Check active code routes and existing tests. Record benchmark and screenshot evidence; no claim of physical device reproduction without device access.

**M1 — Make live Sport TV-first:** Extract reusable authorized sport channel browser from existing TV / match picker; live-now channel and EPG rails, instant play and "Alla sportkanaler"; state/focus/UX. This work must be genuinely usable without external match metadata.

**M2 — Unified Sport experience:** Rebuild landing, discovery, calendar, My teams and league/team/match navigation into a coherent D-pad-first design. Avoid unnecessary duplicated UI and dead ends. Make usable non-hockey sports equally accessible.

**M3 — Confident Watch Match + fallback:** Ranked candidates, correct evidence labels, manual choice, one-tap plain-channel fallback, no false verified broadcasters, robust playback return and profile/source checks.

**M4 — Finish and validate:** Full QA/UX pass across 720p+1080p, larger text, 12k+ playlist, empty EPG, no internet, 403/429, opt-in/out, background/resume, profile swap, stale cached results, 3 simultaneous SHL games on TV4 Sport Live 1–3, match cancelled/rescheduled, channel source revoked, MPV/exo playback, Back/relaunch. Fix every regression rather than adjusting tests to silence them. Show improved before/after screenshots and report exact metrics against 0.19.

Commit real Kotlin/Compose code and tests; maintain a running `docs/sport/v020-implementation-report.md` with root causes, before/after remote press journeys, performance numbers, screenshots, tested coverage, unresolved blockers and manual QA checklist. Include Swedish release notes and a prioritized but **implemented** list of user-visible fixes. Don't stop at one happy-path screen or a plan. Break into milestones/PRs for review where sensible; align integration in a testable branch and run all required checks.

### Tests that must be added or extended

1. **Watchability without APIs**: with every public sports provider disabled, show sports channels and directly play a real authorized IPTV channel from Sport; no league/setup prompt.
2. **Fast path parity**: compare TV vs Sport actions, assert the visible Sport primary channel is focusable/playable and the action count target; log actual remote transitions in instrumented tests.
3. **Three same-time SHL fixtures**: EPG explicitly maps each to different TV4 Sport Live 1/2/3; only exact match shown as confirmed; generic channel names never enough.
4. **No guide**: sports list works, programme placeholders are honest, no false live indicator and no dead-end matcher screen.
5. **Non-Swedish profile**: NHL-only / football-only follows show no unrelated SHL hero, can discover data without causing unsolicited network calls.
6. **Screen-state durability**: Back from playback, refresh, navigation, channel source revocation, profile changes, release upgrade, preference migration and export/import behavior.
7. **Focus/instrumentation**: 720p/1080p, font scaling, rapid D-pad, long-press source selection, virtual keyboard, empty/long lists and stale guide updates.
8. **CI**: actual connected Android device tests, unit tests, full required i18n, signing-upgrade, startup, performance and distribution guards. Never skip tests or rewrite tests to remove legitimate UX assertions.

## Hard safety/release rules

- **No automatic merge, signing, distribution or Stable changes.** No changes to package IDs, signing key aliases, production database, update manifests, credential storage or user IPTV streams.
- Stable must remain independently installable. QA uses `se.jonasschroder.mintv.qa`. Future approved QA version would be `0.20.0-beta.1` or owner-chosen newer; do not silently overwrite installed 0.19.
- If you need upstream `OwnTV_Core` changes, audit API pin, license/compatibility and migrations in an isolated review. Never unilaterally bump the dependency.
- Don't treat source metadata without proper permissions as usable; don't scrape around 403/robots/paywalls. Do not store user credentials or playlist URLs in screenshots/logs.
- Existing SHL, HA, European hockey, NHL, football, EPG confidence and profile safety must be preserved even while refactoring. All supported sports should be accessible, not sacrificed to the Swedish default.
- Do not assert a real stream plays, an actual TV channel carries a specific match, or a Chromecast has been tested without direct evidence.

## Definition of done — owner outcome

The owner opens **Sport** on his Chromecast and, WITHOUT needing any sports API to work, immediately sees relevant actual sports channels and can start watching as quickly as through **TV**. If a real Färjestad/other followed match is known, it is highlighted with truthful details and a fast path to the right channel *only when verified*. Exploring leagues and dates remains polished and fully usable, but never blocks watching.

**Start coding now.** The expected handoff is a substantial working refactor, test artifacts and reviewable PRs, not another proposal. If tooling lacks access to a real Chromecast/IPTV subscription, execute emulator tests on synthetic authorized fixtures and state precisely which real-device checks await the owner.

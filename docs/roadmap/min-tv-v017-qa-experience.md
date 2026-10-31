# Min TV Test v0.17 — Everyday TV Experience (Codex implementation brief)
**Status:** owner-approved next QA development track, 31 October 2026. **Branch:** `feat/v017-qa-experience` based on immutable v0.16 merged main `b94e98cbf28b61f75dcc804525cd128fd9683120`.

## Context and hard boundaries
- The owner has installed and visually accepted **Min TV QA 0.16.0-beta.1, build 1000023** on a physical Chromecast with Google TV 4K; the overall design and responsiveness feel good. **Do not gratuitously redesign the accepted navigation or break working playback.**
- Stable **v0.16.0 signing/publication is a separate, parallel process** based on the above exact main SHA. **Do not merge anything to main or modify its release workflows while Stable is being promoted**. Never sign, distribute, install, publish or change production without separate approval.
- Keep the five native destinations Hem / TV / Sport / Film & serier / Profil/Inställningar, Swedish-first labels, D-pad navigation, a predictable Back stack, easy access to version/update, and existing app shortcuts (only when installed).
- Stable app ID `se.jonasschroder.mintv`, QA app ID `se.jonasschroder.mintv.qa`; respect current protected signing, exact source SHA, trusted update metadata, fixed signer, versionCode increase, and separated user data.
- Preserve Xtream/M3U/Stalker profiles, favorite channels, EPG, selected stream/quality, history, subtitles, live playback, settings, and films/series behavior. Never log or commit provider playlist URLs, tokens, credentials, or personal viewing data. Do not replace the player/Core pin or migrate storage without an explicit technical reason and tests.
- No paid APIs, hosting commitments, unapproved scraping, fake broadcast metadata, speculative autostart of streams, or battery/network-heavy background polling.

## Goal for 0.17
Take the already approved 0.16 foundation to a noticeably more useful **daily watching experience**, not a cosmetic release or another stack of tiny hotfixes. Optimize actual TV discovery, switching, returning and Sport relevance with testable changes. First audit *what already exists*, then improve it rather than duplicating controls.

## Workstream A — Fast, dependable live TV interaction (P0)
1. Review real on-device flows: Home recent channel → playback; TV five-column grid → playback; within-player next/previous logical channel (if already supported); Back → exact previously focused card; source/quality picker; EPG changes while navigating.
2. Prioritize identifiable defects and friction: slow or double player entry, lost focus, duplicate channel representations, scroll jumps, wrong source after returning, stale programme labels, channel zapping requiring too many D-pad actions. Keep short OK immediate and long OK source choice.
3. Improve the existing quick-switch / previous-channel UX **only if code inspection and tests justify it**. A compact, legible now/next overlay may help but must not obstruct picture or invent EPG. Keep controls discoverable with a remote; no new menu tiers, focus traps or accidental channel tune on mere focus.
4. Preserve national vs regional SVT choices including SVT Värmland; never silently substitute national for regional; report provider stream failures as such. Keep quality duplicates grouped under the logical channel.

## Workstream B — More useful Home without a redesign (P1)
1. Preserve the owner-approved compact 0.16 Home layout and existing recent-channel hero; polish information hierarchy and remote actions, not wholesale layout.
2. Make "continue watching / last watched" genuinely actionable based on permitted existing profile history. Indicate current/next programme when fresh EPG exists; otherwise truthful empty/stale state. Avoid showing stale EPG progress.
3. Show next relevant SHL/Färjestad fixture contextually when available, with accurate start time and status. Do not show unverified broadcaster as confirmed and do not bury the last-watched action.
4. Preserve existing app shortcuts (OhnePixel, SVT Play, Telenor Stream, etc.) only if configured/installed, plus manual EPG refresh and visible version. Avoid blank rows and expensive recomposition or unnecessary data subscriptions.

## Workstream C — Sport that gets you to the right playable channel (P1)
1. Audit existing SHL fixture → exact local EPG → permitted free broadcaster candidate → IPTV logical source → actual player handoff, and the existing manual source picker. The physical 0.15 QA service **TV4 Sport Live 3** played with picture and audio via "Se matchen"; that **did not prove** the 31 October match broadcaster.
2. Keep the distinction `Bekräftad kanal`, `Trolig kanal`, `Kanal okänd`, unavailable in current IPTV profile, and multiple options. Use free documented sources with expiry and bounded cache, never hardcode an assumed TV4 Sport Live number.
3. Improve the highest-friction case observed by tests, especially actionable manual fallback and preserving Sport focus after Back. Do not create duplicate fixture cards or unrelated league standings.

## Workstream D — UX/performance/quality gates (P0 across all changes)
- Capture and compare **actual native MainActivity** 1080p and 720p screenshots (with explicitly labeled synthetic content where real IPTV is unavailable). Include Home, TV, Sport, fullscreen return, Settings and any new overlay. Focus on legibility at 2–3m and selected vs focused states.
- Establish repeatable before/after baselines on the **same seeded dataset, device/emulator configuration and journey**, using existing harness. Report median/p95, CPU, PSS, jank/frame samples, relevant EPG/DB calls and observed double player handoffs. No performance percentage claims without evidence. Distinguish synthetic emulator evidence from physical Chromecast.
- Add or extend JVM/instrumentation tests for rapid D-pad, short/long OK, Back, profile switching, late EPG, regional channels and release identity, without weakening existing mandatory suites.
- No feature regressions, permissions changes or secret exposure. Run required Android/i18n/performance CI against final PR head.

## Execution / deliverables
1. **Audit first, implement next.** Read existing `AGENTS.md`, app User Guide, merged #36 and earlier #29–35, current Kotlin/Compose owner modules, tests and actual v0.16 evidence. Identify **three highest-impact confirmed gaps** from the workstreams, and implement them in a cohesive QA milestone rather than planning forever. If a proposed behavior already exists and works, improve its discoverability/performance only if evidence warrants it.
2. Make code changes only in `feat/v017-qa-experience` (or a clearly named child feature branch from it). **Do not merge to main.** Open **one draft implementation PR** for code review when ready, and link this brief.
3. Summarize precisely: user-visible changes, what was intentionally kept, screenshots/evidence links, performance before/after, all CI results, limitations and manual physical Chromecast QA checklist.
4. Keep existing signing/update/QA-vs-Stable safeguards. **Do not start protected signing, publication, main merges or user-device installation.** A QA 0.17 build can be considered only after Stable 0.16 is published, PR review is approved, and the new immutable merged main SHA is green. Request approval at that point.

## Acceptance tests on physical Chromecast (owner will perform later)
- Home → last watched channel is fast; Back preserves logical focus and scroll.
- Five-column TV navigation, short OK playback, long OK source selection, stable favourite/source preferences.
- Next/previous channel (where supported) does not accidentally double-start player or jump to unrelated channel.
- Fresh vs missing/stale EPG is truthful; manual refresh works.
- Sport shows next Färjestad match and broadcaster confidence honestly; manual channel fallback; playback + Back returns correctly.
- SVT1 Värmland is selectable without hiding national/regional alternatives.
- Film & serier, external app shortcuts, profiles, subtitles, update entry and existing settings continue to work.
- Test and Stable remain independently installed; no profile/content wiped.

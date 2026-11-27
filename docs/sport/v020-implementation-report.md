# Min TV 0.20 — implementation report (running)

Brief: [v020-tv-first-codex-brief.md](v020-tv-first-codex-brief.md). Base: PR #43 branch.

## M1 — Live Sport TV-first (this PR)

### Root causes found by code inspection
1. **Plain sports channels were the last row.** `NativeSportDashboard` put the authorized channel rail
   below the hero, My teams, followed, today, upcoming and attribution rows. Entry focus was the
   match hero, so a plain channel needed up to ~6 D-pad moves before OK.
2. **No programme info on Sport channel cards.** Cards showed only name + "OK plays", so the user had
   to open TV to see what was actually on.
3. **Hidden 48 cap with no "show all".** The rail was silently truncated.
4. **Plain picks were validated against the next match (bug).** In the manual picker with no fixture
   selected, `pick()` called `play(channel, rows)` whose `expected` defaulted to `gameToMatch`. A
   non-hockey channel then returned silently (nothing played), and a hockey channel was checked
   against an unrelated fixture. Fixed: fixture-free picks pass `expected = null`.

### Changes
- New first row **Sport på TV nu**: the existing logical, profile-scoped sports channels
  (`homeSportsChannels` → `logicalHockeyChannels`), shown as soon as the local read finishes and
  independent of any match API or provider opt-in.
- Cards reuse `HomeNowChannelCard` with **stored Now/Next** from the same bounded owner as Home
  (`rememberHomeDashboardGuide`: max 16 keys, stored EPG only, no provider HTTP, 5 s clock, honest
  `at()` interval checks). Missing guide → "Ingen programinformation", still playable.
- **One OK plays the linear channel** (`actions.channel`, existing playback path, `expected = null`).
- **Alla sportkanaler** card ends the rail: fixture-free category/search picker; Back returns straight
  to the Sport card.
- Removed the duplicate bottom channel rail. Hero, My teams and fixture rails unchanged below.
- The row is omitted when no sports channels are identified, so empty profiles keep the existing
  recovery entry (`sport-empty-action`).

### Remote press journeys (from code, synthetic; not measured on a Chromecast)
| Journey | 0.19 | M1 |
| --- | --- | --- |
| Enter Sport → play first sports channel | 1 OK + 1–6 D-pad (depends on fixture rows) | **1 OK, 0 D-pad** |
| Enter Sport → play TV4 Sport Live 3 | 1 OK + 4–9 D-pad | **1 OK + 3 Right** |
| Enter Sport → any sports channel via category | 2+ OK, tied to next match; non-hockey could silently fail | 2+ OK, fixture-free, always plays |

### Tests
- `NativeV011PresentationTest.sportLiveChannelPlaysWithOneOkWithoutMatchData` (new): entry focus on
  the first channel, one OK plays, honest no-guide text, "Alla sportkanaler" opens browse, no
  duplicate rail.
- Updated entry/row navigation expectations in `NativeV011PresentationTest`, and the empty-profile
  leak check in `PerformanceJourneyTest` now targets the new card tag.
- Local: i18n number-locale, text-overflow and literal-inventory checks pass. Kotlin/Android build and
  instrumentation run in GitHub Actions only (no Android SDK in the authoring environment).

### Not done yet (M2–M4)
Unified hub/calendar/My teams, sport filters, ranked Watch Match fallback, performance before/after on
the 12k synthetic library, 720p/1080p screenshots review, Swedish release notes for a QA proposal.

### Manual Chromecast checklist (owner)
- [ ] Sport opens with focus on the first sports channel; OK starts it.
- [ ] Now/Next on cards matches TV; stale/missing guide shows no false programme.
- [ ] Back from playback returns to the same channel card.
- [ ] "Alla sportkanaler" → category → channel plays; Back ×1 from the category list returns to Sport.
- [ ] Hero "Se matchen" / manual picker for a match still behave as in 0.19.

# Sport 0.19 — integrated native hub and Watch Match

Depends on draft [#40](https://github.com/jonasschroder/OwnTV/pull/40) and
[#41](https://github.com/jonasschroder/OwnTV/pull/41), exact parent
`4a08a3f28df1ab8aab14a37566bbbfb402c20b05`. No merge or release authorization is implied.

## Implemented

- Native league/sport discovery, league and team pages, Stockholm-date calendar, real reported
  results, My teams, local team filtering and explicit source availability. One common flow
  serves Swedish hockey and the reviewed international leagues; no HTML or mock production feed.
- Existing SHL/HA readers and canonical `hockey:...` preferences remain. Already-read public
  fixtures join the same chronological My teams rail. Followed clubs remain directly reachable;
  a profile following only NHL/football gets a contextual hero, not an unrelated SHL setup prompt.
  Cold startup shows saved clubs immediately; public data loads on an explicit league/team entry.
- Per-profile public-provider opt-in and followed teams extend the existing private preference
  codec. Owner creation time prevents reused database IDs inheriting selections. Atomic changes
  preserve other pickers' fields; malformed new fields do not erase existing favourites.
- Stable native focus keys, bounded saved route history and per-profile focus/scroll state.
  Back, profile switching, loading/empty states, fast repeats and removal of a focused item
  use the existing navigation owner. No additional persistent global menu or decoder.
- Watch Match ranks **GUIDE → EVENT → remembered MANUAL → LIKELY → UNKNOWN**. Swedish EPG
  matching delegates to the existing parser; other reviewed leagues require both full team
  names, competition context and a consistent actual programme/kickoff interval. Replay,
  ambiguous, expired and streaming-brand matches do not become confirmed linear assignments.
- Existing broadcast adapters expose cache-only lookup: their opt-in, blocked state, terms hash,
  fixture identity and expiry still apply. Entering Watch Match never triggers another external
  broadcast scan. Probable metadata cannot directly start a fixture, including the old Home/Sport
  shortcuts. Uncertain/manual selection opens an opaque, focused confirmation dialog.
- Every Play rechecks the current profile lifetime, fixture, channel/source identity and current
  authorization. GUIDE/EVENT evidence is revalidated. The normal player/fallback path is reused;
  playback return restores the selected Watch Match item.
- HTTP parsing and bounded EPG scans run away from the UI thread; Swedish aliases are built once
  per scan. No fetch on card focus, all-league polling, artwork downloads, new permission,
  dependency, Core pin, signing or release-workflow change.

## Actual source coverage and limits

See [the source contracts](v019-data-sources.md). NHL and other sports were investigated equally,
not silently deferred. SportsEvents24 needs no personal key; an additional real NFL response
contained **15 fixtures/results**. Its NBA response contained **50 records without timezone
corroboration** and no independently verified start times; NBA remains explicitly unavailable.
The GB cricket tournament request returned an empty list; this does not establish global absence.
Other unverified sport/league bindings remain unavailable, not fabricated.

Premier League standings were checked against the real API on **9 November 2026, 13:01 UTC**:
`getbltable/pl/2026` returned 20 rows / 152 team appearances, while the accompanying 380-fixture
response marked only 71 games finished and left 29 overdue unfinished. These do not reconcile
(71 × 2 = 142). Do not present that table as verified current standings. Existing SHL/HA tables
retain their original source; no table is manufactured from an incomplete fixture list.

Reviewed season bindings are explicit: Premier League does not accidentally fetch 2027/28 in
January 2027. Calendar-year Allsvenskan and split-season bindings expire for re-review rather
than silently adopting a new season. SportsEvents24 team IDs are explicitly name-derived;
provider renaming remains an identity limitation. Cached schedules/results are not live scores.

## Review and release limitations

- Full Sport 0.19 is **not complete** while permitted/reliable feeds are missing. International
  hockey and the remaining sports still need reviewed bindings; ESPN automated-use permission,
  alternative credentials or usable public feeds must be established. No paid service/hosting added.
- Beta production redistribution rights and ODbL obligations need owner review before distribution.
  Neither new public API provides verified Swedish linear broadcast assignments.
- Existing legacy manual records are retained. New Watch Match choices are bound to profile
  lifetime; unbound legacy records are not silently reassigned to a possibly reused profile ID.
- New Sport preferences are private local data. No `.own` export/restore support was added or
  validated for them; existing backup code is unchanged. In-place persistence is tested separately.
- Native evidence uses a disposable emulator, including loopback synthetic playback. It does not
  prove physical Chromecast decoding, audio, actual-provider EPG aliases or real match assignments.
- Local builds use the existing `0.14.0-dev` fixture stamp; this is not an official QA version.
  Package/signing/version policy and installed owner apps remain unchanged.

## Physical QA after separate owner approval

1. Verify QA signer/version compatibility; never install this local debug build over owner QA.
2. Check existing profiles, sources, Färjestad choices, favourites and hidden-score settings.
3. Open Sport, league, team, Calendar and My teams using only D-pad/OK/Back; repeat quickly,
   cross midnight/resume and return from playback at 1080p/720p.
4. Enable only the intended public provider in one profile; confirm another profile stays unchanged.
5. Follow an actual NHL/EPL/Swedish club; confirm its real source-labelled fixtures and cold entry.
6. Check confirmed GUIDE/EVENT separately from probable/unknown/manual. A TV4 Sport Live number
   alone must never verify a match; TV4 Play must never become an exact linear assignment.
7. Cancel manual choice, confirm another, play with picture/audio, then Back to the exact channel.
   Revoke a source/change profile and verify stale items cannot play. Keep working MPV fallback.
8. Confirm unavailable standings, quota/cooldown and incomplete coverage are honest. Do not call
   this a complete release until the source gaps above have an explicit owner decision.

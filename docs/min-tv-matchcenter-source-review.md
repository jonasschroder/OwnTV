# Matchcenter: reliability and source review, 14 October 2026

This focused follow-up belongs to PR #9. The navigation/Settings redesign remains
a proposal for a later approved PR. Packages, installed data, player, pinned Core,
permanent signing and update infrastructure are unchanged.

## Bugs and bounded work

- A UI publication exception could escape the sports coroutine and crash the app.
  It now ends loading with a classified PUBLICATION/CACHE failure, retaining valid
  cached data and sanitized diagnostic frames. A broken diagnostic writer cannot
  turn that failure into another crash. Cancellation still propagates.
- The complete sports deadline now includes publication, not only retrieval.
  Slow publication cannot complete after the screen's request deadline.
- The visible Table tab can hydrate a verified same-season cache with the source
  disabled. The network call still requires explicit opt-in. Its existing header
  labels cached data and the last update; disabled fetching is shown separately.
  Zero/negative/future cache timestamps are rejected without deleting good data.
- Local EPG evidence must describe the complete current fixture, not just a reused
  fixture ID. Reschedules, changed teams/competition and ambiguous records cannot
  carry an old watch action into a different fixture.
- A verified exact-fixture broadcast assignment confirms only its named linear
  channels in the configured library. It no longer reads every candidate's EPG
  merely to sort channels already assigned to that fixture. This removes up to
  128 redundant programme reads per such lookup; it adds no network request.
  Without a usable assignment, specific EPG lookup has an eight-second aggregate
  deadline and preserves league/team/time checks and cancellation.
- A game card with a resolved variant displays that actual channel name rather
  than an arbitrary other EPG variant. HD/SD/FHD ranking, manual overrides, source
  ambiguity and the existing picker remain unchanged.

Ten new JVM cases per app plus the updated diagnostic regression cover publication
failure, failed journaling, deadlines/cancellation, disabled cache reads, invalid
timestamps, zero EPG reads with verified broadcasts, expired broadcast fallback,
numbered channels and reused/rescheduled fixture identities. Final build/test/CI
results are recorded in the PR; physical Chromecast behaviour remains unverified.

## Source findings

These were bounded anonymous documentation/research reads, not app polling. Robots
were checked first; no authentication/private API, restriction bypass, subscription,
source contact, paid key or new app integration was introduced.

| Source | Actual availability and permission | Decision |
|---|---|---|
| [Official Swehockey statistics](https://stats.swehockey.se/) | Root HTTP 200; robots HTTP 404. [Current SHL standings](https://stats.swehockey.se/ScheduleAndResults/Standings/20961) HTTP 200, 144,375 bytes. The existing production parser accepts the league/season page and reads all 14 ordered standings rows. No documented public JSON/API or explicit general automated-reuse licence was established. | Best verified official standings source in this review. Keep the existing off-by-default personal reader, conservative cache/budget, structure validation and access-denial rules. Official authority does not eliminate HTML-change risk. HockeyAllsvenskan was verified previously; its table was not re-fetched in this review. |
| [TVmatchen terms](https://www.tvmatchen.nu/page/anvandarvillkor), [About](https://www.tvmatchen.nu/page/om-oss) | Robots, terms and About HTTP 200. Normalized reviewed terms hash is unchanged. The private-use clause remains; this is not an explicit general scraping/data-redistribution licence. No documented public fixture-to-channel API/licensed contract was established. Existing exact match-page research is in [min-tv-broadcasts.md](min-tv-broadcasts.md). | Preserve the existing separate off-by-default personal experiment and manual external consultation. It supports SHL, not a claimed general SHL/HA feed. Broader reliable deployment needs written API/data-reuse permission and verified coverage. Never expand to disallowed `/api` routes or pay-TV streams. |
| [TheSportsDB documentation](https://www.thesportsdb.com/documentation), [terms](https://www.thesportsdb.com/docs_terms_of_use.php), [pricing](https://www.thesportsdb.com/pricing) | All HTTP 200. Documented public development key `123` was used for bounded manual probes. SHL `eventsnextleague.php?id=4419` returned one event (Brynäs–Djurgården, 15 October, season 2026–27). `lookuptable.php?l=4419&s=2026-2027` returned HTTP 200 with zero bytes. The first TV probe used `c=Sweden`, which is a channel filter, not the documented country filter; that response is not coverage evidence. Corrected `eventstv.php?d=2026-10-15&s=Ice_Hockey&a=Sweden` returned JSON `tvevents: null`; exact known SHL event `lookuptv.php?id=2476548` returned `tvevent: null`. Documentation describes league tables as soccer-only. Terms limit free usage and impose publication/subscription conditions. | The tested development tier does not establish complete SHL fixtures, hockey tables or Swedish broadcast assignments. A naive switch would lose needed data. Paid coverage is untested, not declared unavailable. No adapter adopted. The returned timestamp lacks an offset; a verified timezone contract would also be required. |
| [Sportradar public hockey reference](https://developer.sportradar.com/ice-hockey/reference/) | Robots HTTP 200. The previously used overview URL now returns 404; the public hockey root redirects normally to the current reference. Its final HTTP 200 document exceeded the 1 MB research limit, so the full coverage/schema/licensing review was not completed. No authenticated feed was queried. | Candidate for a commercial licensed integration, not a verified ready-to-use solution. Obtain exact SHL/HA fixture/table/broadcast-field coverage, Swedish stations, sample matchday data, rights, limits and price before adopting or paying. |
| API-Sports / Goalserve | Robots requests to `api-sports.io` and `www.goalserve.com` were rejected at the development environment's transport/CONNECT stage. No origin response, current documentation or coverage was verified; no further reads or alternate-host workaround. | Research remains blocked here. This does not prove either provider lacks hockey data. No claims of supported Swedish broadcast fields or new adapter. |

## Recommended path

There is currently no verified, explicitly licensed, complete single source here
for both Swedish hockey tables and fixture-to-linear-TV assignments. For this
personal prototype, use official validated schedule/table data separately from
the user's authorized local EPG/channel library. Keep TVmatchen optional under its
existing limited personal policy; generic “SHL” or time alone never proves a match.

For a reliable automatic feed, request a TVmatchen data/API licence or a commercial
sports feed with written SHL/HA and Swedish broadcaster coverage. Validate several
real matchdays and reschedules before integration. A suitable feed should provide
one small league/matchday response cached centrally in the source adapter; filter
followed teams locally and map broadcaster names to existing authorized channel
entities. Keep `SportsRepository` (fixtures/tables) separate from
`BroadcastMetadataSource` (broadcast assignments), independent of playback.

No lineup/live-period support, new decoder, WebView, background worker or streaming
URL generation is enabled by this review. Cached data remains visibly timestamped.
No paid source, new credential/backend or broader scraping is adopted without a
verified contract and the owner's approval.

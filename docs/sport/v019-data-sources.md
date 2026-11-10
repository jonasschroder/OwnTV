# Sport 0.19 — verified public-source contracts

Workstream A, dependent on draft #40 (`9d070cadd74a3725a858f6cadd896678fb069f67`).
These are isolated adapters, not a completed Sport release or a new background service.
Existing SHL/HA readers, Core pin and app behaviour are unchanged. UI consent/discovery,
profile favourites and Watch Match are subsequent integrated workstreams.

## Actual network checks — 8 November 2026, UTC

| Competition | Source / actual response | Parser coverage |
|---|---|---|
| DEL 2026 | OpenLigaDB `getmatchdata/del/2026`, 364 rows | 364; 14 teams, 102 reported final |
| Premier League 2026 | OpenLigaDB `getmatchdata/pl/2026`, 380 rows | 380; 20 teams; 24 overdue unfinished records at capture — incomplete results |
| Champions Hockey League 2026 | OpenLigaDB `getmatchdata/CHL/2026`, 88 rows | 88; 24 teams, 72 reported final |
| NHL 2026/27 | SportsEvents24 tournament **123176**, 46 rows, Nov 8–15 | 46 retained using documented UTC; 7 local-time warnings on the Mac's older tzdb |
| Liiga 2026/27 regular season | SportsEvents24 **142163**, 20 rows | 20; results and upcoming |
| Swiss National League 2026/27 | SportsEvents24 **141810**, 13 rows | 13 upcoming; alternate 141819 duplicated the same fixtures, not combined |
| Czech Extraliga 2026/27 | SportsEvents24 **141861**, 14 rows | 14; results and upcoming |
| ICE Hockey League 2026/27 | SportsEvents24 **141202**, 17 rows | 17; results and upcoming |
| Allsvenskan 2026 | SportsEvents24 **10**, 16 rows | 16 results; 5 source records have no score, never converted to 0–0 |

OpenLiga captures: approximately 16:54 UTC. NHL: 20:21 UTC. Other SportsEvents24:
20:44–20:45 UTC, explicit Nov 1–15 UTC window (exclusive end), one response per tournament.
No pagination was needed for these fixture responses. This does **not** establish whole-season
coverage for the beta API. The actual compiled Kotlin parsers were run on the saved responses;
[sanitized proof](../evidence/sport-v019-data/verification.json) contains counts and response hashes.
No bulk third-party data or artwork is redistributed in this repository.

Independent spot checks: OpenLiga's first DEL fixture agrees with
[PENNY DEL](https://www.penny-del.org/news/detail/meister-eisbaeren-berlin-eroeffnet-die-saison-2026-27).
The EPL opening fixture agrees with the
[Premier League schedule](https://www.premierleague.com/en/news/4675097/all-380-fixtures-for-season-2026-27).
Two SportsEvents24 NHL future start times agree with the
[NHL's published schedule corrections](https://www.nhl.com/news/nhl-announces-the-following-start-time-changes-for-the-2026-27-regular-season-schedule).
These are spot checks, not independent verification of every fixture or Swedish broadcaster.

## Access, rights and budgets

- **SportsEvents24**: [documented API](https://sportsevents24.com/api),
  [OpenAPI](https://sportsevents24.com/api/openapi.json), host `sportsevents24.com`.
  Explicitly permits automated API access without registration, approval or keys; €0 during
  open beta. Limits: 100 requests/day/IP, 10/minute, 5,000 returned records/month/IP,
  100/response. Repeated rows and tournament discovery consume quota; shared IPs share it.
  UTC windows: last 7 to next 30 days, exclusive end. Not an SLA or permanent free promise.
  Broad redistribution/production-app rights and artwork rights are not explicitly licensed;
  clarify before making a Stable distribution commitment. No account endpoint, keys or tokens used.
- **OpenLigaDB**: [primary documentation](https://www.openligadb.de/),
  [API](https://api.openligadb.de/index.html), host `api.openligadb.de`.
  Free, no authentication, community-maintained, **ODbL** attribution/share-alike obligations.
  Numeric rate limit not established. Dedicated identifying User-Agent; 15-minute cache.
  Data licence does not grant team-logo rights. All remote image URLs are ignored.
- SportsEvents24 results refresh at most once/24h per league/window to stay within its small
  monthly allowance; source age is still exposed, not called fresh after 15 minutes.
  No live-score guarantee. Provider-wide 7-second spacing, persistent denial/429 cooldown,
  server quota guard and cancellation; no focus-triggered fetch or automatic cursor walk.
  Missing/invalid UTC, IDs, source/season bindings and ambiguous scores fail closed.
  Local timezone mismatches are warnings, never used to rewrite or drop explicit UTC.

## Alternatives and exact blockers

| Source / sports | Actual evidence and blocker |
|---|---|
| Existing SHL/HA | Retain existing authorized Swedish readers and team IDs; no substitution |
| ESPN / upstream catalogue | Disney's [terms](https://disneytermsofuse.com/english/) restrict automated extraction without permission; runtime API not probed or enabled |
| TheSportsDB | Public demo 123 returned NHL league metadata but **one** next fixture; not a full NHL calendar. [Terms](https://www.thesportsdb.com/docs_terms_of_use.php) distinguish development from paid published-app use |
| PuckAPI | [Docs](https://puckapi.com/docs): free allowance requires personal key/account. Owner has no key and requested alternatives; not a dependency |
| API-Sports | `v1.hockey.api-sports.io` / `v3.football.api-sports.io`: key required, current free coverage not established; no authenticated test |
| Official Liiga / SIHF | Liiga data-service consent; SIHF club API contract (published CHF 300 setup + CHF 130/season). Not used or purchased |
| International hockey | No reviewed complete tournament binding yet; explicitly unavailable, not replaced by CHL or NHL |
| Basketball, baseball, American football, cricket, rugby, Australian rules, volleyball, lacrosse | SportsEvents24 documents corresponding sport IDs; **specific leagues/teams have not yet been validated**. No operational claim from documentation alone |
| Tennis, racing, MMA, golf, rugby league | No permitted keyless endpoint/coverage verified. Upstream ESPN slugs do not establish permission |

No new paid service, mandatory hosting or personal API key. Team IDs from OpenLiga are actual
provider IDs. SportsEvents24 has **no team-ID field**: namespaced country/name hashes are
explicitly marked `NAME_DERIVED`; spelling/club-renaming collisions remain a limitation, not
a migration of existing `hockey:farjestad` or other saved IDs. Seasonal beta tournament bindings
are explicit and must be re-reviewed rather than silently claiming a later season.

## Remaining integrated work

Expose consent, source age/partial coverage, searchable league/team discovery, profile-bound
follows, Calendar/Team/League/Match routes and Watch Match through the native Sport hub.
Neither API supplies verified Swedish linear-channel assignments. Keep authorized local EPG,
match-specific evidence and explicit manual selections separate; no generic TV4 channel guess.
Do not derive standings from incomplete schedules. Broader sports and rights blockers remain
in scope; Sport 0.19 is not complete without owner review of their evidence and availability.

## CI follow-up: timezone rules and main ancestry

The first CI exposed a host tzdb difference: Canada changed rules in November 2026
([Alberta government](https://www.alberta.ca/albertas-new-time-system-abt)). The Mac runs
JDK 21.0.12.1 with older rules; CI has newer rules. Earlier 39/46 acceptance was a
host-dependent validation result, **not proof of seven bad source records**. All 46 explicit
UTC timestamps are now retained; local-time corroboration warnings remain honest and portable.
A deterministic UTC-zone fixture tests conflicts independently of future regional rule changes.

The i18n failure occurred before the ratchet: after full checkout, refetching advanced main
with depth 1 marked its tip shallow and disconnected the merge base. Fetch main without
reintroducing shallow history; the exact merge-base ratchet and every required job remain.
A local two-branch Git regression reproduces the old failure and verifies the actual fixed command.

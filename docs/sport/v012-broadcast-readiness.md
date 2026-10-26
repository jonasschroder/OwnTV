# Exact hockey broadcast lookup — readiness and limits

Base: `08b2adb624a075d939af5f06d9e64f3a648d0ff0`. Separate from performance PR #25.
Public checks: 26 October 2026. No private IPTV snapshots, credentials or downloaded third-party HTML in Git.

## Findings and implementation

- TVmatchen is opt-in and remains off by default. Lookup eligibility is today through today+2 in
  Europe/Stockholm, with exact teams/league/date/instant. On 26 October the requested games on
  29/31 October are outside that window, even though public pages already name their broadcasters.
  The existing six-request/two-hour journal, three fixture-page budget, robots/terms checks,
  no-redirect policy and durable access block remain intact. No `/api` requests or expanded crawl.
- The actual public listing exposed a parser bug: a valid mixed-hockey window with no SHL rows
  failed the non-empty-index assertion. A synthetic regression reproduced it before the fix.
  A validated empty window is now distinct from absent/changed markup; it cannot invent a fixture.
- Null previously meant disabled, blocked, outside the window, budget/backoff, transport error,
  invalid format, ambiguity, rejected fields or no matching fixture. A typed, URL-free diagnostic now
  distinguishes these in the existing Hockey settings panel. Opening it causes no HTTP or polling.
  It reports the latest lookup, not guaranteed coverage for every match. State is in memory;
  an older persisted block does not retain the original HTTP reason across process death.
- Exact collected local EPG wins over a conflicting external assignment, without rereading the
  same channel windows or unioning conflicting channels. Click-time playback still rereads local
  EPG and rechecks source/profile access; reschedules invalidate fixture identity.
- A streaming-only TV4 Play assignment previously stopped local EPG fallback despite naming no
  linear channel. It now leaves the local pathway available and has a distinct diagnostic state.
- Reuse Swedish channel identities for TV Hockey / TV4 Hockey and numbered Hockey Live aliases.
  Bounded alias queries make those identities discoverable; final exact matching still separates
  1/2/12, Play, PPV, foreign services and unknown suffixes. Truncation/ambiguous sources prevent
  automatic Watch. Saved quality/source preferences and explicitly labelled per-profile manual
  choices remain available and never become broadcaster evidence.
- Details identify the external evidence source, or local EPG scan time. Scan time is explicitly
  not the provider's last EPG refresh. Verified external names remain visible without a playable
  IPTV row; no guessed stream is created. No extra persistent navigation menu.

## Actual public-source checks

| Source / hostname | Evidence | Readiness / permission |
|---|---|---|
| Existing authorized IPTV XMLTV / portal EPG | Real Room regressions verify existing mapping, offset, profile and fixture checks; bounded foreground scan remains reused | First-choice in-app path; coverage depends on the owner's imported guide. No new private provider requests made for research. |
| [TVmatchen](https://www.tvmatchen.nu/ishockey/shl), `www.tvmatchen.nu` | Robots HTTP 200 permits reviewed listing/match paths and forbids `/api`. Terms HTTP 200, 619401 bytes, existing reviewed article SHA matches. Listing HTTP 200, 1603212 bytes, 279 rendered match links. Both requested fixture pages HTTP 200 (~650 KB), canonical links and real production fixture parser verify exact linear channels. | Personal opt-in experiment only; not a general redistribution licence. Production fixture parser verified both pages. The listing contains a valid current window with three non-SHL games and no SHL game; the old index parser wrongly rejected that as malformed. The regression now returns an empty verified window while still rejecting missing/changed markup. The two examples are also outside eligibility today. |
| [Tvsporten Färjestad](https://www.tvsporten.nu/lag/farjestad-bk), `www.tvsporten.nu` | Robots and page HTTP 200; bounded page 669241 bytes. Exact examples below verified, with ISO UTC instants. | Manual research validated. No documented automated-reuse licence, public API contract/auth or rate limits established. No product scraper added. |
| [Ronin Sport API feeds](https://www.roninsport.io/api-feeds/), `www.roninsport.io` | Official site offers commercial REST/JSON feeds; Tvsporten's About page identifies Ronin Sport as owner. | Candidate licensed feed. Need Swedish SHL/HA **linear match-level** coverage, schema/sample, API hostname, authentication, request/cache limits and display/retention rights. No API credentials or live API verification. |
| `www.tv4.se`, `www.tv.nu` | Robots HTTP 200 explicitly denies ChatGPT-User/GPTBot | Restricted: no programme-page fetch, impersonation or bypass. Need authorized distributor feed/contract instead. |
| `matchtider.se` | Robots response exceeded 32768-byte research bound | Stopped; permission/content unverified. |
| `www.telenor.se` | TLS handshake failed on robots | Transport blocked/unverified; no content fetch or alternate-host bypass. |

TheSportsDB developer-key test in the existing source review returned null broadcast data for the
correct Swedish filter; paid coverage is not proven. Sportradar/API-Sports/Goalserve remain the
previously documented commercial/unverified candidates; no new integration or credentials assumed.

Research-only fixture evidence (never a production assignment table):
- [Brynäs–Färjestad](https://www.tvmatchen.nu/match/brynas-if-farjestad-bk-1896219), 29 Oct: `2026-10-29T18:00:00Z` = **19:00 Stockholm**, TV4 Sport Live 1.
- [Växjö–Färjestad](https://www.tvmatchen.nu/match/vaxjo-lakers-farjestad-bk-1893256), 31 Oct: `2026-10-31T14:15:00Z` = **15:15 Stockholm**, TV4 Sport Live 3.
- Both pages separately list TV4 Play Hockey as streaming. The existing fixture parser verifies
  `linear=false` for Play and `linear=true` for the numbered channel; streaming branding is not linear proof.

`tools/source-review/TvmatchenSnapshotProbe.kt` is a manual local diagnostic, outside normal test
sources. Copy temporarily into the test package, supply `MINTV_SOURCE_REVIEW_DIR` pointing to bounded
public snapshots outside Git, run that single JVM test, then remove the temporary copy. It performs
no HTTP. These dated examples are research fixtures, not a floating production data dependency.

## Validation and remaining blockers

Local validation: **62 focused JVM tests PASS**, QA app/instrumentation builds PASS,
**3 native Room/diagnostic tests PASS at 1080p**, diagnostic preference-update test PASS at 720p;
i18n ratchet and executed-test inventory PASS. The final production parser also returns a valid
empty index for the downloaded current listing and verifies both requested fixture pages.

Native synthetic diagnostic screenshots: [1080p](v012-images/broadcast-diagnostic-1920x1080.png),
[720p](v012-images/broadcast-diagnostic-1280x720.png). Full production-shell captures come from
mandatory startup CI, with synthetic data and an ordinary CI fixture version, not an official QA installer.

Focused JVM tests cover diagnostic states/budgets/cancellation, exact local-vs-external precedence,
streaming-only fallback, time shifts/replays/reschedules, aliases and ambiguity. Existing real Room
EPG/profile/manual-choice regressions remain required. Added native diagnostic test asserts the
outside-window explanation without any HTTP; mandatory CI explicitly requires its execution.
Existing 1080p/720p production startup, remote/focus, i18n, upgrade and distribution checks remain required.

No default external integration can be called production-ready. An authorized structured feed plus
sample-based contract tests is the recommended next step; don't silently extend the experimental
window, relax parsing, follow redirects or increase limits to hide source changes. Local EPG/manual
fallback works independently of that commercial decision. Physical provider coverage, actual channel
availability and Chromecast playback require subsequent approved QA; neither owner app was updated.

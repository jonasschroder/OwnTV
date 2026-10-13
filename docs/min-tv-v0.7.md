# Min TV v0.7 development and verification

Base: merged v0.6 main `6934d4d7561a35c16830e5c60f8b9c54a5695c72`.
The owner reports that installed QA `0.6.0-beta.1` no longer crashes in Matchcenter
and that Twitch device authorization works on Chromecast. The remaining hockey
problem is data availability; no ongoing crash or out-of-memory diagnosis is made.
Working Live TV, profiles/data, player, permanent signing/updater, IDs and HOME
behavior remain protected. No clean reinstall is required or proposed.

## Hockey request diagnosis

On 2026-10-13 bounded manual research reads returned Swehockey root HTTP 200,
robots HTTP 404, SHL schedule 20961 HTTP 200 (369,467 bytes), and
HockeyAllsvenskan schedule 20962 HTTP 200 (371,908 bytes). Both had the expected
2026–27 headings and 365 desktop table rows (header plus 364 fixtures). This
establishes accessibility/format from this development environment, not the
Chromecast's response or permission for unrestricted automated reuse. The existing
experimental reader remains off by default; 401/403/robots blocks, budget, TTL,
retry deadlines, no redirects and visibility cancellation are retained. No new
scraper, paid API or private endpoint is adopted by this diagnostic change.

Confirmed source defects: early disabled/access/backoff gates ran before failure
journaling; successful requests did not clear old issues; discovery required both
leagues to parse even when only one was requested. Discovery now requires only the
requested league and opportunistically reuses the same root for an unambiguous
other league. A missing unrelated league does not block SHL or spend extra requests.
Cache hydration also works with external retrieval disabled; standings are requested
only on the optional table tab. Physical failure could still be source opt-in,
visibility, persistent access denial, budget/backoff, transport, parsing, season,
cache or publication. No single cause is asserted without the new device report.

The new bounded journal persists 24 typed events per public league, HTTP status,
stage, parser reason, cache age/count, last success and retry time. Gates, missing
or rejected caches, HTTP-200 format failures, valid-empty success, network/timeout,
persistence failures and publication are distinct. It contains no provider/account
URLs, profile/team names, tokens, raw exception text or HTML. Broken journal writes
retain classified in-memory evidence without starting retries. Last success survives
ring rollover. The Settings panel shows the current disabled/blocked policy even
when no request was made; a prior refusal is never automatically cleared.

In the hockey Settings tab, **Senaste hockeyuppdatering** shows per-league status,
attempt/success timestamps and cached fixture count. **Exportera hockeydiagnostik**
uses the existing sanitized sports export; the main error-log export includes the
same typed journal. Reports go to Downloads. Existing sanitized crash/exit evidence
is supplementary historical information, not an assertion of a current crash.

Synthetic report example (not the owner's device):

```text
source=SWEHOCKEY league=se.shl stage=DISCOVERY outcome=FORMAT_ERROR at=1791644760000 http=200 count=-1 cacheAgeMs=-1 retryAt=1791645360000 detail=ROUTE
```

Phase-one verification: 33 focused reader, diagnostic, frozen official-page and
load-state JVM cases; final count is recorded with the completed full validation.
Wrong league/season, malformed HTTP 200, disabled/blocked zero-request behavior,
rate/budget/backoff, cache preservation, cancellation and bounded sanitized journal
remain under regression tests. Current public HTML is not committed as a new fixture.

## Local-first Min hockey

The primary guide reads already stored EPG, independent of external opt-in, schedule
availability or standings. Per-profile teams and the existing manual EPG mapping and
channel/global guide offsets are respected. Relevant actual channels (TV4, TV12,
hockey/sports names and favorites) are bounded to 64, with at most 128 stored
programmes each, 32 fixtures and an eight-second local deadline. The guide refreshes
only while the relevant foreground content is visible; it never fetches provider
EPG, starts a decoder, polls in the background or clears existing data.

A specific league and exactly two validated team identities must appear as an
explicit pairing in a title or description. A favorite team is required. Without a
fresh independently verified schedule, an explicit live/direktsändning marker is
also required. Replays, studio/highlights, youth/women, cross-league ambiguity and
contradictory overlapping games on one channel are rejected. Generic “SHL” is not
confirmation. The displayed time for an EPG-only fixture is clearly labelled as EPG
programme time, not independently verified faceoff. Epoch instants render through
Europe/Stockholm, including DST. No score is inferred from EPG.

Manual choices remain per profile and fixture, with actual source/quality channel
identity and expiration; they never bind all a team's games to one channel. The
UI distinguishes specific EPG evidence, broadcast information, manual choice and
unknown assignment. Ambiguous actual channel variants open the existing picker;
a truncated scan never implies uniqueness. Before EPG-based direct playback the
programme, channel identity, profile and accessibility are rechecked. Missing or
expired evidence fails safely and preserves manual browsing. Playback delegates
to the existing player. “Andra kanaler” stays reachable without external data.

Home now prioritizes one current/upcoming followed fixture and its real channel;
finished result snapshots do not drive its watch action. Extra Home-card HTML
broadcast lookups have been removed. The full guide keeps upcoming matches and
optional results/standings, with sports browsing and a watch action before tables.

## Featured Movies and Series

Discovery now starts with a cinematic featured title from the currently authorized
local library, preferring a matched TMDB ranking and actual cached backdrop. At most
six existing card identities and metadata records are considered; no extra catalog
scan or detail-enrichment HTTP is added. Only one featured record is retained.
Artwork uses the existing cinematic renderer, official bounded TMDB `w780` paths,
and existing poster/cache machinery. Invalid paths, nonfinite/out-of-range ratings
and oversized synopsis are rejected/bounded. Artwork is clipped to the hero and
buttons retain space even with long text. Missing backdrop uses the actual poster;
missing synopsis stays absent. Current-language cached synopsis is preferred; a
candidate from another language does not silently replace it.

The hero offers movie Play, actual continuation with stored position, and Information.
A series without a verified continuation opens Choose episode instead of inventing
an episode. Resume matching requires stable source/item identity from the existing
Core continuation link; title similarity never selects an episode. Movie variants
retain the explicit source picker before Play. Authorization is checked again before
action. Hero focus has stable keys and returns after playback/information; asynchronous
arrival does not request focus. Provider folders, continue/next rows, complete library,
search and filters remain intact. Source labels distinguish provider ratings, TMDB
ratings/global popularity and local genre recommendations. Viewing history stays local.

The reviewed Apache-2.0 JetStreamCompose sample at
`android/tv-samples@15ac8df7df91a07450751a5fe8c6fd17a6a4297f` informed LazyRow/
LazyColumn composition and visible focus/navigation patterns. This implementation
reuses OwnTV's existing components; no sample assets, copyrighted service branding,
Jellyfin code or additional library were copied into the app.

## Channel quality defaults

Automatic Swedish channel choice now uses HD, SD, FHD, unknown, then UHD, with
stable source/id tie breaking. Unknown labels never imply a measured resolution.
UHD remains excluded by default; the existing explicit Show UHD setting permits it
as a last fallback in the curated library. A saved variant always wins, including
FHD/UHD. No enum constants, preference keys, stored values, source identities,
favorites or Swedish group ordering change; no migration or stream probing occurs.

The hockey guide applies the same HD/SD/FHD/unknown preference only after reliable
fixture evidence and a complete scan, for a unique best variant of the same
broadcaster, source and EPG service. Different broadcasters (including numbered
Sport Live channels), sources, EPG services and equal-quality alternatives retain
the picker. A saved channel variant can resolve the source choice; an explicit
fixture choice has precedence. UHD requires an explicit saved variant or manual
selection for direct hockey playback. All candidates remain available in the
existing picker/library; quality selection never expands a fixture's assignments.

Focused JVM regressions cover the ranking, unknown labels, FHD-only availability,
UHD visibility/selection, stable resync keys, manual choices and ambiguous hockey
variants. The existing Room/emulator regression now also verifies all saved
visibility/order/hidden/preferred fields and independent selections in two profiles,
source authorization, unchanged original favorites and reused-profile isolation.

## Twitch stream opening

The owner has verified working physical Twitch device authorization and correct
offline state in QA 0.6.0-beta.1. Encryption, Android Keystore, token validation,
refresh/rotation, device code, public build Client ID and advanced override are
unchanged. A live card additionally shows the official Helix `game_name` and a
**Se stream** action. Only that action queries installed players; it never starts
playback on focus and requires a fresh live status before launching.

Direct opening uses Android's public ACTION_VIEW contract for
`https://www.twitch.tv/ohnepixel`, with an explicitly resolved exported/enabled
activity whose filter names the Twitch authority, a TV LEANBACK entry, enabled
application and target SDK >=23 (Android 14 installation baseline). Generic TV
browsers and mobile-only/old clients are excluded. A picker distinguishes direct
channel opening from normal app opening. The component is resolved again on OK;
uninstall/disable changes or launch failures produce a graceful message. No account
tokens, private extras or extracted stream URLs leave Min TV. No decoder, WebView,
new service or browser assumption is introduced. Login/settings remain reachable.

Public client feasibility on 2026-10-13: S0undTV's official `beta_145.apk` has SDK
23 minimum / SDK 37 target, TV launcher and exported VIEW filters `channel://` and
`s0undtv://stream`. Its official release digest was independently matched:
`bd92c4a984ddc9850239720a42719dbb077d2b4f6636e8dd1d2de97117b1d2e6`.
Only public manifest metadata was inspected; the APK was not installed. The README/
reviewed public issues did not verify the stream parameter contract, so Min TV does
not guess a custom channel URI. If a compatible S0undTV is installed it offers
**Öppna [app]**, its supported ordinary TV launcher, with instructions to find
ohnePixel's channel manually (channel login name: **ohnePixel**). A verified
public HTTPS handler instead offers **Se i [app]**. There is no claim that a
particular client is installed on the owner's Chromecast or that physical playback
works yet. Missing player leaves Min TV sign-in/data untouched.

## External source feasibility (2026-10-13)

| Source | Verified access/contract | Coverage and adoption decision |
| --- | --- | --- |
| Swehockey existing experimental reader | Root/schedules accessible in this environment; robots returned 404. Public HTML alone is not permission. Existing opt-in, restrictions and conservative budgets remain. | Both current schedules show 364 fixtures; physical availability is still unknown. No new scraper or expanded harvesting. Local EPG is independent. |
| [TheSportsDB](https://www.thesportsdb.com/documentation) / [terms](https://www.thesportsdb.com/docs_terms_of_use.php) / [pricing](https://www.thesportsdb.com/pricing) | Documented V1 JSON development key `123`; free development lookup permitted, app-store publication requires paid subscription. Free limit 30 requests/min; listed Developer $9/month 100/min and Business $20/month 120/min. Paid artwork does not grant third-party rights. | Documented Sweden/Ice Hockey query returned SHL `4419`, HockeyAllsvenskan `5162`, current season `2026-2027`. Season calls returned only 15 September fixtures each, matching the documented free season cap. Swedish ice-hockey TV query for 15 October returned zero rows. This does **not** verify complete current/future coverage or broadcasts, even with payment. Not adopted; no paid key requested or embedded. |
| [Sportradar ice-hockey API](https://developer.sportradar.com/ice-hockey/reference/ice-hockey-overview) | Overview/coverage pages returned 403 here; origin-vs-environment cause was not established. No restriction bypass. | Current SHL/HA coverage, broadcast fields, license, credentials, limits and price unverified. A commercial offer and exact coverage would need confirmation before adoption. |
| TV4 / licensed guides / TVmatchen | The tested public TV4 guide/terms routes returned 404; this is not proof that all guides are unavailable. Existing TVmatchen research/permission limits are in `min-tv-broadcasts.md`; no new private endpoint discovered/adopted. | No new authorized fixture-to-channel API verified. Existing separate optional broadcast metadata and manual channel matching remain; local specific EPG supplies actual channel evidence. |
| [TMDB trending documentation](https://developer.themoviedb.org/reference/trending-all) | Existing configured TMDB provider/cache/attribution reused; direct documentation returned 403 in this environment. | Global popularity intersected with actual local content, **not** Netflix Sweden. Existing local profile genre recommendations send no private viewing history. No new key/payment adopted. |
| [Netflix Sweden Top 10](https://www.netflix.com/tudum/top10/sweden) | Public page HTTP 200/title observed; the bounded response was incomplete. The tested old `/top10/data` URL returned 404, terms could not be retrieved. | No authorized machine-readable Swedish ranking was verified. No automated page extraction and no “Netflix Sweden” popularity claim. Netflix folders remain explicitly provider-curated. |
| Disney+, Prime Video, JustWatch/Watchmode | No permitted official Swedish ranking feed was verified; tested JustWatch/Watchmode documentation failed transport here. | No scrape, paid integration or guessed ranking. Provider folders and correctly labelled global/local alternatives remain. |

These are bounded development research reads, not app polling. HTTP 200 alone is
neither coverage nor license verification, and this environment's access failures do
not prove the source is unavailable on Chromecast. No API/source requiring a new
key, payment or unverified permission was adopted. `SportsRepository` remains the
schedule/standings boundary; broadcast metadata stays separate from it and playback.
A future licensed JSON adapter needs verified coverage/contract and owner approval
before introducing keys or paid access. Schedule time must retain real time-zone
conversion and full fixture identity validation.

## QA update safety and manual setup

Do not install ordinary PR/debug or unsigned release validation outputs over the
populated QA app. They are not permanently approved candidates. No APK is uploaded
for installation by ordinary PR CI. This work never invokes signing/distribution,
changes keys/counters/package IDs or publishes a Release. The development version
label is 0.7; approved workflows retain their explicitly selected version overrides.

After review and **separate owner approval**, use the existing protected signing and
distribution process for `se.jonasschroder.mintv.qa`, the same permanent certificate
SHA256 `D2:0B:2F:58:F4:E1:2B:95:6E:1E:1D:C4:0E:47:87:3D:9B:9E:E6:A6:66:A2:DE:1A:F2:A6:B8:AC:3D:40:54:7C`,
and a verified higher approved versionCode. Export/verify the existing encrypted
IPTV backup before the approved in-place update; see `min-tv-safe-qa-update.md` and
`min-tv-permanent-updates-mac.md`. Use the authenticated in-app QA channel. Stop on
certificate, identity, version or metadata/integrity mismatch. Never uninstall,
clear data, downgrade or solve a mismatch with a clean reinstall. QA/Production
remain separate packages, private storage and update channels. Existing database
and preferences are retained. Permanent keys and installed apps are untouched here.

For use after an approved update: choose favorite teams per profile in Min hockey;
let normal existing IPTV EPG sync provide the guide. **Andra kanaler** works without
external sources. Manual match selection preserves an existing quality/source entry.
External hockey sources remain optional and off by default. Export the new Settings
hockey report if the external source still fails; do not send credentials/raw provider
URLs. Movies/Series use the existing cinematic layout (Settings if previously changed)
and configured metadata; provider-only mode works with reduced details. Twitch login
already works; do not re-enter the Client ID or disconnect unnecessarily. If no
supported TV player is installed, installation of one is an explicit later owner
choice; Min TV never installs or modifies another application automatically.

## Physical Chromecast checklist

- Export/verify encrypted backup, installed QA package, permanent certificate and
  versionCode before the separately approved in-place update. Confirm existing
  IPTV sources, favorites/profiles, Twitch sign-in and updater still persist.
- Source disabled: local EPG Min hockey/browse work and zero external hockey requests.
  Source enabled: export exact stage/HTTP/season/cache/publication evidence; preserve
  sticky denial and backoff. Do not assume development HTTP 200 explains device failure.
- Real selected teams, concurrent games, generic/replay/studio programmes, missing or
  shifted EPG, TV12, multiple source/HD/FHD variants, deleted channels and profile switches.
  Verify one OK opens the actual channel and manual choice is fixture/profile-specific.
- Home/Min hockey D-pad, other channels, picker, Back, source failure, process recreation
  and returning from playback. Cached times/provenance/score hiding remain honest.
- Movies/Series real artwork, Swedish synopsis when available, rating source, folders,
  complete library/search, movie quality picker, actual series continuation/next episode,
  focus restoration, large libraries and memory on Chromecast. Emulator timings are
  not Chromecast benchmarks; no measured percentage speedup is claimed.
- Twitch real LIVE/category/viewers, offline, unavailable/disabled/uninstalled clients,
  actual installed Android-14-compatible player's direct or clearly labelled manual
  opening, remote control and return. Verify the player's own login/playback separately.
- Existing Swedish channel order/categories, hidden UHD, favorites/EPG, live/catch-up,
  muted preview/fullscreen promotion, backup and authenticated QA updates regressions.

Additional review fixes: request-attempt time is persisted separately from cache/UI
publication; a successful schedule immediately updates the fixture cache count,
and standings cannot overwrite it. Outer fetch deadlines are journaled as TIMEOUT,
without overwriting the reader's own backoff/season/access classifications. Settings
observes scoped local preference changes instead of stale snapshots or polling.
The EPG adapter reads the requested profile's customizations explicitly and checks
active profile/source access before and after storage reads, so a delayed shared
UI state cannot supply the preceding profile's guide mapping.

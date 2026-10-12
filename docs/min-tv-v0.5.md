# Min TV v0.5 implementation evidence

Baseline: main `8a1593f8cdd2b1f9200ca116e6d5aed004fd7e11`; Core remains
`adca2bcd653f19e6e5d659c2722309e6aef4ec15` (database v47).

## A. Sports stability and diagnostics

The reported physical exit after verified data arrival is still unconfirmed. There
is no owner-device stack or process-exit evidence. HTTP success is not evidence of
an OOM, parser exception or Compose crash. Use More → Error log → Export crash
report and share the sanitized report (not the raw provider log). Android exit
reason/status/PSS/RSS, original exception frames and bounded previous-process
context are retained; current Java heap/max and native heap bytes are added.
Native graphics/decoder memory requires device `dumpsys meminfo`, not a JVM guess.

Reuse: existing replaceable opt-in Swehockey reader, strict season/team verification,
AtomicFile compact caches, persistent shared 12 requests/2 hours, request cancellation,
8-second HTTP deadlines, 35-second total request deadline, TTL/retry/access gates.
Disabled source performs zero requests. No new service, player or decoder.

Removed automatic screen retry/poll loops: opening visible content reads cache first,
then attempts one refresh; reopening respects the same TTL/backoff and mutex. Back
and background cancel screen work. Reader IO uses one parsing slot. HTML is capped
at 600,000 bytes, cached JSON at 200,000 bytes per league/table, games at 400 per
league. Table row extraction is linear with 16 KiB rows and 24 cells maximum;
oversized/malformed data is rejected, not partially confirmed. Oversized HTTP data
is classified FORMAT rather than a misleading network failure. Successful data
and publication errors remain distinct; no swallowing a UI exception as a source
failure. Conservative caches remain labelled with their last update time.

Targeted sports/Hockey JVM tests passed locally before presentation work. Physical
crash reproduction and measured Chromecast performance are still required.

## B. Swedish Live TV

New default Live view derives Swedish logical channels from the existing library.
SVT1–TV12 ordering is explicit; absent services are omitted. Separate Sport, News,
Children and Other views and All channels/search retain the original category view.
Exact broadcaster names and explicit Swedish country markers are recognized;
country/quality decorations are removed without substring-merging channel numbers.
Unknown regional/audio/+1 names remain distinct (or in All if untagged); explicit
regional EPG evidence also separates groups. TV4 Play Hockey is not synthesized as
linear playback. Recognition is conservative, not a universal provider naming map.

One preferred variant is shown, FHD → HD → SD → unknown → UHD (labels are only
advertised quality, never measured reliability). Explicit prior choice wins. UHD is
off by default only in the curated view; full library, existing favorites, direct
links, catch-up, recordings and numbering remain intact. Choose quality/source,
show variants, reorder, hide, favorite, and restore controls are available in the
collection. Source marks distinguish identical provider names. Preferences use a
stable source/remote-or-name identity and profile creation stamp; resync does not
clear them. No existing data is deleted and the Room schema is unchanged.

Derived catalog reads use 400-row IO batches, at most 100,000 scanned rows and 4,096
compact candidates; exceeding bounds reports an incomplete collection and points
to the complete paged library. The existing Live controller, EPG and preview surface
are reused; entity authorization context is fetched afresh before playback.
The selected channel's real current/next EPG is displayed; no synthetic guide.
Eight pure recognition/grouping tests passed; playback/D-pad/provider fixtures and
physical device checks follow in the integration phase.

## C. Authorized discovery and local indexing

Audited Core MetadataRepository, TmdbProvider, title/match overrides, metadata_match,
metadata_cache, TrendingScheduleStore, indexed catalog fields, Room/Paging, Home
continuations and provider progress before adding discovery. No second metadata DB,
no schema migration and no network scan of the library. Existing provider configuration
precedence (self-host > personal API key > shared metered worker) and allowances remain.
A metadata-only transport bounds decompressed responses to 1 MiB and calls to 15 s;
IPTV transport is unchanged. Trending calls are serialized/coalesced through the same
provider instance used by Core's import worker. Visible discovery uses one page (20
candidates) per media type, shared existing disk cache, 24-hour TTL and 15-minute
failure backoff. Cached data survives a failed refresh. No requests on focus.

Daily movie/TV trending uses documented `/3/trending/movie/day` and `/3/trending/tv/day`.
Official weekly trending, movie/tv popular, top_rated, recommendations and similar
endpoints exist, but are not additional feeds in this prototype: adding all of them
would consume the shared quota and duplicate traffic. Top 10 is the highest-ranked
verified available subset of the bounded daily feed, never Min TV viewership. Fewer
than ten matches produces fewer cards; only the first feed page is covered. Rankings
are explicitly attributed and stamped. Provider rating rows remain labelled as such.

Local primary-key metadata matches (confidence >= .92, correct media type and Kids
scope) are preferred. Without an ID, exact safe original/localized title AND known
matching year are required; ambiguous/remake/missing-year/destructively normalized
names abstain. Existing historical positive metadata can still require manual correction;
it is not purged. Accessible profile/source/hidden/Kids policies are applied. Catalog
queries walk 400 compact rows per IO batch (100,000 hard maximum), retain fixed-size
rows (12 items, 6 genres), and never retain all 7,000 movies as Compose state. Genre
recommendations use locally cached genre overlap from bounded profile history/favorites;
no history, provider URL or credential is sent to TMDB. Newness is real provider addedAt.

Five discovery tests plus eight channel tests passed locally, including a synthetic
7,000-title bounded collector, partial trend coverage, ambiguity, type/year protection,
profile genre seeds and completed-content exclusion. Android/Room integration follows.

### Source feasibility and manual setup

- TMDB: [official API documentation](https://developer.themoviedb.org/reference/trending-all),
  [API FAQ/terms](https://developer.themoviedb.org/docs/faq). Free noncommercial API access
  is subject to TMDB's terms and attribution, not an exemption for personal use. Owner
  should review those terms; no paid plan or new service was adopted. Existing TMDB logo
  and required non-endorsement statement are reused in discovery. The documentation
  host could not be reached from this runtime; official endpoint usage is source-verified
  against the pinned Core, not a new live-account/key verification. Recommended personal
  key setup is existing Settings → Content & metadata → Metadata (TMDB) → remote QR/PIN
  entry, avoiding remote typing. Never commit the key; metadata failure keeps local browse.
- SHL's public site returned HTTP 403; no bypass attempted. Swehockey robots returned
  404 (absence of robots is NOT reuse permission). No documented free official API and
  verified license covering BOTH SHL and HockeyAllsvenskan were established. Existing
  experimental opt-in reader is retained with stricter bounds; no new automatic source.
- Sportradar's documented ice-hockey product and potential licensed league/federation
  feeds require a coverage/licensing/account discussion. Its documentation host was
  unavailable here, so current both-league coverage, prices and rate limits are unverified.
  Sportmonks hockey product URL was also unavailable. No registration, purchase, private
  endpoint reverse engineering or paid service adoption was performed. An owner-approved
  officially licensed replacement must verify season/team coverage and terms first.

## D. Movies and Series presentation

Cinematic default now shows a compact header and lazy horizontal discovery rows
rather than the giant grid/hero. Continue Watching and verified Next episode use
Core's real per-profile continuation objects and positions. Empty discovery rows
are omitted. Top available, additional trending matches, provider-recent/provider-
rated, local genre recommendations and bounded genre shelves come from real data.
Missing metadata/library gracefully offers All movies/series with existing search,
category, sort, manual metadata correction and Paging, and Back returns to discovery.
Separate layout and More → Favorites/History remain unchanged.

Movie selection opens existing MediaDetailsScreen and its resume/play policy;
series selection opens existing seasons/episodes and playback controller. Provider
artwork, rating source labels, clear focus and profile-keyed saved focus reuse
existing StagePoster and continuation components. No new player or large-image
preloading. Instrumentation screenshots must use synthetic data and cannot prove
real catalogue matching, artwork performance or Chromecast remote behavior.

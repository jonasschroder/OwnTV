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

## E. Home

Home order is Live preview, real favorites, Continue Watching, favorite-team sport,
available movie trends, available series trends, Twitch and external shortcuts.
Empty optional rows collapse. Stable section IDs map to real lazy-item indices as
data arrives; dynamic rows do not renumber Twitch/apps identities. Per-profile
section visibility is customizable in Home settings. Hiding Live stops its decoder
preview and leaves compact Live/Guide/Settings actions. Existing real favorites
(including deliberately saved UHD/non-Swedish channels) are preserved.

Discovery activates only when relevant lower Home content approaches the visible
viewport, shares the same cache/request gates with Movies/Series, and cancels on
background/hidden content. The single existing Live preview controller and protected
surface remain. Sidebar destinations and external launch recovery are retained.
Compact favorite-team Matchcenter defaults to up to three upcoming matches plus
five verified previous scores and existing cached standings/team cards. No result
is guessed when missing; experimental controls/diagnostics stay in its Settings tab.
Home navigation, discovery and sports targeted JVM checks passed locally.

## F. Twitch

Owner confirmed Public client registration. On 2026-10-12, the official
`https://id.twitch.tv/oauth2/device` endpoint returned HTTP 200 for the configured
public ID with empty scopes, valid activation schema, interval 5 s and expiry
1,800 s. One unapproved `/oauth2/token` poll returned HTTP 400
`authorization_pending`, as documented. Codes/device IDs/bodies/tokens were never
printed or saved. No live account approval was performed. The actual physical
v0.4 failure remains unconfirmed; valid Client ID is not proof of completed login.

Official [device grant documentation](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow)
was reachable. Existing device request fields, endpoint and activation validation
match its Public flow. Fixed missing denial/expiry/slow-down/rate-limit/network
states, monotonic expiry and polling discipline, pre-storage token identity
validation, and erroneous deletion of saved credentials on a transient refresh
failure. Explicit invalid refresh/unauthorized tokens still require reconnection;
single-use refresh tokens are never transparently retried. Existing Android Keystore
AES-GCM/AtomicFile/no-backup storage and certificate/update configuration are retained.

Selecting Connect starts immediately with the configured ID (advanced override still
wins). The code/official URL stay visible while polling. Close/Back/background cancel
the request/poll loop; explicit retry creates a new flow. Actionable EN/SV messages
identify registration, restriction, denial, expiry, network, rate-limit, invalid
response, identity and secure-storage errors, without exposing server bodies.

Seven injectable-protocol tests plus existing activation tests passed locally. Cases
cover code-before-poll, no client secret, pending/slow-down intervals, denial/expiry,
rate/network retry, cancellation, response sanitization and token identity. Completed
mobile authorization, Chromecast focus/background/resume, real token refresh and
account status still require physical/live-account acceptance. On the phone, open
the exact supplied Public activation URL and approve the short code; if rejected,
check the app remains Public and the advanced override matches the registered ID.
Never enter a Client Secret; never clear TV application data to retry Twitch.

## G. Integration and release safety

The production Core database uses BundledSQLiteDriver. Integration review caught
legacy `openHelper` calls in the new readers; these were replaced with bounded
prepared reads through `useReaderConnection`, and regression fixtures run against
the real bundled driver. Source/profile ownership is rechecked before displaying
and selecting entries. After resync, stale row IDs cannot substitute another provider
identity. Home movie selections now open existing details/resume policy rather than
forcing a film to start at zero. Live return focus and protected-video fullscreen
promotion retain the existing controller and DRM behavior.

New Android TV suite members verify real Room matching/order, offline cached lists,
hidden and foreign-profile refusal, provider replacement, preferred quality/source
persistence, favorite preservation, headers/EPG/numbering/catch-up context, 7,000-movie/
1,900-series bounded queries, D-pad selection and insertion while apps retain focus.
A synthetic Twitch transport verifies real Android Keystore encryption and preservation
of credentials after a transient refresh failure. No owner's data/account or signing
key is used. Screenshots are production row components with explicitly synthetic
records, not the owner's IPTV library. CI requires every old and new case to execute
without failures/errors/skips. Signing upgrade tests use disposable credentials.

### Manual acceptance before any QA rollout

Installed QA is `0.4.0-beta.1`. Do not uninstall, clear data, use a debug APK over it,
or modify system packages/settings. No permanent signing/distribution workflow was
started. Owner will review/merge and manually create a new QA candidate through the
existing protected process, with a higher approved versionCode and the SAME permanent
certificate. App IDs, channel isolation, authenticated update checks, signing environments,
keys, version counters, player and pinned Core remain unchanged. Only development
versionName defaults to 0.5; protected caller-supplied names/codes still take precedence.

Before approving an in-place update, export an encrypted Core backup to the Mac,
verify its integrity/password with the existing backup tools, and retain the old
working signed installer. The owner's physical backup has not been obtained or
verified in this workspace. New derived Home/channel preferences survive in-place
updates; they are app-private preferences and are not newly added to Core's backup
schema. Token files remain in no-backup storage. Never recommend a clean reinstall
as an authentication fix or upgrade path.

Physical acceptance required: collect the sanitized sports exit report; rapid
Home/Matchcenter/league/team/profile navigation during data arrival; real matchdays,
robots/access/rate/offline failures; thousands of provider channels and variant names;
FHD/HD/SD/UHD selection, distinct regional broadcasts, saved UHD favorites, direct
number tuning/zapping, EPG/catch-up/recording; preview mute/fullscreen/DRM/stream quota;
large real VOD titles/artwork, ambiguous/manual metadata corrections, details/resume,
seasons/episodes/audio/subtitles/return focus; Twitch phone approval, expiry/Back/
background and real single-use refresh; app shortcuts and EN/SV/system language;
same-key QA update preserving profiles/favorites/history/settings and Production data.

Measure Chromecast Java/native/graphics/decoder memory and smoothness using the
existing sanitized export and read-only diagnostics (`dumpsys meminfo` when ADB is
already authorized). Emulator query timing/heap output is synthetic evidence only;
it cannot establish Chromecast RAM/startup/frame/decoder performance or prove the
reported physical crash is fixed. Metadata's existing provider may return null for
transport, parsing or allowance failures; the UI honestly reports unavailable discovery
rather than inventing a specific upstream HTTP cause.

### Local validation snapshot

- Full JVM: QA 358 + Production 358 + Core 939 + player-core 271 = 1,926;
  no failures/errors/skips. Security/policy Python: 45 passed.
- Both ARM debug and optimized unsigned release builds passed. All four lint variants
  passed with zero errors (139 debug / 136 release warnings plus 22 hints each).
  A new FocusRequester lint error was corrected using remembered composition identity;
  no lint baseline, assertion or security check was weakened.
- Release package/provider/permission/component/link isolation and absence of HOME
  registration passed, both public Twitch-ID post-R8 DEX checks passed, release
  pseudolocales absent. i18n ratchet remains 45 baseline entries; actionlint/whitespace pass.
- Comparable unsigned release sizes: QA 63,744,580 bytes; Production 63,743,576 bytes.
  Versus v0.4: +60,572 / +60,580 bytes (~0.095%). These are development artifacts,
  not approved permanently signed update candidates. VCS metadata can alter final hashes.
- Android TV fixtures compiled locally; no local KVM device is available. Actual emulator
  execution, screenshots, i18n UI checks and disposable-signature upgrade results will
  be recorded with exact final-commit GitHub Actions links in the PR after completion.

Build commands (JDK 21, Android SDK 37, pinned Core):

```sh
bash tools/prepare-core.sh
./gradlew :app:testQaDebugUnitTest :app:testStandardDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest
python3 -m unittest discover -s tools/tests -v
./gradlew :app:lintQaDebug :app:lintStandardDebug :app:lintQaRelease :app:lintStandardRelease \
  :app:assembleQaDebug :app:assembleStandardDebug :app:assembleQaRelease :app:assembleStandardRelease \
  :app:compileX86_64DebugAndroidTestKotlin --max-workers=4
```

Use the prepared cloud helper `python3 /workspace/cloud-setup/run.py ./gradlew ...`
in this retained workspace. Ordinary CI intentionally does not upload debug installers;
only reports and synthetic screenshots are downloadable. Do not install unsigned/debug
artifacts over the permanently signed populated QA application.

Final lifecycle follow-up: local discovery reads and Room observers are separate from
network visibility, so cached rows can appear before scrolling to them. Hidden sections
and background state stop their work; metadata-mode changes re-read local policy. The
new daily-feed requests now attach their actual OkHttp calls to the caller's cancellation,
including Core's synchronous IO. A temporary request child closes the socket immediately
on cancellation and is removed on completion; no service or polling loop is introduced.
The regression uses a genuinely blocking intercepted HTTP call and verifies Call.cancel,
with real time so a virtual deadline cannot skip request start. Known-length and unknown-
length oversized response tests both enforce the decompressed 1 MiB budget.

The first PR CI attempt stopped BEFORE Android tests on exact literal counts and a new
numeric display formatter. Those were corrected without new exceptions or a baseline
increase; existing vodRating is reused. The subsequent baseline revision executed all
23 Android TV tests and disposable-key upgrade checks successfully. The final follow-up
is revalidated on its own commit; exact final run links/results are maintained in PR #7.

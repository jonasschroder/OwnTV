# Min TV v0.6 — implementation and source evidence

Base: merged v0.5 main `12cc63d29cea40e760eb329c5b05907c3a8de23f`.
Owner reports the permanently signed v0.5 update succeeded on Chromecast Android 14.
Installed QA is populated `0.5.0-beta.1`; it was not accessed or changed here.

## Provider-first discovery

Movies and Series now put real, nonempty provider folders before optional TMDB rows.
Movie priority recognizes exact IMDb Top 250, Oscar Movies, Disney, Swedish, Swedish
(Kids), Christmas, English and Multi Subtitles labels, with MOVIE/MOVIES prefixes.
Series recognizes Netflix, Disney and Amazon Prime with a SERIES prefix. Other
accessible folders follow. Explicit profile category order and names take precedence.
Names are labels from the provider, **not** proof of official rankings, licensing,
subscriptions or IMDb-verified ratings. The visible source attribution says so.

Each preview retains the original category/source and actual playable entity. A title
may appear in multiple real folders. Within one folder, only the same high-confidence
metadata ID AND known same year may group versions; unknown/ambiguous identities or
different years stay separate. Choosing a version opens the existing movie details
or series episode flow. Stream URLs, headers, DRM, profiles and progress are not
recreated. Source/profile/stable item/category identity, hidden content and moved-out
membership are rechecked before selection. A stale or foreign entry cannot play.

Previews retain at most 12 folders × 12 cards × 4 displayed variants. All underlying
entries and additional variants remain accessible through **All entries in this
folder**, which opens the existing category pager, or All movies/series/search.
The existing 400-row IO catalog walk (100,000-row hard limit) supplies both local
folders and enrichment without retaining a complete catalog in Compose. No second
database, schema migration or network scan. Provider previews follow provider order;
the complete pager retains all existing sort/manual-order/custom-category controls.
Core stores one categoryId per imported entity; memberships absent from that import
cannot be reconstructed or invented. No provider categories/items are modified.

Hidden categories/items, category renames/order and moves are honored; customization
changes reread local shelves without refreshing the network. Kids/profile/source
restrictions remain. Swedish Live TV ordering/grouping/quality and player code remain
unchanged. Continue Watching/Next episode, offline browse, cached art, conservative
TMDB matches and daily available rankings remain complementary.

## TV UI references

[Google JetStreamCompose](https://github.com/android/tv-samples/tree/15ac8df7df91a07450751a5fe8c6fd17a6a4297f/JetStreamCompose)
MovieCard/MoviesScreenMovieList were reviewed: lazy horizontal shelves, focusable TV
surfaces, restrained focus scale, artwork and section headings. The sample is Apache
2.0. This implementation reuses Min TV StagePoster/VodPosterArt and independently
written folder/picker logic; no sample assets or source were copied.
[Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv/tree/0032f4aea3909cf251d7b67ea5c5ed9f434d6505)
public overview/license was reviewed for TV browsing context (GPL-2.0); no GPL source,
assets or dependency were copied. No Telenor screenshot was available in this upload;
no claim of pixel matching or copying proprietary artwork.

## Popularity sources — keep provenance distinct

| Source | What v0.6 actually does | Access/coverage limitation |
| --- | --- | --- |
| Provider folders | Primary local shelves with original membership/source | Provider labels do not establish subscriptions, external rankings or verified IMDb ratings. |
| TMDB | Existing documented daily movie/TV trending, 20 candidates/type, 24-hour cache, verified available subset | Existing configuration/attribution/allowances remain. Current documentation request returned HTTP 403; new terms/key/account verification was not performed. No extra popularity feeds added. |
| Official Netflix Sweden Top 10 | Research only; official Tudum Sweden page and published country TSV location attempted | Runtime proxy denied CONNECT with 403 before reaching Netflix. Schema, current Sweden ranking and automated reuse terms are unverified; no feed/scraper added. |
| Disney+ Top 10/availability | Research only; official help location attempted | Runtime CONNECT denied 403; no verified licensed public ranking/availability API established. A Disney folder is not Disney+ Top 10. No scraping or subscription inference. |
| IMDb noncommercial datasets | Official dataset documentation attempted; no download/import | Runtime CONNECT denied 403. Current license/redistribution/update/size requirements need review before use. No bulk dataset, new index or guessed ID/rating added. |
| Local recommendations | Existing cached genre overlap from local history/favorites | No private viewing data or IPTV credentials sent out; no claim of an AI model. |

Personal use does not override terms. Optional personal TMDB setup still uses existing
Settings → Content & metadata → Metadata (TMDB) remote QR/PIN entry. No paid API,
account, backend or new credential was adopted. API-enrichment failure leaves provider
folders and complete local libraries available.

## Sports findings and TheSportsDB feasibility

**The physical sports exit remains unconfirmed.** The owner was asked for the sanitized
v0.5 report through More → Error log → Export crash report. No report was available
during implementation. Existing original exception frames, Android historical exit
reason/status/PSS/RSS, bounded previous-process context and current Java/native heap
export remain. No OOM, native or Compose root cause is claimed.

One concrete latency problem was corrected: all relevant leagues' verified caches are
now hydrated before any network refresh. Previously a slow first league could conceal
another league's cache for up to 35 seconds. Loading text makes continued navigation
explicit. Existing visible-only one-shot schedule refresh, TTL/backoff/mutex, 35-second
overall and 8-second HTTP bounds, single IO parser slot, strict compact cache/model
limits, opt-in/robots/access gates and lifecycle cancellation remain. Disabled means
zero sports feed requests. No new service, decoder or unverified sports results.

[TheSportsDB documentation](https://www.thesportsdb.com/docs/api), terms location and
league pages [4419](https://www.thesportsdb.com/league/4419) / [5162](https://www.thesportsdb.com/league/5162)
were attempted on 2026-10-12. All were blocked by the runtime proxy's CONNECT 403;
this is an **environment denial**, not a verified denial by TheSportsDB itself.
The IDs and reported incomplete league pages come from the request, not a completed
live coverage audit. Current season, fixtures/results/standings, missing events,
freshness, costs, licensing and rate limits could not be verified. No undocumented
key/endpoint, trial signup or paid service was used. **No TheSportsDB adapter adopted.**

Swehockey's public root was reachable (HTTP 200, date 2026-10-12, both league labels);
robots returned 404, which is not permission. That root alone is not verification of
individual fixtures. Representative upcoming/completed Färjestad and BIK Karlskoga
games were **not** cross-verified against TheSportsDB; zero comparisons are claimed.
Retain the existing owner-approved experimental source and honest cached/unavailable
states. A licensed league/federation feed or Sportradar product is a candidate for
owner coverage/licensing discussion, not a validated replacement. Any remote compact
cache would need verified data rights, account/hosting/cost approval first; none exists
or was deployed by this task.

Environment draft additions preserve existing rules and add www.thesportsdb.com,
www.netflix.com, help.disneyplus.com and developer.imdb.com. Saving does not apply
runtime changes or grant data reuse permission. No proxy/403/robots/TLS bypass.

## Twitch — reproduced protocol mismatch and lifecycle correction

The registered public ID's official device request returned HTTP 200 on 2026-10-12.
The actual returned address was `https://www.twitch.tv/activate?device-code=…`, without
`public=true`. v0.5's parser required that optional parameter and rejected this valid
response before showing the code. v0.6 accepts its absence, preserves the exact
returned URI, and retains HTTPS/exact www.twitch.tv/activate/no port/userinfo/fragment,
matching uppercase code, allowed query names, no duplicates/extra fields, and true
when public is present. The documented public=true variant still works. One subsequent
unapproved poll returned HTTP 400 authorization_pending. No code/device ID/complete URI/
token/body was logged or saved; no account authorization occurred.

A second source-level problem was removed: Home's Live preview LOADING/favorite-focus
gates previously controlled the authorization coroutine and could cancel it. Those
conditions now suppress only stream-status polling. An open grant stays alive while
Home is foreground; Back/close/background still cancel. These are reproduced/source
findings, **not proof of the historical physical failure or complete account login**.

The dialog now shows the official URL, short code, locally rendered QR and waiting
instructions. QR generation reuses Core's existing library; no WebView/server/decoder.
Failures show only localized stage and HTTP status plus existing actionable reason;
server text, Client ID, activation codes and tokens are excluded from diagnostics.
Public configured ID and advanced override remain. GitHub denied repository-variable
inspection to this integration (403), so no claim is made about an unseen override;
release DEX checks verify the build's configured ID. Android Keystore/AES-GCM/AtomicFile/
no-backup storage, token validation/refresh and no Client Secret remain unchanged.

[Official Twitch device grant docs](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow)
were reachable and reviewed. Without personal login, a stream-status backend could
use a separately registered confidential app and server-held app token if permitted.
A public TV client cannot obtain client-credentials app tokens on its own. No secret
may enter the APK; no backend/account/cost was approved or implemented. Existing
authorized user-token status and external app shortcut remain.

## Validation and manual acceptance

Final exact-commit CI results, reports/screenshots and measured synthetic fixture
timing are recorded in the PR after completion. Unit suites, real bundled-Room tests,
28 required TV cases, four lint variants, i18n ratchet (45 entries), ARM debug/unsigned
release both identities, package/authority/permission/no-HOME/pseudolocale checks and
disposable-key upgrade/native signing tests are required. No assertions or baseline
are weakened. A plural-resource lint error was corrected with EN/SV plurals.

No merge, permanent signing/distribution workflow, Release, owner-device installation,
reset/uninstall or system modification. Protected workflows, package IDs, counters,
authenticated updates, player, Core pin adca2bcd653f19e6e5d659c2722309e6aef4ec15 and DB47
are unchanged. Only development default versionName becomes 0.6. Ordinary CI uploads
reports/synthetic screenshots, not debug installers; do not install debug/unsigned
APKs over populated permanently signed QA.

Before a manually approved update: export and verify an encrypted Core backup on
the Mac, retain the old signed installer and use the same permanent certificate with
a higher approved versionCode through the existing protected process. The owner's
physical backup was not obtained/verified here. Do not clear data to fix Twitch.

Physical acceptance: actual bouquet membership and multiple-source variants; manual
category order/hidden/moved content and resync; large real artwork/catalog; movies
resume and series/next episode; D-pad/footer/picker/Back/return focus; original Swedish
Live/EPG/catch-up/DRM/muted preview; sports entry/cache/data arrival/profile/league
switching and sanitized crash/exit evidence; Twitch QR phone approval and automatic
completion while preview changes, background/Back, real token refresh/storage;
same-key in-place update preserving all QA data and keeping Production separate.
Measure physical Java/native/graphics/decoder memory and responsiveness; synthetic
emulator heap/query measurements are not a Chromecast performance or crash claim.

Build with the retained JDK21/SDK37 and pinned Core:

```sh
bash tools/prepare-core.sh
./gradlew :app:testQaDebugUnitTest :app:testStandardDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest
python3 -m unittest discover -s tools/tests -v
./gradlew :app:lintQaDebug :app:lintStandardDebug :app:lintQaRelease :app:lintStandardRelease \
  :app:assembleQaDebug :app:assembleStandardDebug :app:assembleQaRelease :app:assembleStandardRelease \
  :app:compileX86_64DebugAndroidTestKotlin --max-workers=4
```

In this retained cloud workspace prefix Gradle with
`python3 /workspace/cloud-setup/run.py ./gradlew …`. Do not modify cached Core or
read private signing credentials. Production device acceptance remains manual.

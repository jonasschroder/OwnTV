# Iteration 3 — review (25 October 2026)

Same local workspace and preview: http://127.0.0.1:57820/ . No Android, Core, Git branch, commit, PR, build, signing or release changes.

## Verified before changes

Actual browser showed DESIGNLABB02,20TV cards/five columns,art-led sport cards,Movies/Series rows,ordered TV Hockey channels,no Twitch on Sport,and working source mode/status controls. Iteration2 existed; it was not rebuilt. External adapter script now has an explicit iteration URL version to avoid stale cached code. Footer is DESIGNLABB03; MOCK correctly continues to say synthetic data.

## Completed now

- Channel detail sheet with current/next/missing-guide information,simulated playback and clearly mock-only source-quality selection. Back restores the exact channel and scroll position.
- Fixed inherited scroll when first opening another page; remembered per-page content focus/column remains. Focus outline changes immediately,without a fading trail suggesting multiple focus targets.
- One compact developer bar; Scenarier drawer holds match state,favourite team,EPG,progress,Twitch and configured mock channel-library scenarios. Escape dismisses it; Till TV returns to the remote focus. Capture mode hides developer controls.
- Home hero follows selected mock team/match,then Continue Watching or TV when no match. Four distinct mock Twitch states:online,offline,missing auth,API unavailable. No Twitch on Sport.
- EPG cases:missing guide,missing art,stale guide (no claimed current programme/progress),incomplete branding with readable channel identity. Existing stable IDs preserve focus on updates.
- Hockey choices use the selected mock library only:full,partial(Hockey+Live2),empty. Empty manual selector has only Back; no invented channel. LIVE never uses this synthetic library.
- Status panel separates schedule/results,live status,verified broadcaster and accessible IPTV channel. Manual selection never upgrades broadcast verification.

## Automated validation

Reusable CUA API browser modules: `keyboard-tests.mjs` and `scenario-tests.mjs`. Run exported functions with the existing CUA tab handle; no direct Playwright process or browser-state mutation. Evidence saved as iteration3-*-results.json.

-27keyboard assertions at1920×1080 and27 at1280×720 passed. All20channels traversed with actual arrows; focus visibility checked; Enter/detail/player/Back; Home horizontal→vertical; Movies→Series without prerequisite Right; same-column movement; inactive nav focus; Down activation; top-menu access; page-history restoration.
-19deterministic scenario assertions passed:EPG cases,Twitch states,team changes,no-match/no-progress,partial/empty library,manual fallback and unavailable mode.
-Public LIVE adapter rechecked once via browser:Frölunda HC–Rögle BK,27Oct2026,source time18:00,statusNS. Retrieved24Oct22:41:09UTC =25Oct00:41:09Stockholm. Retrieval freshness is not proof of real-time score freshness or timezone semantics.
-Four actual browser screenshots at1920×1080 in screenshots/iteration3-*.jpg. These are browser prototype screenshots,not Android/device evidence.

## Data-source matrix

| Contract | State | Blocker / next requirement |
|---|---|---|
| Partial SHL schedule | VERIFIED RESPONSE | www.thesportsdb.com public development API;one-event free limit,not full SHL/HA coverage. Existing bounded900s cache;no background polling. |
| Real-time score/status | MISSING | No SHL/HA live-score feed validated. NS is a returned fixture field,not proof of an ongoing live feed. |
| Current broadcaster assignment | MISSING | No current fixture-specific linear broadcast verified;historical API TV record is insufficient. TVmatchen reuse permission unresolved. |
| Accessible IPTV channel | MOCK-ONLY / NOT CONNECTED | Authorized profile-scoped provider/library bridge required. Host unknown;never expose credentials or private URLs. |
| EPG/programme art/channel logos | MOCK-ONLY / NOT CONNECTED | Authorized provider/XMLTV feed plus matching/freshness and artwork rights. Existing EpgLogoStore/EPG contracts retained as read-only references. |
| Twitch online/offline | AUTH REQUIRED;states MOCK-ONLY | api.twitch.tv + id.twitch.tv,registered Client-ID and server-side token. Earlier401evidence24Oct;not re-authenticated or probed again. |
| TMDB movie/series metadata | AUTH REQUIRED;UI MOCK-ONLY | api.themoviedb.org authorized server key/proxy contract;image.tmdb.org artwork rights/attribution. Earlier401evidence24Oct. No key extraction. |
| Swehockey / TVmatchen | PERMISSION BLOCKER | stats.swehockey.se / www.tvmatchen.nu reuse rights unresolved;no new scraping. TVmatchen/api robots restriction retained. |
| Commercial candidates | UNTESTED DATA / PRIOR DOC BLOCKERS | Sportradar coverage/sample/contract needed. API-Sports/Goalserve prior research-tool availability problems were not retested;do not claim origin outage. See SOURCE-READINESS.md. |

Read-only Android contract recheck:BroadcastMetadataSource/Assignment exact identity+expiry;FixtureChannelChoice profile/source ownership;TwitchState;EPG guide-logo overrides. No real profiles or device data read.

## Decisions before Android implementation

1. Review whether channel OK should keep the new detail-first flow or offer a separate direct-play shortcut.
2. Confirm missing-auth Twitch prominence/default on Home and favourite-match versus Continue Watching priority.
3. Validate real remote key repeat,Android focus lifecycle,asynchronous list changes and playback on Chromecast;browser tests cannot prove native behaviour.
4. Resolve licensed complete hockey/broadcast coverage,EPG/artwork rights and authorized profile-data bridge. No verified Watch Now exists without all required contracts.
5. Mock preference/source choices are session-local design state,not real persisted profile settings. Artwork/logos are illustrative,not a licensed live asset catalogue.

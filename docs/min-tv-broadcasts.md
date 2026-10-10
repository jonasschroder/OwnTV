# Min TV Test: automatic SHL channel discovery and in-app selection

This continues PR #3. Package IDs, Android HOME roles, Google TV components and the
single existing IPTV/player pipeline are unchanged. Nothing installs, uninstalls,
clears data, merges the PR or publishes a Release automatically.

## Access and permission review — 10 October 2026

| Source | Actual observation | Decision |
| --- | --- | --- |
| TVmatchen robots | HTTP 200. Ordinary `/match/`, `/ishockey/shl` and `/page/anvandarvillkor` are not disallowed; `/api`, `/go` and several infrastructure paths are disallowed. | Only the allowed public HTML paths are candidates. No API, advertising redirect, app impersonation or authenticated request. |
| [TVmatchen terms](https://www.tvmatchen.nu/page/anvandarvillkor) | HTTP 200. The intellectual-property section says **“Vi tillåter endast användning av sidan för privat bruk.”** Commercial/business use and personal gain are restricted. No automated-access prohibition was found in the reviewed text. | Off-by-default personal, non-commercial HTML experiment, based on the explicit private-use clause and allowed paths. This is an interpretation of those terms, **not an explicit scraping licence or permission for general redistribution/commercial reuse**. |
| TVmatchen API/licensed access | No documented public API or general data-reuse licence was established in the reviewed public terms/About pages. About describes a website widget, which is not an Android data-feed licence. | Keep the adapter replaceable. Obtain written API/data-reuse permission before broader distribution or deployment beyond this personal experiment. |
| [TV4 schedule](https://www.tv4play.se/kanaler/tabla) | The environment proxy rejects CONNECT to `www.tv4play.se` with HTTP 403 before the origin, including its robots request. Terms, feed availability and origin response could not be verified. | No TV4 adapter or private API investigation. Additive TV4 domains were saved in the environment configuration draft; saving a draft does not apply/publish it. |
| Local OwnTV EPG/XMLTV | Existing configured-library and stored-programme DAO reads work in the build. | Always retained; no external sports site is needed for this fallback. |
| tv.nu | Excluded at the user's instruction due to its published collection restrictions. | No requests or scraper. |

The earlier TVmatchen proxy blocker was resolved between tasks. This review reached
its actual origin successfully. Public accessibility and robots allowance alone
are **not** the permission basis: the limited personal experiment relies on the
reviewed private-use clause. The source may withdraw access or change terms.

The reader requires the exact SHA-256 fingerprint of the reviewed terms' normalized
article text. An HTML/policy change stops it for a new review rather than silently
accepting different terms. There is no automatic reset after policy/access denial.
No contacts were messaged, source credentials used, or access restrictions bypassed.

## Behaviour and limits

- Existing **Aktivera experimentell SHL-data** remains off by default. A separate
  **Aktivera experimentell TV-kanalsökning** switch lives in Matchcenter → Inställningar
  and is also off by default. Both switches must be on. Either off means zero broadcast
  requests, including robots/terms requests; turning one off cancels the visible job.
- Only visible Home Färjestad content or a selected Matchcenter fixture can trigger a
  lookup while the app is resumed and the IPTV preview is not loading. No hockey-focus
  preview, service, alarm, worker, WebView, JavaScript execution, server or decoder.
- Only today and the next two Stockholm calendar days qualify; games more than four
  hours past faceoff do not trigger new retrieval. Färjestad is the Home priority.
- Broadcast mappings expire after **two hours**. The SHL listing index is reused for two
  hours, robots for six hours and reviewed terms for 24 hours. Opening Home repeatedly
  reuses the small parsed cache and does not reset a fetch budget.
- Maximum **six requests and three fixture-page requests per two-hour window**, with
  the budget recorded before issuing a request so cancellation/process recreation
  cannot reset it. Opening many games can exhaust this conservative budget; local
  EPG/manual selection still works. Failures back off 2/4/8/16 hours.
- Responses are limited to robots 32 KiB, terms 700 kB, SHL listing 2 MB and each match
  page 800 kB; each call times out after eight seconds. Only parsed assignments/index
  survive, bounded to a 100 kB disk cache and 16 assignments. Full HTML is discarded.
- Redirects are disabled. HTTP 401/403/429, robots denial or changed terms persistently
  stop fetching and clear automatic assignments. Changed/malformed match structure
  returns unavailable with backoff, not a guessed broadcaster. Technical details stay
  in Settings; the main screen says **TV-kanal ännu inte hittad**.

## Exact matching and playback

The public HTML includes a server-rendered fixture record with an explicit UTC date.
For the supplied example, `2026-10-10T13:15:00.000Z` is **15:15 Europe/Stockholm**.
A bare displayed `13:15` is never interpreted as local time. ISO timestamps must have
an explicit Z/offset; Java timezone rules handle winter time and DST.

League, both known canonical team identities, Stockholm date and exact faceoff
instant must agree with the Swehockey fixture. Reschedules, unknown teams, duplicate
valid assignments and different games at the same time fail closed. The normal
match URL is obtained from rendered listing anchors, never generated from team names.
The listing must first provide the same league, team identities, date and explicit
faceoff instant; later rematch pages are not fetched. Its mixed hockey index is
bounded to 64 entries per date and filtered to SHL only, retaining at most 32 keys.
Names must also appear in the rendered match-page channel list. No scripts are run
and no private endpoint is called; bookmaker/hydration extras are discarded.

**TV4 Sport Live 2**, **TV4 SPORT LIVE 2 HD**, **SE | TV4 Sport Live 2** and
**SE: TV4 Sport Live 2 FHD** match. Channel numbers 1/2/3/4/12 stay different.
**TV4 Live 2** and **TV4 Sport Live 2** are not aliases without independent verification;
**TV4 Hockey** is separate. **TV4 Play Hockey** remains a streaming platform and is
never automatically promoted into a linear IPTV channel.

A verified broadcaster is matched to the user's visible configured channel entities.
Multiple source/quality entities remain separate cards. Local stored EPG supplies
fixture evidence only when both teams and timing agree; a generic **SHL** title does
not confirm an overlapping game. Bounded/truncated library searches cannot establish
that only one candidate exists, so they require the picker rather than direct play.

One reliable complete match permits **Se matchen** through the existing playback
callback. Multiple candidates stay in the in-app picker. Missing broadcasters report
**Kanalen finns inte i din lista**. No assignment falls back to local sports suggestions
and deliberate manual search. No stream URL or pay-TV service is invented.

Explicitly choosing a channel saves **Din valda kanal** locally for that exact fixture,
profile, channel ID/source/name until four hours after faceoff. It remains a user
choice, not an externally confirmed assignment. It does not apply to rematches or
reschedules. **Välj annan TV-kanal** and **Glöm kanalval för den här matchen** let the
user change/remove it. Deleted/hidden/profile-inaccessible entries cannot be reused;
playback rechecks the existing policy/PIN gates.

## Remote and layout changes

- **Öppna TVmatchen** and all SHL browser hand-offs are removed. Channel selection
  stays inside Min TV; Downloader is only an installation tool.
- No IPTV channels: a friendly explanation and **Lägg till en TV-källa** open the
  existing source settings. No search input or keyboard appears in an empty library.
- Channel cards and logos precede the secondary **Sök TV-kanal** button. Search input
  is created only on explicit selection. Back follows search → picker → selected
  fixture → previous Matchcenter tab → Home. Playback retains the selected destination.
- Match cards use larger team/time text, Färjestad highlight, available broadcaster
  and direct watch action. Missing information uses concise Swedish copy.
- Standings have aligned Plats/Lag/Poäng columns, plus Spelade/Målskillnad only when
  every row has reliable values. Goal difference is verified against published GF:GA
  and any explicit difference; contradictory values fail parsing. Färjestad position
  and points are prioritised in its tab. No fabricated lineup or live-period data.

## Live-source verification versus device testing

A standalone JVM probe used the **production ExperimentalBroadcastReader and
CompanionHttp**, with the same official OkHttp version's JVM transport (the Android
transport cannot run on a desktop JVM). It made actual HTTPS requests and resolved
the supplied Färjestad–Malmö fixture to **TV4 Sport Live 2 (linear)** and **TV4 Play
Hockey (streaming-only)**. This verifies current live-source access and extraction,
not just saved-HTML tests. The final probe ran at **12:01 Europe/Stockholm** on
10 October and used four requests: robots, reviewed terms, SHL listing and the
exact match page. It does not verify the Android HTTP runtime or the user's
actual configured IPTV library. Detailed final automated results and the downloadable artifact are in PR #3.
The first tightened-index live check failed safely because the page includes 33
entries from mixed leagues; a 16-entry limit was too strict. The corrected 64-entry
bound, strict SHL filter and a 33-entry regression case now pass.

The user physically tested the preceding redesigned QA app on Chromecast Google TV
4K / Android 14: schedule/standings worked, and the browser/empty-picker problems
above were observed. This updated build still needs physical testing:

1. Read the APK signing gate in [min-tv-qa.md](min-tv-qa.md) before updating Test.
   Preserve installed regular Min TV and both applications' data. A new CI debug key
   cannot update a differently signed Test installation; stop on mismatch. No uninstall
   or data-clearing instructions are part of this change.
2. Import an encrypted backup into Test using the existing backup tool, or configure
   a source normally. Keep IPTV credentials and backup/password private.
3. Enable SHL and, only for the personal experiment above, TV-kanalsökning. On the
   Färjestad matchday check Stockholm time, broadcaster and **Se matchen**. A first
   lookup can take several seconds; Home/remote/IPTV remain responsive.
4. With HD/FHD or multiple sources, verify the picker retains separate entries. Try
   a missing broadcaster and generic overlapping SHL EPG. No browser should open.
5. Test an empty library, adding a source, secondary search, TV keyboard Back, fixture
   Back, tab/Home focus restoration, background return and process recreation.
6. Save/change/forget a fixture-specific channel. Ensure it is not reused for another
   game/profile and hidden/deleted channels are rejected.
7. Disable either experiment: verify zero broadcast traffic. Check expiry, parser
   errors and access-denial settings without bypassing a denied live source.
8. Verify real favorites, muted preview, EPG, fullscreen/audio, external shortcuts,
   standings columns, long-running playback, RAM/frame timing/provider sessions, and
   then reopen regular Min TV to confirm its configuration is unchanged.

Download **MinTV-v0.2-QA.apk** from the successful prototype Actions run linked in PR
#3. GitHub delivers a ZIP containing the actual APK, checksum, public certificate and
offline guides. Use [Mac/Downloader installation](min-tv-qa.md#install-from-a-mac-using-downloader)
only for a first install or an update with a verified matching signing certificate.
Stable signing for future regular updates remains [the protected-key plan](min-tv-signing.md).

## Reproduce the build

Use JDK 21, SDK 37/Build Tools 37.0.0 and the repository's wrapper:

```bash
bash tools/prepare-core.sh
./gradlew :app:testStandardDebugUnitTest :app:testQaDebugUnitTest \
  :OwnTV_Core:core:testDebugUnitTest :OwnTV_Core:player-core:testDebugUnitTest
./gradlew :app:lintStandardDebug :app:lintQaDebug
./gradlew :app:assembleStandardDebug :app:assembleQaDebug \
  :app:assembleStandardDebugAndroidTest :app:assembleQaDebugAndroidTest
```

Core remains pinned at `adca2bcd653f19e6e5d659c2722309e6aef4ec15`. Parser/reader
unit tests use saved, minimized public-HTML fixtures and fake transports; the normal
test suite makes no live sports-site requests. The live-source probe described above
was a separate explicit research check. Instrumentation APK compilation does not
prove that the seven routing/identity/backup tests per identity ran on a device.

## Actual local results — 10 October 2026

- **1,708 unit-suite validations pass:** app 249 regular + 249 QA; Core 939;
  player-core 271. Zero failures/errors/skips. Unchanged Core/player outputs were
  reused locally; Actions invokes all suites. This follow-up adds 28 tests per app
  variant: saved HTML/time/identity/robots/mixed-index cases, fake-transport cache,
  access-stop/cancellation/budget cases, search Back, fixture choices and visibility.
- Both ARM debug APKs assemble; both device-test APKs compile. Instrumentation was
  **not executed** because no Android device/emulator is attached to this environment.
- Both lint targets pass: **0 errors, 98 warnings, 22 hints** each (previous baseline:
  100 warnings). No lint baseline/suppression added. The new synchronous-preference
  KTX suggestion is retained because the request journal must check commit success
  before network access, and runs on Dispatchers.IO.
- Identity/authority/custom-permission/component/link isolation, no HOME, ARM32+ARM64,
  APK signature and QA debug pseudolocale checks pass. Literal ratchet, number locale
  and text overflow checks pass; existing 45-entry translation debt unchanged.
- Clean QA size: **89,392,836 → 89,481,844 bytes**, **+89,008 (+0.100%)**. Raw DEX
  +177,900 bytes; **all 26 native libraries are byte-identical**. No new runtime
  dependency, media player, decoder, service, server or parsing framework. APK size
  is not a physical RAM/frame-time measurement.
- Local APK SHA-256: `339b80e2d57dd861fe1558f18d3c7b419e87de60509257aa15cb4357a2cc17ee`.
  Local signer SHA-256: `2b8407729a5bf707603756248a9873a295718f39f1cb52061cb84979081c86b1`.
  The CI artifact has its own checksum/certificate: **do not substitute local values**
  or assume the installed Chromecast uses this key.
- Initial local literal checks rejected new technical keys until they were reviewed
  and classified; two test-fixture mutations were corrected. The tightened-index
  live probe failed safely on 33 mixed-league entries, then the corrected bounded
  parser and final four-request live probe passed. None of those failures was hidden
  by disabling tests, access checks or assertions.

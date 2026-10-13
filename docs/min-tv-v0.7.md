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

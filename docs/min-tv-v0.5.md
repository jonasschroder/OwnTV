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

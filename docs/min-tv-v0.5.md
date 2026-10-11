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

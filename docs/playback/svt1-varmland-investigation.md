# SVT1 Värmland: independent service selection

Baseline: main `a3f04fbfa29869e272abbfae7522c1389c45a80a` (v0.14).

## Confirmed cause and minimal fix

`preferVarmland()` removed national SVT1/2 when a regional group had one provider
and no saved national preference. Conversely, saved national preferences or
multiple regional providers removed the regional group. Neither condition proves
stream health. `SwedishChannelStore` now retains both independently recognized
services unless the owner explicitly hides one. No national stream is relabelled
or used as a regional fallback. Existing order, source preferences and profile
ownership remain intact; no migration or preference write is needed.

`SwedishChannels` already distinguishes strict national and regional labels,
including quality/country/diacritic variants. `ChannelSourceResolver` keeps saved
quality choices within that exact group. Missing saved FHD can select an available
regional variant; an absent regional group remains unavailable. It must not select
national SVT1. Provider-exact Guide/library requests keep their actual entity.
The v0.14 LIVE_ENTRY ladder now records finite regional identities instead of OTHER;
no provider names, URLs, credentials or profile IDs are added.

## Entry paths and evidence

Home, curated TV and its source chooser share the bounded Swedish library. The
full IPTV list and EPG retain provider-exact entities. All 12 LIVE_ENTRY origins
are exercised with national SVT1, SVT2, TV4, regional HD and regional FHD through
real Activity/Room/Koin and a synthetic loopback decoder fixture in existing CI
startup tests, at 1080p and 720p. This is application-routing/decoder evidence,
not proof of the owner's provider stream or authorization.

Local validation: 37 targeted JVM cases passed; QA instrumentation Kotlin/Java
compiled; loopback HTTP range smoke passed; i18n inventory passed (unchanged 45
hardcoded baseline). Native execution is delegated to the existing complete CI.

Physical Chromecast: existing QA 0.14.0-beta.1 (1000020) was reachable by ADB.
The TV grid exposed the regional service. The device reported Asleep; wake/open
and playback attempts did not yield reliable playback telemetry. No new APK was
installed. The actual selected provider/HD-FHD inventory, authorization response,
stream decoding and cross-view playback remain UNVERIFIED. This change fixes a
proven visibility defect, not a proven provider-stream failure. Private diagnostic
files remain outside Git.

## Owner QA after an approved signed update

Check national and regional cards are independently visible. Play regional HD and
FHD explicitly from the IPTV list, then compare TV, Home and Guide; confirm the
actual regional programme and sound. If only a provider variant fails, capture
its finite LIVE_ENTRY stages and redacted HTTP/decoder error. Do not save national
SVT1 as an automatic regional substitute. Existing source choices should survive.
Rollback: revert this code change; no schema/data changes require reversal.

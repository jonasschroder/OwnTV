# Min TV v0.10 — navigation and Smart Home proposal

**Design review only.** No production Kotlin, resources, database, dependencies, signing or release
workflow is changed. No Android package was installed, removed, opened or cleared for this work.

Reviewed application: `main` **7c4411d5280b439b7fb7916eade3d80dc2baf2ff**, including merged v0.9.1
reliability work. The owner-accepted physical production baseline remains **Stable v0.9.0 / 1000011**.
Those are different baselines: this proposal does not claim v0.9.1 was installed or accepted physically.
Pinned Core: `adca2bcd653f19e6e5d659c2722309e6aef4ec15`.

## Review the deliverables

- [Architecture, focus behavior and implementation phases](architecture.md)
- [Complete destination migration map](destination-map.md)
- [Channel/source and subtitle assessment](playback-and-preferences.md)
- [Validation and remaining decisions](validation.md)
- [Interactive offline preview](prototype/index.html): open locally in a browser; GitHub renders its
  source rather than running it. The prototype is a design tool, **not a WebView to ship in the app**.

The preview supports arrows, short Enter, held Enter (500 ms), Escape/Back, top navigation,
remembered content focus and a source picker. Use the review controls above the TV canvas to compare
Home alternatives and empty/offline/missing-EPG states. Everything is synthetic. It never reads a
playlist, plays video, signs in, fetches sport/EPG or changes installed-app data.

## Recommendation for approval

Choose **Lugn start**: a restrained hero, eight grouped Swedish channels in a single row, followed
by genuine resume items. Choose four top destinations, explicit OK/Down activation, and Profile as
an always-reachable upper-right hub. Preserve a constant header layout footprint while reducing its
emphasis in content focus. Do not automatically activate destinations on a focus dwell.

First implementation milestone after approval: **v0.10 M1 — navigation contract and native Compose
shell adapter**, preserving all current destination IDs and existing content screens. Add the complete
migration/focus restoration tests before hiding More. No Home data rewrite, decoder change, VOD
cross-provider merging, schema migration or device upgrade belongs in M1. Then implement the selected
Home layout and shared source resolver in small separately reviewable steps.

## Two Home directions

### Lugn start — recommended

![Lugn start: wide hero and eight-channel row](mockups/home-cinema.jpg)

A single stable Play action is prominent. Eight current-channel cards fit across 1080p without a
second row of nearly identical favorites. Generic Swedish order is independent of favorites. The
optional resume strip shows only actual positions; sport/Twitch are absent. Original user choices
for hidden rows/order must be adapted, not silently discarded.

### Kanaler först — alternative

![Kanaler först: split hero and channel matrix](mockups/home-split.jpg)

A narrower, tall hero leaves eight larger channel tiles in a 4×2 matrix. This deliberately challenges
the horizontal-row assumption: easier title reading, but more vertical focus crossings, greater
channel dominance and weaker cinematic balance. Do not ship both layouts as a new configuration
matrix initially. Use this alternative to validate the eight-card density before committing to A.

## Destination previews

| TV | Sport |
| --- | --- |
| ![TV](mockups/tv.jpg) | ![Sport](mockups/sport.jpg) |

| Film & serier | Profil & inställningar |
| --- | --- |
| ![Film and series](mockups/library.jpg) | ![Profile and Settings](mockups/profile.jpg) |

[Source picker](mockups/quality-picker.jpg) · [Guide](mockups/tv-guide.jpg) ·
[No history](mockups/home-cinema-no-history.jpg) · [No EPG](mockups/tv-no-epg.jpg) ·
[Empty TV profile](mockups/tv-empty.jpg) · [Offline Sport](mockups/sport-offline.jpg)

All twelve previews are **1920×1080**. Channel wordmarks are schematic text, not new logo assets.
Programme names, sources and VOD artwork are synthetic examples, not a claim about the owner's
actual lineup or content. Sport intentionally shows honest schedule/availability states rather
than inventing a match. Production logos/artwork must come from authorized existing provider/cache
sources, with readable text fallback. No private device screenshot, IPTV data or original app APK
is included in the PR.

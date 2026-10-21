# Final design iteration — owner review

This document supersedes conflicting layout, library and picker decisions in iteration 1.
It is a product specification and synthetic interactive preview, not implemented Android behavior.

## Recommendation and comparison

Recommend **Din kväll**, the revised Lugn start. The 350 px decorative hero becomes a 302 px
programme composition. Its right side now answers “what is next?” or provides a contextual return
to live TV when a series leads. No invented programme imagery or automatic preview stream is used.
Provider-authorized cached artwork may replace the neutral identity panel only after availability,
image rights, crop and cache limits are verified; the no-art layout is complete on its own.

At 1920×1080 the header, primary playback action, current programme and next programme, eight
channels, row navigation and three saved resume items fit above the footer. Channel titles remain
22 px and wrap to two lines; full accessible names survive truncation. Focus uses the existing
strong turquoise treatment without scaling cards or shifting their neighbours.

**Kanalbordet** is a genuinely different alternative: a narrow editorial/action column alongside
a four-by-two channel matrix. It prioritizes channel scanning rather than a programme hero. It
has larger cards but more vertical crossings and gives the latest programme less context. Recommend
Din kväll for the first implementation; do not add another permanent layout preference yet.

| Comparison | Previous iteration | This iteration |
| --- | --- | --- |
| Hero | Decorative right half, 350 px | Programme/next split, 302 px, useful actions |
| Now row | Eight cards, no further channels in fixture | Eight cards, edge scrolling, two additional fixtures |
| Density | 214 px cards | 193 px cards; same 22 px programme text |
| Series | Generic resume strip | Dedicated active-series hero state |
| Library | Film/Series and Profile entry duplicated | Profile owns library; contextual shortcuts only |
| Variant OK | Save, close, then another OK to play | Save and simulate playback immediately |

[Previous Lugn start](mockups/previous/home-cinema.jpg) ·
[Previous alternative](mockups/previous/home-split.jpg) ·
[Six-versus-eight comparison](mockups/home-compare.jpg)

Six cards provide about 285 px each versus about 210 px for eight, with unchanged text size.
Eight exposes the entire requested default sequence before horizontal input. Six reduces wrapping
but hides Sjuan/TV8 initially. The comparison shows both at the same scale. Recommend eight,
subject to physical sofa-distance review and Android font/overscan checks before implementation.

## Deterministic Home selection contract

Proposed policies below are explicit review decisions, not claims about current Android behavior.
Build a profile-scoped snapshot from existing history/progress, accessible sources, parental rules,
favorites and current EPG. No recommendation service, random artwork, new sports fetch or tracking.

1. Eligible resume: existing incomplete flag, positive saved position, known positive duration,
   position below 90% of duration, playable exact item in current profile. Unknown duration does
   not invent a percentage; keep accessible through History, outside automatic resume promotion.
2. Pick latest eligible item by last-played timestamp descending; stable item identity breaks ties.
   A series episode is “active” for hero promotion if played within the past seven days and newer
   than the latest accessible live-TV history event. No dwell-time timer can promote it mid-browse.
3. Hero: active episode above; otherwise most recently watched accessible live logical channel;
   otherwise latest eligible resume item; otherwise first available Swedish channel in saved order
   or default order. With no source, show Add source, not fake channels. Unavailable history is skipped.
4. A live hero shows current EPG selected using now in the existing time zone, and actual next event.
   Missing/stale EPG shows channel identity and an honest missing-info message, no fabricated times,
   title, progress or next event. Playback remains enabled. Series hero resumes exact saved episode.
5. Hero companion: actual next EPG event for live; for series the last accessible live channel if
   present, otherwise episode context and episodes shortcut. No-history offers TV discovery.
6. Now row is always the first content row when channels exist, independent of favorites. Group
   logical quality variants once. Preserve explicit saved order/hidden settings; otherwise order
   SVT1, SVT2, TV3, TV4, Kanal 5, TV6, Sjuan, TV8, then remaining Swedish channels by existing
   canonical order and stable identity. Missing EPG never removes an accessible channel.
7. Resume row follows Now, only when eligible items remain after removing the hero item; order by
   last-played descending then stable identity. Show at most three above the fold; further items
   remain in History. Deduplicate by verified content/episode identity, never fuzzy title matching.
8. Do not add a second live channel/favorites recommendation row on Home. A channel may appear in
   the hero and its fixed Now slot, but never in repeated channel rows. Favorites live in Profile;
   TV's Favorites filter is a contextual view of those same records, not a second store.
9. Optional installed-app shortcuts follow content below the initial viewport. Sport, team cards
   and Twitch stay in Sport. No large hockey panel on Home.
10. Freeze module membership/order, hero identity and focused logical key for the current Home
    visit. Update EPG text/progress in place, without reordering or moving focus. On return from
    playback/re-entry create a new snapshot and restore focus by stable key; if gone, use adjacent
    retained key, then section heading/primary action. Profile change discards previous focus/data.

Fixture limitation: the prototype exposes explicit scenarios, not a real clock/history repository.
The active-series fixture has no additional eligible resume items; “no progress” hides resume;
no-history still has playable synthetic channels. Production reducers need boundary tests at seven
 days, 90%, midnight, source removal, profile switching and refresh while focused.

## Remote interaction and source preference

Short OK on an ordinary live item immediately plays its resolved current preference. Long OK
opens available provider variants; release must not also play. A source preference is scoped to
profile + logical channel, shared by Home/TV and only identity-compatible Sport entries.

In the picker: initial focus is saved variant. OK on a variant atomically validates availability,
saves that source preference and requests playback once. “Save marked version as default — do not
play” is a secondary action: highlight a variant, press Right to that action (Left returns to the variant), OK saves and returns
focus to caller. Back cancels without any write. A disappearing provider must show an explicit
error and leave preference unchanged; never silently select another account/source.

Ordinary playback failure after a successful save retains the preference and follows the existing
working engine fallback. Storage failure must prevent the save-and-play success claim and offer
retry or explicit play-once. No credential, URL or token enters diagnostics.

EPG/catch-up/recordings with exact source/event identity preserve it. Where a variant cannot be
proven equivalent, hide the preference-changing selector and retain the exact action. Sport must
preserve verified fixture/broadcaster mapping. Never redirect scheduled recording identities.
Movie/series long OK retains existing context menus. These constraints supersede the old picker’s
save-only OK rule in playback-and-preferences.md.

Now-row Right at last visible card scrolls one logical card and focuses the next; Left reverses,
without wrap. Explicit More/Previous controls also work. Up/Down uses spatial neighbours, and Back
returns to top navigation; Down restores the remembered item. Refresh never steals that focus.

## One library home

Profile (upper right) is the canonical home for Favorites, History, Recordings and Downloads.
Film & serier has For you / Movies / Series / Search; remove its duplicate Mitt bibliotek tab.
Resume cards are contextual links into exact content, TV Favorites is a filter, TV Recordings
opens the canonical recording list, and legacy More aliases remain until all mappings are tested.
Settings stay in Profile with every existing settings destination retained (destination-map.md).
The original migration map remains authoritative for feature coverage; its duplicate Film/Series
library entry is superseded here. No data/store migration or preference deletion is proposed.

## Performance and approval gates

Use existing cached logos/EPG/history, bounded resolvers and stable Compose keys. One snapshot
reducer per visit, EPG updates limited to visible cards, no second player, no autoplay preview,
no video/network work on focus. Optional supported muted preview is deferred until decoder/player
ownership and stop-on-leave behavior are proven. Current no-art recommendation needs none.

Before native implementation: owner approval of eight-card density, active-series precedence and
seven-day policy; verify current progress completion semantics; confirm exact Sport/EPG selector
eligibility; measure TV sofa readability, TalkBack and overscan; test persistence/save failures.
No production code, builds, signing, installation or device data were touched in this iteration.

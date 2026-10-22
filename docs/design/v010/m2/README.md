# M2 — native Din kväll Smart Home

Branch `feat/v010-smart-home` starts at approved M1 merge `4938472caaffe0044cdc8eaaaa6bb4a65413a8c6`.
Before implementation all seven post-merge checks passed, including the real startup, native
D-pad, upgrade, build and i18n checks (runs 39765878465 and 39765878519).
Visual reference: `../iteration-2.md`, `../mockups/home-cinema.jpg` and active-series/empty variants.
This is native Compose, not a rendered HTML view. Final emulator comparisons and CI links are in the PR.

## Ownership and scope

- `MinTvContentHome`: per-profile/per-visit snapshot, current source/visibility authorization,
  existing live playback and exact movie/episode callbacks. No preview surface, sports/Twitch
  composition, metadata discovery or new recommendation API. Sport keeps its existing modules.
- `SwedishChannelStore`: existing bounded logical grouping, canonical Swedish order, hidden/order
  and preferred variants. Up to 48 logical channels resolve per visit; eight fit the initial row,
  additional channels scroll without wrap. No favorites are inserted or reordered by Home.
- `rememberHomeDashboardGuide` / `SwedishRowCache`: existing visible-key window (16 keys, cache 32),
  local stored now/next and existing EPG shifts. Only visible channels plus two lookahead and hero
  are requested. Missing/expired EPG never manufactures a title, time, progress or next event.
- `SmartHomeReader`: the same Room bundled connection and exact-item DAOs. Projects at most 96
  positive-progress candidates from active, section-enabled, profile-linked sources, then checks
  category/adult visibility, hidden items and playable stream presence. No stream probing.
- `SmartHomeContent`: stable logical Compose keys, eight compact channel cards, a split 302-unit
  minimum hero, and up to three actual continuation items. Scrolling remains available for larger
  fonts. Focus outlines do not scale/resize cards. Previous/More also move into the revealed card.

No persisted enum, schema, favorite, history, source/quality preference, signing/update contract,
shared player, subtitle engine or source picker is changed. M3 and M4 are excluded. Legacy Home
layout flags/order remain stored; M2's approved fixed composition supersedes those legacy rails.
Their settings are not rewritten. Favorites/History/Downloads/Recordings/settings remain in Profile.
The old Home version/customization shortcuts are available through their existing Profile pages.

## Selection contract and explicit decisions

1. Resume requires an exact accessible movie/episode, saved position > 0, known duration > 0,
   position strictly below 90%, and not completed. The database has **no stored completion flag**;
   Core derives completion at 95%. Home's stricter 90% display rule does not write completion or
   erase positions. Unknown duration and 90–95% items remain in History.
2. Core's shared launcher planner has a ten-second floor and NEXT-episode synthesis. Reusing that
   filtered list would silently violate M2's positive-position/exact-episode contract. Home instead
   uses the existing bounded SQL reader and DAOs, retaining Core's launcher behavior unchanged.
3. Sort newest progress timestamp first, then exact source/remote identity (catalog row ID if remote
   ID is absent). Deduplicate exact identity only, never titles. Multiple unfinished episodes of
   the same series are distinct actual progress entries; there is no unapproved next-up inference.
4. Only the **latest eligible resume item**, if an episode no older than seven days (inclusive),
   newer than the most recent accessible live watch, may replace the live hero. Future timestamps
   cannot promote a series. With no live history, latest eligible resume wins regardless of age.
5. Otherwise use most recent accessible live history (bounded existing 20-event reader), then first
   available Swedish channel in saved/default order. No source means Add source; a source with no
   playable channels/progress keeps TV discovery available. Unavailable items are skipped.
6. Promoted resume is removed from the resume row by exact key. A promoted series retains the last
   accessible live channel in its companion. A live hero gets actual current/next EPG. No previous
   live channel means real episode context, not a made-up broadcast.
7. Membership/order/hero freeze for a Home visit. EPG clock/invalidation updates metadata in place.
   Return from playback/app resume rebuilds the snapshot. Profile/source/access changes invalidate
   it; another profile cannot see the old snapshot. Play revalidates current access and exact IDs.
8. Focus is remembered by logical channel/content key. Re-entry uses the retained key, nearest
   retained neighbour, then primary action. The new fixed Home has no duplicate favorites/Now rail.

Bounded-reader limits are performance limits, not deletion policies: older progress outside the
96-row window and live history outside 20 candidates remain accessible through History/TV. A
future paged history candidate API can improve coverage without catalog-wide Home reads. Sofa
readability, overscan and external-provider playback still require separately approved physical QA.
Optional installed-app shortcuts are deferred; no network work is added to compensate for empty rows.

## Verification

JVM tests cover eligibility/completion boundaries, deterministic ties, exact identity, seven days,
newer live/movie precedence, midnight/re-entry and removed-key fallback. Required emulator cases
cover eight-card geometry and scrolling, EPG refresh/midnight focus, header focus restoration,
missing EPG/no-source/profile isolation, promoted series and read-only exact stored progress.
The real QA startup fixture includes canonical channels, actual synthetic Room EPG, three saved
progress rows and profile/source settings. It captures native production Home at 1920×1080 and
1280×720, and retains all existing native route/startup/upgrade regressions. Synthetic fixtures
are test-only; production never creates programmes, artwork, channels or viewing history.

No owner-device installation, signing, distribution or merge is authorized by this PR.

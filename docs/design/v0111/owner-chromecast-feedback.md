# Min TV v0.11.1 — owner Chromecast feedback / implementation acceptance

**Source:** physical v0.11 QA test, 26 October 2026. **Baseline:** main `7d5dd1f9a6157d0db2038c1ff5d49073ff0fb961`. **Development:** `feat/v0111-navigation-hockey`. User likes Home layout; Film & serier should stay as-is. Implement **native Android**, not mock design; do not touch Stable, sign, merge or publish without separate approval.

## Home: retain approved layout, improve utilities

- [ ] Under the compact OhnePixel surface, show **two separate, compact, remote-focusable Android TV app shortcuts**: **SVT Play** and **Telenor Stream**, using installed app logos where possible. Verified Google Play package IDs: `se.svt.android.svtplay` and `se.telenor.stream`. Use Android `PackageManager.getLeanbackLaunchIntentForPackage()` / regular launch fallback; do not hardcode component activity. Add appropriate targeted `<queries>` only if needed. If missing, display a Store/install action with safe fallback; never crash or steal navigation focus. Back from launched app returns correctly.
- [ ] Move version text from beneath OhnePixel to a discreet right-aligned part of the **global top bar**, visible on all destinations; no duplicate version label on Home. Use real `BuildConfig.VERSION_NAME`; status remains visible in Settings > About.
- [ ] Add a compact **Update EPG** action in the top bar; call the **existing guide refresh/sync owner** (not a parallel importer), with progress, completion/failure, last refreshed time, guarded manual throttle and profile/source ownership. No network operation on focus or repeated click; no deleting/resetting guide. Preserve focus on completion and across section changes.

## TV: remove unnecessary stacked menus

- [ ] Do not render secondary top tabs `Svenska kanaler / Programguide / Sök` above the TV section. Preserve Guide and Search as reachable direct actions in a single consistent surface (global header utility or settings/search route), not stacked subnavigation.
- [ ] Within the five-column TV grid keep **one simple collection selector**: `Svenska kanaler`, `Hockey & sport`, `Övriga svenska kanaler`. Remove `Nyheter` and `Barn` from curated selector, **without hiding/deleting their actual IPTV channels from All channels**.
- [ ] Remove the internal grid header's `Alla kanaler / Anpassa samlingen / Programguide` action rail. Keep source preferences, library/category browse, customization and full EPG reachable through a deliberate Settings or global single-level path. No functionality/data loss, hidden changes, profile ownership or saved focus regression.
- [ ] Under `Svenska kanaler`, prefer a genuine **SVT Värmland** regional variant for SVT1/SVT2 *only if the authorized provider has a uniquely identifiable real stream/EPG regional service*. Never rename ordinary national SVT channels to pretend Värmland; fall back to existing channels. Honor explicit user source preference before automatic selection; no duplicate regional cards.
- [ ] `Hockey & sport` canonical ordering: `TV4 Hockey / TV Hockey`, then `TV4 Sport Live 1...N` in **numeric** order, then `TV4 Sportkanalen` and other TV4 sport relevant options, then Eurosport 1/2 and remaining recognized sport channels. Do **not** alphabetize Eurosport ahead of hockey or put Live 10 before Live 2. Actual channel library and preferred HD sources remain authoritative.
- [ ] In `Övriga svenska kanaler`, show channels with **valid current EPG** first, then valid next EPG, missing/stale programme data last. Do this on already available cached/bounded metadata; do not fetch EPG for all channels just to sort. Stable order within buckets, avoid disrupting focus or card position during active browsing. Do not equate absent EPG with channel cannot play.

## Sport: fixtures + identified broadcast channels

- [ ] Remove the `Bläddra bland hockeykanaler` CTA from the hero and channel rail. Make main hero / upcoming fixture **OK open match details**, with visible time, opponent, competition and available source status. If exact authenticated IPTV channel assignment is verified, show `Se på TV4 ...` and play. No autoplay or generic browse button when not verified.
- [ ] Remove direct `Inställningar` from the Sport hero. Keep team and hockey-source settings under the **single global Settings** location, preserving profile/team preferences.
- [ ] `Dina hockeykanaler` should show ALL actual relevant TV4 channels from the profile's IPTV source: `TV4 Hockey`, `TV4 Sport Live 1..N` (and accepted aliases), then other hockey-relevant sources, not just TV Hockey. The current `HockeyChannelOrder.kt::hockeyChannelNumber` regex misses `TV4 Sport Live N`; update it and related actual candidate discovery. The recognized Swedish `known` list currently hardcodes TV4 Sport Live 1..4; support the real provider's extra numbered variants, avoiding false positives such as Highlights, Replay and unrelated services. Keep channels logical, deduplicate quality.
- [ ] **Broadcast lookup:** review existing `TvmatchenBroadcastSource`, `ExperimentalBroadcastReader`, current opt-in, robots/terms gate, failure budget and `BroadcastResolver`. Prior failure is not proof broadcast info does not exist. Investigate permitted/licensed feeds (TV4/league/EPG authorized providers) as primary paths; **do not bypass robots, terms, HTTP blocks or scrape prohibited endpoints**. Where a current schedule lists `TV4 Play` as streaming platform, distinguish platform from an **exact linear TV channel**.
- [ ] First improve **local EPG fixture-to-programme matching** across real TV4 Hockey and Sport Live channel variants: exact fixture/team IDs, date/start, fresh event title/description, timezone and correct profile source. Verified full match evidence may label a linear channel; weak string similarity can only show `Möjlig kanal` (not verified/auto-play). Keep manual assignments explicitly labelled unverified. Never invent a broadcaster, scheduled match or live score.
- [ ] Display source/evidence and last update in match details; offer channel information on each upcoming match when known. If source is unknown, explain **where detection failed** (no matching EPG, no permitted broadcast metadata, channel absent, source disabled), not simply `Ingen kanal bekräftad`.
- [ ] Preserve current next-match selection, upcoming fixtures, and existing SHL/HA fetch limits; no standings table.

## Profil & Inställningar

- [ ] Replace the awkward `Profil` destination showing a secondary side-navigation then another Settings page with one coherent 10-foot UI. Suggested global destination: **Profil & inställningar**. Header shows current profile and switch, followed by clear categories in one left menu; right panel shows the active category (not another menu stack).
- [ ] Categories chosen by actual repo features: Profil, IPTV & EPG, Sport & lag, Uppspelning & undertexter, Utseende, App & uppdatering, Avancerat. Don't invent unavailable sections. Keep discoverable search, provider management, backup/restore, parental controls, history, downloads, Twitch settings, diagnostics and existing deep links, but locate each action once.
- [ ] Back = detail → group → top navigation; focus and scroll return deterministically. No deleted or reset user preferences, profile-specific data, backup migrations or setting IDs.

## Release notes: owned by assistant/Codex, **never the user**

- [ ] Correct the existing public v0.11 QA release `qa-1000014` whose GitHub description is `qwert`. Generate truthful release notes from verified commits/tests and change only **release description**, not APK, tag, assets, signed manifest or metadata. If connector cannot edit release, Codex may use existing authenticated `gh release edit qa-1000014 --notes-file ...` after verifying correct repo/tag/permissions.
- [ ] For every subsequent QA/Stable release, **author Swedish release notes from actual merged commits and tests** as part of development, including limitations and known issues. Never request the user to write a description or copy-paste arbitrary placeholder. Respect protected manual sign/distribution workflows; either prepare a reviewed release-notes file for the existing required field or carefully implement an audited safe default that enforces substantive non-placeholder notes. No bypass of release guard.
- [ ] Do not claim the release is tested on physical hardware until it has been. Provide a clear user-visible changelog for v0.11.1.

## Definition of done

- Real Kotlin/Compose implementations and tests, not a new HTML designlab or documentation-only PR.
- D-pad navigation & Back from all views and launched apps. Tests for missing apps, SVT region absent/present/explicitly overridden, >10 TV4 Sport Live channels, EPG-sorted Others, stale guide, multiple profile data, disabled broadcast source, manual unverified vs evidence-confirmed TV, and settings deep links.
- Real native 1080p/720p capture, correct font-size/focus, existing version/update UX; latency acceptable on Chromecast Google TV 4K.
- Full existing unit/emulator/CI/i18n/signing-upgrade/distribution checks. Never weaken tests.
- Keep branch + PR draft until owner reviews, then explicit approval for merge, separate QA sign/publication/installation. Stable unchanged.

## Product constraint

Primary goal is an easy personal media launcher, not another exhaustive IPTV player settings surface. `Film & serier` currently satisfies owner; touch only if compatibility requires it. Existing v0.11 Home hierarchy is approved — improve small controls, not redesign hero or main sections.

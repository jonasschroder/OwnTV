# Home remote navigation and initial TV4 Hockey favorite

This PR keeps `se.jonasschroder.mintv.qa` and `se.jonasschroder.mintv` separate.
It does not register HOME, change Google TV settings, install/uninstall apps, clear
user data or change IPTV credentials. **The workflow QA APK is a validation build,
not an in-place update of an app signed with a different certificate.** Follow
[min-tv-safe-qa-update.md](min-tv-safe-qa-update.md) first; keep the populated Test
installation until its real encrypted backup and compatible signing key are verified.

## Remote navigation

Min sport is one full-width TV button. With no teams, OK opens the existing team
picker; with teams, OK opens Matchcenter. Fixture information is inside this card.
The old separate far-right action and nested matchday Home buttons are removed;
watch/channel-selection actions remain available in Matchcenter. The Home card
remains an entry point when prominent sports information is disabled.

Up/Down explicitly moves between Live TV/Guide, IPTV favorites (or its empty
card), Min sport, Twitch and app shortcuts. Horizontal navigation stays within the
IPTV/app rows. The target lazy item is scrolled into composition before focus is
requested. The selected section and list scroll are saveable. Back from the team
picker or Matchcenter restores the sports card. Return from fullscreen or another
application restores the selected section without arming playback automatically.
The existing team storage, PIN/profile gate and player engines are unchanged.

## Initial default favorite

After a completed initial source import, the active profile can receive one
configured linear `TV4 Hockey` entry if its Live TV favorite table is empty.
The existing country/quality normalization accepts `TV4 Hockey SE`, `SE | TV4
Hockey HD` and FHD variants. It retains broadcaster numbers and rejects Sport
Live 1–4, TV4 Play, PPV, EXCLUSIVE and NO EVENT STREAMING. Only a nonempty
provider-configured stream entry in the selected profile's active Live sources,
allowed by kids/hidden-item/hidden-category policies, is eligible. Visibility
checks capture the expected profile explicitly, including when sources are shared
and the Live view-model context is still changing. No stream is
opened to test availability or resolution. An explicitly named HD variant wins,
then FHD, then other variants; ties use source/provider order and row ID.

The local FTS lookup is bounded to 129 entries. A truncated candidate set is
left for manual selection. No provider/network request is made for this lookup.
Favorite insertion uses Core's FavoriteDao in its driver-native write transaction.
It re-reads the profile, active source links, channel and existing favorite IDs
before insertion. Existing favorites and their order/timestamps are untouched.

An app-private durable claim is keyed by profile ID **and creation timestamp**;
it contains no channel list, credentials or stream URL. Existing favorites also
consume the initial offer, including during import. Retained Core Live-favorite
deletions also consume it, preserving an earlier manual choice. Source IDs and
visible favorite IDs are observed so playlist/visibility changes invalidate the
Home snapshot immediately. The claim is committed before the insertion, under the
serialized database writer. A failed durable write inserts nothing. A crash or
transaction rollback after the claim can skip this optional convenience, but
cannot cause repeated additions. The database transaction and preference write
are not claimed to be one atomic cross-store transaction. Manual removal,
relaunch, playlist re-import and source/profile switching never reset the claim.
A missing/hidden candidate does not consume it, so a later completed import can
supply the first default. Re-created profiles are separate owners.

Same-signer upgrades preserve the claim. It is outside Core's `.own` backup,
like the new sports preferences. A deliberate clean-sandbox restore of an empty
favorite list can therefore receive an initial default again; record the user's
intent during migration. No migration or restore is run automatically.

Room's existing favorite invalidation refreshes Home. The same selected channel
feeds logo/now-next EPG and the existing single muted preview. Initial, imported or restored
focus updates metadata only (earlier loading-card navigation is disarmed); preview requires deliberate remote navigation, and
fullscreen promotion uses the existing engine.

## Empty states

- Initial profile/database/source observation or active Live-channel synchronization: loading,
  never an Add Source prompt. An incomplete first import gets a 30-second grace;
  afterward its available local channels are shown, or source management is offered.
  The default is deferred until `lastSyncAt` confirms a completed import.
- No active usable Live source: welcome and Add TV source.
- Source with visible configured channels, no favorites: Choose favorite channels;
  the action opens the existing Live TV browser, not Add Source.
- Source with zero usable/visible channels: explain synchronization/visibility and
  open source management. Other profiles' source/channel entries are not offered.
- Auto-added TV4 Hockey: normal favorite Home immediately after observable refresh.

A configured source with Live TV explicitly disabled is outside the active Live
source set. Switching active playlist follows OwnTV's existing source filter.

## Validation and device acceptance

`HomeChannelDefaultsTest` covers actual channel-name wrappers, broadcaster
exclusions, declared quality/deterministic selection and all empty/loading states.
The existing preview-controller tests cover no initial autoplay, muted-owner
handoff, debounce/cancellation and fullscreen promotion behavior.

`HomeDpadTraversalTest` dispatches real Android D-pad Down/Up/Center and Back keys
through the production sports card and section-navigation modifier in a scrolling
Compose LazyColumn, in both empty-team and multi-team states. This is a synthetic
navigation harness, not proof of the complete Chromecast/player lifecycle.
`HomeChannelDefaultsStorageTest` uses synthetic profile/sync flows (no process-global Settings DataStore or WorkManager database), durable
claims and a synthetic in-memory Room database with the production bundled SQLite
driver. It checks existing favorites, one-time insertion, manual removal, store
recreation/re-import, missing/hidden/foreign-source candidates, profile isolation, context-not-ready loading
and available-channel/no-favorite observation after manual removal.
The CI emulator job runs these tests using the existing x86_64 flavor; no device
IPTV credentials or backups are supplied.

Physical Chromecast checks still needed: all rows reachable without focus traps;
Back to Min sport and retained scroll; fullscreen and external-app return;
actual source-import and failed-sync timing; real logo/current/next EPG; one muted
preview, provider connection limits and unchanged fullscreen playback. Optional
profile convenience remains available through Settings → Profiles → Mina lag.

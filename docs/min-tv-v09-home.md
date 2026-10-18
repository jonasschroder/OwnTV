# Min TV v0.9 · Premium Home UI polish

The baseline is main `5326c641` (v0.8.1), which the owner verified on a physical
Chromecast with Google TV. This PR changes Home presentation only. It does not
create a signed QA candidate, bump a version, install an update, or change the
permanent update infrastructure.

## Design

- The last-watched hero sizes to its real content. The channel, programme, real
  times, bounded progress and Watch action form one reading order; Next stays in
  the same column. A low-opacity existing channel logo adds depth when available.
  Missing/broken artwork falls back to the navy gradient and readable channel text.
  Stored EPG has no programme-image field: no poster or programme title is invented.
- The existing preview slot is rendered only for the existing active preview engine.
  Its activation, lifecycle and single-stream controls are unchanged. No extra
  player, image-analysis work, blur, polling or metadata lookup was introduced.
- Hockey and Twitch use comparable compact surfaces and stable widths. Hockey's
  verified/manual/cached evidence remains intact. Twitch still opens the installed
  official app through the existing `openOfficialApp` path, without a new player
  chooser, sign-in step or persistent setup on Home.
- Quick channels use smaller logo plates and a single readable channel label.
  Now/Next cards share the same surface, with real stored programme data and
  progress. The duplicate TV-kanaler buttons above both rails are removed;
  the hero retains TV-kanaler and its remote-accessible TV-guide/quality menu.
- The default order puts På TV just nu directly after Dina kanaler, before VOD
  continuation. Explicit existing per-profile orders and hidden sections are
  preserved; no preference migration or overwrite occurs.
- Home-only 18 px corners, restrained surfaces and native TV focus elevation/scale
  unify the cards. Native Compose animation follows Android's motion-duration
  scale, including disabled animations. No perpetual animation or bitmap effects.
  The version remains keyboard-focusable and opens the existing update/about UI,
  with a transparent idle background.

The reference `Gathin23/tv-vibehack` informed ambient depth, spacing and focus
hierarchy. No reference code, assets, HOME/launcher registration, system settings,
debloat or ADB behavior was copied.

## Screenshot review

The **Min TV prototype → mintv-v09-home-comparison** artifact contains an offline
`index.html`, original-resolution before/after PNGs at **1920×1080** and **1280×720**,
and `evidence.json` with source commits and image SHA256 values. Download/extract
the artifact and open `index.html`; click an image for its full resolution.

Before: `d0640010`, identical production UI to main `5326c641`, with only the
additional persisted synthetic screenshot fixture and screenshot frame synchronization. Baseline workflow run:
https://github.com/jonasschroder/OwnTV/actions/runs/39150622187

After: the tested candidate commit recorded in the artifact. Both use the actual
QA Application, Koin, Room database, MainActivity, profile gate, shell and Home:
two profiles, two sources, 600 channels, favorites, history, local synthetic logos,
Now/Next EPG and the existing +60 minute global EPG offset. One channel deliberately
has no guide/logo. All stream URLs are loopback test placeholders; previews are off.
The fixture never accesses an owner's installation, account or IPTV service.

The rich comparison uses 320 dpi at 1080p and 213 dpi at 720p (standard TV scaling).
The original 720p/320 dpi dense cold-start stress test remains mandatory as well.
Only disposable emulator display settings are changed and reset on exit.

The device-test screenshot artifact additionally covers missing EPG, no history,
long Swedish titles, live/offline Twitch, hockey without a channel, overflow menus,
expanded navigation and focus states. Those use the production presentation
components with injected synthetic state; they are distinguished from the real
production-process screenshots above. Existing `v08-*` fixture filenames are kept
for compatibility with prior reports and now render candidate components; the
comparison artifact is the authoritative before/after pair.

## Validation and data safety

The original mandatory startup suite remains in CI: fresh QA seed, three real
cold starts across two profiles, 1080p/720p, English/Swedish, an eight-second real
startup survival window, background/resume and Activity recreation, followed by
checks of original IPTV rows, profile links, favorites, history, sports preferences,
EPG offset and saved hidden sections. Rich EPG captures are additional executions.
The expanded production test also traverses the actual overflow menu, Back and
channel rail before returning to the hero and recreating the Activity.

The presentation suite checks long text, header/status separation, initially visible
quick channels, Now/Next visibility at 1080p, D-pad movement, explicit OK callbacks,
fullscreen-return focus, dynamic row changes, menu Back, 720p layout and no preview
allocation while disabled. Existing official-Twitch external-return tests remain.
All existing instrumentation classes are now selected: routing, sandbox/backup,
locale/resources and bounded channel queries join the Home suite, with a Python
inventory check preventing silent omissions. Dedicated startup and upgrade runners
retain their separate real-process fixtures.
JVM tests retain the String/Bundle-saveable LazyRow key regression and verify
explicit legacy orders are preserved. Existing bounded EPG/cache tests remain.

No production database schema, Core pin, source identity, stream URL, stored
preference format, favorite, profile, package ID, manifest, player implementation,
signing environment or authenticated update policy changes. All emulator runners
refuse physical devices. Only disposable normal test builds/fixture keys are used
by existing CI; no approved installer is uploaded.

Final CI results and artifact links are recorded in the PR description after the
latest commit completes. Local Python/i18n checks run separately. This environment
has no Android SDK/KVM; Android compilation and actual TV emulator tests run in CI.
The reviewed baseline PNGs are committed under `docs/images/v09/before`, so CI
can generate future comparisons without depending on an expiring workflow artifact.
Screenshot artifacts contain synthetic data only and require no signing permissions.

## Owner approval still required

The new UI has not been installed or validated on a physical Chromecast. Review
both resolutions, focused/unfocused states and programme readability first. After
approval, validate the actual remote, restored focus after Twitch, real provider
logos/EPG, fullscreen return and sustained memory on Chromecast using the existing
safe signing/update procedure. The owner-confirmed v0.8.1 installation stays intact.

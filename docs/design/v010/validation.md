# Validation, evidence and review boundaries

## What was actually checked

- Audited the current shell, every `MainSection`, MoreItem, all twelve SettingsGroup entries and
  current nested SettingsTab entries; mapped content/player/recording/profile/search/update routes.
- Read the Swedish grouping/store, Home hero/preference writes, exact Guide tune and conservative
  hockey channel resolver. No preference or database modification was performed.
- Read pinned Core's per-profile subtitle settings, MPV/Exo selection, language normalization,
  remembered series tracks and external-subtitle handover. Ran unchanged SubtitleLanguageTest (3)
  and TrackMemoryTest (5): **8 passed**, zero errors/failures/skips. These are not Swedish end-to-end
  series rendering tests; see the explicit remaining matrix in playback-and-preferences.md.
- Rendered twelve 1920×1080 design previews through the Codex in-app browser. They are browser
  screenshots of this prototype, **not Chromecast or native Compose screenshots**.
- Programmatically checked all button bounds against safe-area x=52…1868/y=30…1040 on ten screen/
  state combinations; no out-of-bounds buttons. Manually inspected the exported main Home, alternate
  Home, TV and Profile images, then corrected clipped text, hero focus outline and footer spacing.
- Exercised arrows across the eight-channel Home row, Back to header and Down back to the same
  channel; held Enter opened exactly one picker with no Play; source choice returned to caller and
  updated the demo default; Back cancelled/restored; short Enter opened the separate simulated Play
  dialog. These actions use prototype memory only. Checked the browser's captured error log: no
  JavaScript errors were present during the inspected sequence.
- Source JavaScript/renderer syntax and local Markdown/file references are checked separately before
  PR creation. No Android application test or performance claim derives from the mockups.

## Reproduce / inspect

Open `prototype/index.html` from a local checkout, or serve the repository root with an ordinary
local static server and browse `/docs/design/v010/prototype/index.html`. No packages are needed to
use the preview. It uses only local CSS/JavaScript and the existing repository's Jakarta font. It
makes no fetch/XHR/media/API requests. The browser will read those local static assets.

Optional capture runner: install Playwright in your own development tooling, then run
`node docs/design/v010/prototype/render.cjs`. Use a normal supported Chrome/Chromium installation;
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` can select it. The renderer asserts layout and representative
interactions and exports JPEG images. **This optional headless runner was not successfully executed
in this Mac sandbox**: launching standalone Chrome was blocked. Its JavaScript was syntax checked;
the committed screenshots and interaction checks were instead produced with the supported in-app
browser. Do not describe this as a passing standalone Playwright run or an Android TV test suite.
The actual exported dimensions/hashes are in `mockups/manifest.json`.

Review controls are outside the 1920×1080 TV canvas. Their small-browser responsiveness is a viewer
convenience, not a proposal to scale a 1080p Android UI to a phone. The prototype approximates remote
focus geometrically and simulates selected actions. It does not implement every settings subpage,
IME, system Back exit, production modal focus trap, lazy-list performance, actual EPG selection,
Twitch or player. Canonical behavior is specified in architecture.md; resolve gaps before native
implementation, rather than shipping prototype JavaScript.

## Approval decisions

1. Lugn start (recommended) versus Kanaler först; eight versus six visible channel cards after sofa review.
2. Explicit OK/Down activation and constant-height compact header; finite Back hierarchy.
3. Fixed empty-capability tabs versus legacy dynamic visibility; keep explicit hidden/guide-first choices.
4. Logical preferred versions versus source-exact provider/favorite/Guide/recording actions.
5. Optional bottom Home apps row and migration of existing explicit Home/Sport/Twitch preferences.
6. Delayed Swedish subtitle tests in Core before any language-policy fix or Core pin change.

Approval of this design PR is **not approval to merge production navigation, sign/distribute a
release, change production keys, install QA or operate either existing Chromecast installation**.
The next separately authorized work should be M1: native navigation adapter and focus/route gates,
with existing content/data/player screens unchanged. Keep Stable 0.9.0 and the current Test app
untouched; use disposable fixtures for new tests until an owner-approved QA upgrade.

## Final design iteration (21 October 2026)

- Updated existing PR #16, same design branch; Android/Kotlin/resources/signing/workflows unchanged.
- Fifteen screen/state exports plus picker and guide captured with the actual Codex in-app browser.
  All current JPEGs are 1920×1080; SHA-256 and dimensions are regenerated in mockups/manifest.json.
  The two original Home captures remain in mockups/previous for direct comparison.
- All fifteen rendered states passed visible-button safe-area checks (52..1868 x, 30..1040 y).
  Hidden comparison controls are excluded. Evidence: mockups/iteration-2-checks.json.
- Browser interaction checks passed: held OK opens one picker; variant OK saves FHD and opens
  simulated playback; save-only selects 4K and restores caller without playback; changing highlight
  then Back leaves saved 4K unchanged; reopening focuses the saved choice.
- Eight Right presses from first Now card reveal the ninth logical channel; Back focuses Home
  navigation; Down restores ninth channel. Ordinary browser console error log was empty.
- Node syntax checks for preview.js/render.cjs and git diff --check passed.
- render.cjs updated for new scenarios/contracts; headless runner remains unexecuted because of the
  previously documented macOS sandbox restriction. Do not equate the manual browser checks with
  Android instrumentation tests. No Gradle/APK task was run for this design revision.
- Scenarios are synthetic fixtures. Seven-day promotion, 90% completion and production save failure
  behavior are specifications requiring native regression tests after owner design approval.

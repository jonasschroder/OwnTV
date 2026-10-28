# Local decoder fixture

`app/src/androidTest/assets/live-entry-bars.mp4` is original synthetic moving RGB bars with a quiet
440 Hz test tone: 45 seconds, H.264 Baseline 320×180 at 5 fps, AAC mono. No programme content,
provider data or third-party media. Used only by a disposable CI QA emulator.

Authored using macOS AVFoundation (`live-fixture.swift`). Supply a 45-second mono 44.1 kHz sine WAV
converted to AAC with Apple's `afconvert -f m4af -d aac -b 32000 input.wav tone.m4a`, then run the
compiled generator with three **new** output paths: `generator video-only.mp4 tone.m4a final.mp4`.
The generator is authoring tooling only; CI uses the checked-in fixture and needs no new dependency.

Fixture SHA-256: `5b2e81197196ceba470a97857ab843824b6d28662be9029ccd2070707be63fa8`.

The native fixture serves these bytes over loopback HTTP with byte ranges. ExoPlayer's live source
uses OkHttp and cannot open a `file:` URI. No external host, portal, credentials or new dependency.
Smoke check (JDK 17+, temporary class output):

```sh
mkdir -p /tmp/mintv-fixture-check
javac -d /tmp/mintv-fixture-check app/src/androidTest/java/tv/own/owntv/home/LoopbackVideoFixture.java tools/test-media/LoopbackVideoFixtureSmoke.java
java -cp /tmp/mintv-fixture-check LoopbackVideoFixtureSmoke
```

# Internal playback feasibility probe

Optional development-only Android packages. No dependency from the shipped app; no microphone input,
Anki, network, dictionary, or Screenlate settings access. Both packages target the project's current SDK.

Build: `./gradlew -Pscreenlate.audioProbe :tools:audio-probe:assembleDebug`.
Install source and recorder debug APKs on an explicitly selected **emulator**. Grant the recorder RECORD_AUDIO.
Start Source and tap Start source, then start Recorder and tap Capture 10 seconds; approve projection consent.
Source emits 440 Hz; recorder emits 997 Hz under its own UID while excluding that UID from capture.
The source continues when its activity is backgrounded but stops automatically after 30 seconds and releases
its output. Stop it explicitly with its Stop button after each capture. Use an emulator started with `-no-audio` for further
sound-generating checks; never leave a continuous test tone running while doing unrelated work.
The API30 probe still captured the eligible internal source with that flag; the host did not need audible output.

Codec-only end-of-stream fixture: launch Recorder with `--ez codecOnly true --es codec aac` or `opus`.
This writes a synthetic source of 480,123 frames, with a different tone in its final 480 frames, then encodes
and decodes it without playback or projection. Inspect `fixture.properties` and `fixture-decoded.pcm` to
check final partial-frame preservation. These fixtures never play sound on the host.

For the API29 concurrent legacy-screenshot/playback test, launch Recorder with `--ez screenshot true`.
It creates one shared projection virtual display, drains at most two in-memory images, stores only image
counts/dimensions (no screenshot files), and captures audio through that same projection. Check
`screenshotFrames > 0` together with non-silent external-tone capture. Normal runs create no virtual display.
On API34+ consent requests the full-display configuration, matching all-eligible-source capture scope.

The recorder writes only synthetic `capture.pcm`, `capture.m4a`, `decoded.pcm` and `result.properties` to its
private files directory. Inspect with `adb -s <serial> shell run-as com.vpr.screenlate.audioprobe cat files/result.properties`.
Check external-tone amplitude versus own-tone leakage, frame count, encoder EOS, decoded duration/quality,
write latency and elapsed time. Launch Recorder with `--ei rate 44100` (or another supported rate) to compare.
Do not use `am force-stop` or instrument Screenlate. These intentionally bounded experiments do not prove
physical-device power/route/TTS behavior, long-session resource bounds, or production lifecycle correctness.

# Camera lookup and playback audio mining: preliminary research

2026-10-16. Research requested by the owner, not an implementation plan. Technologies, defaults, UI placement and field markers below are proposals requiring later decisions. No application code or device state changed.

## Existing scope and estimate basis

Snapshot: `main` b50aa0a, `languages/main` 12a5ecc, current `languages/answers` f4f5648, plus existing uncommitted work. Three-dot diffs include some shared fixes and generated files, not just handwritten language logic. The separate OCR comparison experiment is excluded.

- `main...languages/main`: 416 files, 31,480 added / 1,581 removed lines.
- `main...languages/answers`: 448 files, 34,614 added / 1,652 removed lines.
- Coarse path buckets in the latter: 13,964 additions to code/scripts, 5,696 to tests, 7,533 XML lines (mostly locales), 937 documentation lines, 6,484 catalog/config/other lines. Generated code is not subtracted; binary contents and submodule source volume are not counted.
- Language work spans profiles/readiness, C++/JNI/QuickJS lookup, form-of indexes, OCR models, scripts, catalog, settings and Anki isolation. Camera lookup reuses most of that.

Engineering estimates, excluding translated copies of strings, vendor/generated data and unrelated refactors:

| Scope | Added production lines | Added test lines | Difficulty | Relative LLM budget, language work = 100 |
|---|---:|---:|---|---:|
| Camera, memory-only still, OCR and existing lookup | 1,500–3,000 | 700–1,500 | Medium | 20–35 |
| Audio buffer, trim/preview and export, manual video pause | 3,000–5,000 | 1,000–2,500 | High | 35–60 |
| Full audio scope, including continuation and automatic media control | 5,000–9,000 total | 2,000–4,000 total | High; compatibility-dependent | 70–120+ |

The last row includes the basic audio row. Relative LLM budgets are judgment about implementation and verification effort, not arithmetic from line counts. Allow at least factor-of-two uncertainty. Money requires actual model input/output/cache rates and comparable usage. Formula: uncached input tokens * input rate + cached tokens * cache rate + output tokens * output rate. Repeated long context and retries are major unknowns. Neither feature requires an LLM at runtime; existing optional cloud OCR is separate.

## Camera: proposed architectural steps

1. Dedicated camera screen with CameraX `Preview` and in-memory `ImageCapture`, camera permission and lifecycle handling. Saving to the gallery is optional. [CameraX capture](https://developer.android.com/media/camera/camerax/take-photo).
2. Normalize crop/rotation, retain one owned image and its captured `Language`, start `CompositeOcr` immediately and cache draft/final `TextLayout`. Aim movement must not recapture the display or repeat whole-image OCR. Reuse language/model readiness, but app-text-only mode cannot recognize a photo.
3. Introduce a narrow image-source/session seam around screenshot acquisition and note context. Reuse `OcrEngine.recognize(Bitmap, Language)`, `TextLayout`, `DictionaryLookup` and `LookupPage`. `OverlayController.startScan()` currently acquires a screenshot; do not route a photo back through screenshot OCR. The debug `OcrTestViewModel` already demonstrates recognition of a supplied image.
4. Map image coordinates to viewer coordinates and back for aim, highlights, crop, rotation, fit and any zoom/pan. Existing screenshot geometry is insufficient. [CameraX transformations](https://developer.android.com/media/camera/camerax/transform-output).
5. Reuse `NoteSource`/`NoteContext` and bitmap ownership patterns from `SharedScreenshot`, adapting screen-specific geometry. Anki crops should use the original image, not camera chrome or the popup.
6. Verify real camera input, dense/vertical text, rotation, transforms, retake/cancel, late OCR callbacks, memory and local/cloud recognition. Add test-model cases when implementing.

The main costs are coordinates, lifecycle/bitmap ownership, integration with the overlay-oriented workflow and physical image quality. Live OCR, perspective correction and model training are outside this estimate.

## Audio: proposed architectural steps

Detailed platform evidence: [audio-capture-platform-research.md](audio-capture-platform-research.md).

1. First test intended real-device players for two independent capabilities: capturable sound and controllable media sessions. App capture policies and missing sessions cannot be fixed by changing an editor library.
2. Isolate recording/control behind interfaces, potentially `core:media`, consumed by app/overlay. Use `AudioPlaybackCaptureConfiguration` + `AudioRecord`, a user-started `mediaProjection` foreground service, runtime `RECORD_AUDIO` and MediaProjection consent. Accessibility screenshots do not provide this permission. Audio capture need not save screen video. [Playback capture](https://developer.android.com/media/platform/av-capture), [service prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection).
3. Maintain bounded PCM history on a worker thread with sample indexes and monotonic timestamps. Example: 60 s of 48 kHz stereo 16-bit PCM is 11.52 MB. Pin/copy history at the note action, before image cropping, so it cannot be overwritten. Freeze language and source/session identity with the draft. Duration and source policy remain undecided.
4. Show waveform, trim handles, preview and cancel/confirm after image crop. Encode confirmed PCM using platform tools, provisionally `MediaCodec`/`MediaMuxer` with AAC/M4A, checking Anki playback. Android documents AAC encoding and container support: [supported formats](https://developer.android.com/media/platform/supported-formats).
5. Keep recorded sentence audio distinct from pronunciation. Current `NoteRequest.audio` and `{audio}` represent one pronunciation clip. A new recording attachment/marker likely belongs beside it; exact behavior needs a later owner decision. Reuse `AnkiBackend.addMedia`, generalizing the local media model rather than inventing a network URL.
6. Continue recording as explicit segments; exclude the known non-recording gap when joining. Keep real pauses inside segments. Optional silence suggestions need padding and preview to preserve quiet speech. Prevent editor preview and popup pronunciation contaminating captured sound; UID filtering can isolate a stable source app, but not individual players sharing its UID.
7. Add media control independently. Match the existing foreground package to a platform `MediaController`, discovered through `MediaSessionManager` with enabled notification-listener access; accessibility alone does not authorize session discovery. Use explicit pause/play requests and check observed state/actions. [Session access](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)).
8. Track exact session, pause ownership and invalidation. Standard callbacks do not universally identify manual button presses. Literal "resume only if the user never touched controls" is not fully observable; conservatively leave paused when uncertain. Several sessions/tabs in one package remain ambiguous.
9. Coordinate pause acknowledgement, screenshot and audio marker. OCR can identify a word in an older screenshot while the video has advanced. Avoid attaching a stale subtitle to a newer frame/audio cut. Scan-start versus valid-word-hit pause is an unresolved interaction choice, not a silent implementation default.
10. Test revocation, stop, source switch, ring rollover, cancelled notes, preview playback, manual controls, pause/resume races, lifecycle and missing audio. Capture and media-control failures should degrade independently.

## Reusable prompts

### Camera: requirements only

Investigate a camera lookup mode for Screenlate. The user opens an in-app camera, captures a still without automatically saving it, and keeps it on screen. Start OCR immediately. Moving the familiar circular aim over the frozen image shows dictionary definitions through existing lookup behavior, without repeatedly capturing or recognizing the display. Preserve the active learning language and existing Anki workflow. Optional explicit photo saving is secondary; live camera OCR is outside the first version. Estimate reuse, scope, risks and architectural steps; ask only material clarification questions. This is preliminary research, not approval to implement or choose unresolved defaults.

### Camera: preliminary technical direction

Research still-camera lookup in Screenlate's current language branch. Provisional approach: CameraX Preview and in-memory ImageCapture; an owned still-image session; existing CompositeOcr/OcrEngine(Bitmap, Language), cached TextLayout, DictionaryLookup and LookupPage. Separate image input from OverlayController's screenshot acquisition through a narrow interface. Map original-image/view coordinates for rotation, fit, crop and supported zoom/pan. Reuse NoteSource/NoteContext and bitmap ownership patterns; Anki crops come from the original photo. Honor per-language OCR readiness, without substituting accessibility text for photo OCR. Assess lifecycle, cancellation, late results, memory and physical camera testing. Technologies and seams are preliminary and may change after further investigation. Propose scoped implementation steps and effort before coding.

### Audio: requirements only

Investigate optional video audio mining in Screenlate. While explicitly enabled, retain a bounded rolling history of internal playback. At long-press Add to Anki, preserve the history immediately; after image cropping, show an audio trim/preview editor. Allow recording a continuation and joining it to the initial fragment while excluding the intervening user pause. Optional silence trimming must preserve quiet speech and allow review. Export the clip alongside the note, preserving pronunciation audio behavior. Also investigate optional automatic pause when aiming at a word in the visible player, excluding unrelated background music, and configurable resume when the user has not overridden playback. Explain Android/player compatibility and the limits of manual-override detection. Separate basic capture from automatic media control in the scope and effort estimate. Research only; do not choose unresolved defaults or start implementation.

### Audio: preliminary technical direction

Research internal-playback mining in Screenlate's current language branch. First propose a real-device experiment testing capture and media control independently. Provisional architecture: user-started mediaProjection foreground service, RECORD_AUDIO and MediaProjection consent, AudioPlaybackCaptureConfiguration + AudioRecord, timestamped bounded PCM ring buffer, immutable note history pinned before picture cropping. Isolate recording/control interfaces and PCM editing, potentially in core:media. Add waveform trimming, preview, explicit continuation segments, final platform encoding and an Anki attachment distinct from pronunciation. Exact markers/defaults require later owner decisions. Reuse foreground-package tracking to select a matching MediaController through MediaSessionManager with notification-listener access. Track pause ownership and conservative resume; do not claim callbacks identify every manual action. Address source policies, session/tab ambiguity, UID filtering, preview contamination, pause/screenshot timing, cancellation and revocation. Prefer excluding known inter-segment gaps over deleting all quiet audio. All technologies are provisional; change the recommendation when research supports an alternative. Provide stages, compatibility risks, tests and LLM-assisted effort, without implementing.

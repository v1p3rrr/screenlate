# Temporary diagnostics build

**TEMPORARY. Remove after the analysis.** The diagnostics live only on the branch `claude/diagnostics-build-9fbbc9` and
are never merged into `main`, `languages/*` or another working branch. The owner installs a build with them on the
phone, uses it for a while, exports the logs, Claude analyses them, and then everything below is removed (see
"Removal"). Started 2026-10-19.

Every place in the code carries the marker `TEMPORARY DIAGNOSTICS BUILD`:

```bash
grep -rn "TEMPORARY DIAGNOSTICS BUILD" --include=*.kt --include=*.kts --include=*.xml .
```

## Design (chosen without the owner, who asked for autonomous work; confirm or change later)

| Question | Choice | Why |
| --- | --- | --- |
| Marking | One Gradle module `:diagnostics` (package `com.vpr.screenlate.diagnostics`, entry object `Diag`), reached by every module through `api(project(":diagnostics"))` in `core:common`; call sites marked with a comment; the debug label "Screenlate Diag"; release tasks fail on the branch | Deleting the module makes the compiler list every leftover call; the label tells the build apart on the phone; a release can never carry it |
| Branch | Its own branch; the APK comes from a temporary merge with the latest work | Nothing to revert on the main line |
| Storage | `noBackupFilesDir/diagnostics/`: events as JSON lines in 8 files of 2 MB, the logcat copy in 8 files of 2 MB, ANR traces at most 4 of 1 MB (~36 MB at most) | Survives app restarts and phone cleaners (not the cache directory); never in backups |
| Export | The existing About → Logs buttons ("Share logs", "Save to Downloads") give one zip: the usual log text, `diagnostics/state.json` (a snapshot taken at export), `diagnostics/events/*.jsonl`, `diagnostics/logcat/logcat.txt*`, `diagnostics/traces/anr-*.txt` | No new screen or permission; the owner already knows the buttons |
| In-app card | About → "Diagnostics": what the build collects, since when, how much, and "Delete collected data" (with a confirmation) | Marks the build and lets the owner start over |
| Permissions | None. Optional: Perfetto System tracing from Developer options, only when a specific slow case needs it | Everything used is open to an app about itself |

## What is collected

Fields are numbers, identifiers and states. Never recognized text, looked-up words, note contents or URLs with terms
(`net` and `download` keep the host only; crashes keep exception types and frames, never messages). Every line:
`{"t": wall clock ms, "up": elapsed since boot ms, "e": event, ...fields}`; `scan` fields name the scan the event
belongs to.

Process and system:

- `process` (each start: pid, ms since the process started), `device` (once per start: maker, model, Android, build,
  ABIs, cores, RAM, memory classes, screen, refresh rates, WebView version, locale), `process_start` (Android 15+:
  start reason, cold/warm, whether the app was force-stopped, fork/bind/onCreate/first frame times).
- `exit`: why each earlier process ended (`ApplicationExitInfo`: low memory, crash, native crash, ANR, freezer, user
  stopped, …) with its PSS/RSS and the state string the process had set (`Diag.state`: service state, last scan, PSS);
  ANR traces are copied into `traces/`.
- `app_create` (Hilt injection ms), `app_init` (first-run settling and bundled install ms), `app_repair` (ms).
- `service`: accessibility service created / connected / unbind / interrupt / destroyed with the process age;
  `service_config` (orientation, size in dp, night mode).
- `stall`: a main-thread message that ran 150 ms or longer, with its handler and callback class (from the looper's
  message log); stalls of 48 ms or more are summed per scan.
- `activity`, `frames`: app screens created/destroyed; per visit of a screen its frames, janky frames and the worst
  frame (the overlay's windows have no frame metrics).
- `trim_memory`, `low_memory`, `memory` (after a trim and after each scan; the full form reads PSS by kind and the
  system's free memory, at most once a minute), `sample` (every 15 minutes: CPU time, full memory, thermal state and
  headroom, power saving, doze, battery level/temperature/charging, battery optimization exemption, standby bucket,
  background restriction, data saver, network, whether the service is enabled in the settings, free storage, size of
  the collected data).
- `thermal`, `power_save`, `screen`, `doze`, `network`: changes as they happen.
- `crash`: an uncaught exception's type, causes and frames, written before the usual crash.
- `export`, `cleared`, `dropped` (lines lost to a full queue).

Network (`net`, one per HTTP call of the shared OkHttp client and every client derived from it: cloud recognition,
catalog, downloads, audio, translation, updates): host, method, code, total ms, DNS, connect, TLS (and version), wait
for the first byte, body ms, bytes sent/received, speed, whether a pooled connection was reused, idle time since the
previous call to the host, protocol, error class.

Capture and recognition:

- `capture`: window or display capture, size, ms, the hardware buffer copy ms, retries after "too soon";
  `capture_fallback` (window capture failed).
- `model_load` / `model_release` (ML Kit per model, Tesseract per model with file size), `ocr_pass` (one per engine
  run: engine, model, image size, ms, ML Kit task vs line building ms, Tesseract load/prepare/recognize/read ms,
  whether it was the first run after a load, native heap after it).
- `model_install` (checksum ms, wait for the store, unzip/place ms), `model_verify` (damaged models).

Dictionaries:

- `import_task` (start with run number, retries and free storage; stopped by WorkManager with the reason; requeued;
  end with ms, count, error kind), `import_step` (wait for the other import, import ms, memory), `download` (host,
  resumed/received/total KB, ms, speed, the longest pause between progress callbacks, error, network, free storage).
- `dictionary_load` (per language load: ms, counts by kind, memory), `dictionary_import` (engine import ms, archive
  size), `engine_load`, `engine_session`, `engine_lookup` (native ms vs JSON decode ms, JSON size, results),
  `language_script_load` (QuickJS language script load ms), `lookup` (total, lock wait, engine time and calls, results,
  text length only, language, dictionary count).

AnkiDroid: `anki_call` (which method of `AnkiDroid`, ms, error class).

Trace sections (`SL:` prefix) for Perfetto: `capture_copy`, `mlkit_load`, `mlkit_task` (async),
`mlkit_lines`, `tesseract_load`, `tesseract_recognize`, `hoshidicts_import`, `hoshidicts_load`, `quickjs_load`,
`import` (async), `scan` (async); counters `SL:pss_mb`, `SL:native_mb`.

### Not yet instrumented (waits for the performance changes)

The scan summary needs the overlay and the files of the performance work, which another session is changing. Once it
has pushed to `languages/checks`, merge that into the diagnostics branch and add:

- `OverlayController`: `Diag.scanBegin(trigger, settings snapshot)` at the scan start, `scanMark` for touch, capture
  start/end, app text, first result, popup shown, WebView ready; `scanUpdate` for each draft/final result;
  `scanEnd(reason)` in `resetScan`; bubble window add/remove, dock decisions, insets/keyboard/fullscreen decisions,
  warm-up; process start → bubble shown.
- `LookupPage` / `PopupController`: WebView created (reason, ms), page ready, render ms, renderer gone, how often.
- `CompositeOcr` (band/whole passes with crop sizes, which result won), `DeviceOcr` (model choice), `LensOcrEngine`
  (JPEG encode ms and size, parse ms), `PaddleOcrEngine` / `mnn_network.cpp` (load, warm-up, input shapes, session
  resizes, release, per-network inference ms, native heap after a pass).

Until then the `scan` event is not written; the other events are.

## Overhead

- The looper's message log builds a short string for every main-thread message (microseconds each).
- The full memory read takes tens of milliseconds on the diagnostics thread, at most once a minute (after scans,
  trims, the 15-minute sample).
- Events are written by a low-priority thread; the logcat copy is a `logcat` child process.
- Storage: up to ~36 MB, shown on the About card.

## For the owner: collecting and exporting

1. Install "Screenlate Diag" (it replaces "Screenlate Dev": the same package, `com.vpr.screenlate.debug`). If Android
   refuses with a signature conflict, the Dev build on the phone was signed with another key: Settings → Backup and
   restore → Create backup (with dictionaries if they should not be downloaded again), uninstall Screenlate Dev,
   install Screenlate Diag, restore the backup, turn on its accessibility service.
2. Use the app as usual for a few days: scans in different apps, popups, Anki exports, downloads.
3. About → Logs → "Save to Downloads" (a zip in `Download/Screenlate`) or "Share logs". Send the zip.
4. Optional, only when asked for a specific slow case: Settings → Developer options → System tracing → turn on
   "Trace debuggable applications", record a trace while reproducing the case, share the trace file.
5. Before installing a normal build: About → Diagnostics → "Delete collected data".

## Building the APK

- Local: `./gradlew :app:assembleDebug` on the diagnostics branch (or on a temporary merge of the latest work into it)
  gives `app/build/outputs/apk/debug/app-debug.apk`. It is signed with this machine's debug key.
- CI: run the CI workflow (workflow_dispatch) on the branch; the artifact is `screenlate-debug-<sha7>`. CI signs with
  the runner's own debug key.
- Release tasks (`assembleRelease`, `bundleRelease`, …) fail on purpose on this branch.

## Reading the data

The events are JSON lines; e.g. with Python: `[json.loads(l) for f in sorted(glob('diagnostics/events/*.jsonl')) for
l in open(f, encoding='utf-8')]`, then group by `e`. The logcat copy has the app's own log lines with years in the
time stamps (`-v threadtime -v year`).

## Removal

1. Do not merge the diagnostics branch. Delete it locally and on `origin` once the analysis is done:
   `git push origin --delete claude/diagnostics-build-9fbbc9`, `git branch -D claude/diagnostics-build-9fbbc9`, and
   remove its worktree (`.claude/worktrees/diagnostics-build-9fbbc9`).
2. If anything of it was merged somewhere after all: delete the `diagnostics/` module, its `include` in
   `settings.gradle.kts`, the `api(project(":diagnostics"))` line in `core/common/build.gradle.kts`, the release guard
   and the "Screenlate Diag" label in `app/build.gradle.kts` (back to "Screenlate Dev"),
   `app/src/main/java/com/vpr/screenlate/settings/DiagnosticsCard.kt`; then build: the compiler names every remaining
   `Diag` call; remove each marked block and check that `grep -rn "TEMPORARY DIAGNOSTICS BUILD"` finds nothing.
   `LogExport` goes back to plain text (`text/plain`, no `DiagExport.zip`).
3. On the phone: "Delete collected data" before installing a normal build; what stays (a few small files in
   `no_backup/diagnostics`) goes with the next uninstall or "Clear storage".
4. Delete this note, its line in `AGENTS.md` and the open item in `ai/status.md`.

## Open questions for the owner

- Should the system log lines be included (READ_LOGS granted once over adb)? It gives other apps' and the system's
  lines (e.g. why the system stopped the service), but Android 13+ asks for consent each time logcat starts.
- Are ~36 MB and 15-minute samples right, or should the caps be smaller/larger?
- Should a normal build delete leftover `no_backup/diagnostics` files by itself (one line in the main line), or is the
  card's "Delete collected data" enough?
- CI or a local build for the phone: both sign with a key that differs from the installed Dev build (see step 1 above).

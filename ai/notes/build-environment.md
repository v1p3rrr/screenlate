# Build environment

## Machine

- Windows 11. Shells available to the agent: Git Bash and PowerShell.
- JDK for Gradle: `C:\Program Files\Java\jdk-21` (also `jdk-17`). The JBR inside `C:\Program Files\Android\Android Studio\jbr` is broken (missing `lib/jvm.cfg`), do not use it.
- Android SDK: `D:\Android\Sdk` (platform `android-37.0`, build-tools up to 36.0.0, NDK 25.1, 26.1 and 29.0.14206865, CMake 3.22.1 and 3.31.6). `local.properties` points there.
- `gradle/gradle-daemon-jvm.properties` asks for JDK 25; Gradle provisions it through foojay automatically.

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew assembleDebug testDebugUnitTest
```

A cold build takes ~3.5 minutes, incremental builds much less. Configuration cache is on.

## Toolchain facts

- AGP 9.4.1 with built-in Kotlin: modules must not apply `org.jetbrains.kotlin.android`. The root build declares it with `apply false` only to pin the Kotlin Gradle plugin version (2.4.20).
- In AGP 9 `CommonExtension` is not generic and has no `defaultConfig`; configure `defaultConfig` on `ApplicationExtension` / `LibraryExtension` (see `build-logic/`).
- `compileSdk = 37` (plain int) resolves the `android-37.0` platform fine.
- Release minification uses the AGP 9 DSL `optimization { enable = true }`; keep rules go to `src/main/keepRules/*.keep`.
- Hilt 2.60.1 + KSP 2.3.12 + Room 2.8.5 build cleanly with AGP 9.4.1. Hilt 2.59 had an AGP 9 bug (dagger#5099).
- Native code (`:dictionary:engine-hoshidicts`) needs CMake 3.31.6: glaze requires CMake >= 3.31. Install SDK packages with `sdkmanager --package_file=<file>` from PowerShell, a `;` in a package id on the command line gets split.
- glaze reflection does not work on types in an anonymous namespace ("type does not have linkage"); keep JSON DTOs in a named namespace.
- Release builds (R8 via `optimization { enable = true }`) need the keep rules in `app/src/main/keepRules/rules.keep`: ML Kit's `ComponentRegistrar` constructors are found by reflection, and the WebView bridge methods are called from JavaScript. The AnkiDroid API aar brings lint checks for its own code base; modules that have it on the classpath call `disableAnkiDroidLintChecks()` (build-logic `ProjectExtensions.kt`). Lint (`./gradlew lintDebug`) is clean apart from two known warnings.
- To try a release build without the owner's key: sign `app-release-unsigned.apk` with `~/.android/debug.keystore` (`apksigner sign --ks ... --ks-key-alias androiddebugkey`, passwords `android`). Only one accessibility service should be enabled at a time (`settings put secure enabled_accessibility_services <pkg>/<service>`).
- The AnkiDroid lint checks still reach `app`: `DirectDateInstantiation` fails `Date()` (use `java.time`, e.g. `LocalDate.now()`).
- CI (`.github/workflows/ci.yml`) runs on pushes to `main` and on pull requests only, with `assembleDebug
  testDebugUnitTest lintDebug :build-logic:convention:test`; `languages/main` has none. The local command in AGENTS.md
  leaves out `lintDebug`, so run it as well before code reaches `main`. Lint errors fail CI: `main` failed from
  2026-10-15 to 2026-10-20 on `RestrictedApi` (WorkManager's `Result.Retry` and `Data.Builder.put`; compare with
  `Result.retry()` and use `putAll`). Every push to `main`, ai/ copies included, sends the owner a failure mail then.
- Compose: `ScreenlateTheme` must compose its content on one path for every mode (only the color scheme and locals change). A separate branch for e-ink mode rebuilt the NavHost and sent the app back to the home screen when the mode was switched.
- Compose skipping (2026-10-15, the download lag): with strong skipping, a lambda that captures a value of uncertain
  stability is compared by reference, so `onDownload = { ... item ... }` was a new lambda on every progress update and
  every catalog card recomposed (about 2000 recompositions for one download). Pass the item to the lambda instead of
  capturing it; give classes passed as parameters an `equals` (data class); declare models of non-Compose modules and
  collections in `compose-stability.conf`. Measure with a `SideEffect` counter and `dumpsys gfxinfo` ("Slow UI
  thread"), or the compiler reports (`reportsDestination` in the Compose convention plugin, not kept).
- Compose `SideEffect` (2026-10-27, the camera review): state read inside its lambda is not observed, so the page
  does not recompose when only that state changes and the effect never sees the new value (the Camera page's menus
  never hid the photo). Read the state in composition and pass the value in. And `SideEffect` runs after every
  recomposition: an assignment with a cost, such as CameraX's `imageCaptureFlashMode` (a new camera request each
  time), goes into a keyed `LaunchedEffect`.
- kotlinx-coroutines-test: `advanceUntilIdle()` returns once only `backgroundScope` tasks are left, so a collector
  started there may not have seen the last events yet; call `runCurrent()` after it.
- Kotlin name resolution (2026-10-28): an explicit import wins over a top-level function of the same file. A private
  `languageName(code)` that capitalized `core.common.languageName(code)` was never called (calls with one argument
  went to the import), so headings showed "японский" in Russian; give such a wrapper its own name (`titledLanguageName`).
- `resValues` build feature is off by default in AGP 9; the app label per build type uses a manifest placeholder (`appLabel`).
- Language bundles (`yomitan-language/<iso>.js`, 2026-10-22): the build needs a Yomitan clone at the commit in
  `scripts/yomitan-language/yomitan-commit.txt` (a `git clone --filter=blob:none` into the session's scratch folder is
  enough); `glue.test.mjs` runs its Yomitan cases only with `YOMITAN` set to that clone (a Windows path). Every bundle
  carries `glue.js`, so after an edit of `glue.js` or `own-transforms.js` rebuild all of them, not only the language
  edited: `languages/lt` had rebuilt only `ro.js` after its last glue change, and the merge into `languages/main`
  rebuilt the other 37. Minified names shift whenever shared code changes, so compare a rebuild with the committed
  files with one- and two-letter identifiers normalized (the coordinating session's check). Each bundle that imports
  from `own-transforms.js` also carries the other languages' rules there (Romanian and Latin in no, kk, ky, bn, fa,
  he: about 2.4 KB), since esbuild keeps the rule helpers' calls; marking the module `sideEffects: false` in the
  resolver does not drop them.

## Agent tooling gotchas

- Bash heredocs in this environment turn `\\` into `\`, even with a quoted delimiter. Write files that contain backslashes (regexes, escapes) with the Write/Edit tools.
- `cd` inside a Bash call can move the session's working directory; use absolute paths.
- `/tmp` in Git Bash is not the same path Windows Python sees; pipe data into Python through stdin or use the scratchpad directory.
- A new `git worktree` (e.g. of main for cherry-picks) has no hoshidicts submodule and no `local.properties`: before building there, copy `local.properties` and run `git submodule update --init --recursive`, or CMake fails with "does not contain a CMakeLists.txt file". Such a worktree can be removed only after `git -C <worktree> submodule deinit --all -f`, which also drops the submodule from the shared `.git/config`: run `git submodule init dictionary/engine-hoshidicts/src/main/cpp/hoshidicts` in the main checkout afterwards. A worktree only for cherry-picks needs neither. `--reference` to the main checkout's MNN fails (that clone is shallow): fetch MNN from GitHub with `git submodule update --init --depth 1`. hoshidicts has eight submodules of its own (utfcpp, glaze, zstd, unordered_dense, xxHash, libdeflate, utf8proc, kanji-processor); without them CMake stops at utf8proc.

## Devices

- Compose UI tests (added 2026-10-11) fail inside the bundled Espresso input injector on the API 37 emulator before
  running the UI checks: `NoSuchMethodException: android.hardware.input.InputManager.getInstance`. Run them on
  API 30 (or the CI API 34 emulator) until the test dependency supports that platform. The non-UI registry and
  WorkManager tests run on API 37. This is a test framework failure, not an app exception.
- Compose answers no `findAccessibilityNodeInfosByText`; a test that reads the window through `UiAutomation` walks
  `rootInActiveWindow` and compares text and content descriptions (`FirstRunRequestTest`).
- API 30's bundled WebView is too old for the existing popup page's `replaceChildren`. Tests of the real page use
  API 37 with `ActivityScenario<ComponentActivity>` and main-thread WebView callbacks, without Espresso's input
  injector. Wait for a painted frame before taking a screenshot: the JS bridge can answer before the GPU paints.
  `-no-window` QEMU processes are named `qemu-system-x86_64-headless.exe`; include that name when checking cleanup.
  The API 37 AVD's data disk is nearly full. The 2026-10-11 checks used a disposable `-read-only` instance; installing
  the final APK needed temporarily setting the storage threshold to 1%/100 MiB, then deleting those settings again.
  When `install -r` fails for space and `pm trim-caches` frees too little (2026-10-19, about 340 MB free): back up
  the app's data with `adb exec-out run-as <pkg> tar cf - . > app-data.tar` (2.7 GB with the dictionaries), turn
  the accessibility service off, `pm clear <pkg>`, install, restore with `adb exec-in run-as <pkg> tar xf - <
  app-data.tar`, grant `com.ichi2.anki.permission.READ_WRITE_DATABASE` and `POST_NOTIFICATIONS` again and put the
  service setting back. Under Git Bash set `MSYS_NO_PATHCONV=1` and give host paths as `D:/...`.

- Emulator AVD `Pixel_10_Pro`: 1280×2856, density 480 (3.0), Android 17 (API 37.2), Play Store image with 16 KB pages. Native libraries (hoshidicts) must be 16 KB aligned: use NDK r28+ or the equivalent linker flags.
- `Pixel_10_Pro` has no quick-boot snapshot since 2026-10-15: saving one (`adb emu avd snapshot save default_boot`)
  hung in its textures step (window not responding, no CPU or disk activity; the log showed the fallback to system
  OpenGL), and the emulator had to be killed. The save had already dropped the disk's internal snapshot, so the old
  one could not load any more; its folder was moved next to the AVD (`Pixel_10_Pro.default_boot.broken-2026-10-15`,
  safe to delete). The AVD now cold-boots from its disk and keeps the state of earlier runs (not booted yet since):
  AnkiDroid 2.24.1 with a local collection, the `languages/main` debug build 0.2.3-a62b900 with ten Japanese
  dictionaries including Wiktionary (ja–ja), the test images. Keep `-no-snapshot-save` (a snapshot at exit may hang
  the same way) and shut it down with `adb shell reboot -p`: killing the process is a power cut for its file systems.
  Check `dumpsys package <pkg>` (versionName, lastUpdateTime) before relying on a state from an earlier run.
- Start the AVD with `-crash-report-mode never` (e.g. `emulator -avd Pixel_10_Pro -no-snapshot-save -no-boot-anim -crash-report-mode never`), in the background from the session so it can be stopped by its own task. After any emulator crash, the next start shows a crash-report consent dialog that blocks the boot; it looks like a new crash at boot.
- Builds from a branch without the latest release tag get a lower version code (the code is derived from tags): installing over a newer release needs `adb install -r -d`. No app logic reads the version code.
- `adb`: `D:\Android\Sdk\platform-tools\adb.exe`. In Git Bash set `MSYS_NO_PATHCONV=1`, otherwise device paths like `/sdcard/...` get rewritten to Windows paths.
- `scripts/debug-device.sh` wraps the routine: `install` (installs the debug APK and enables the accessibility service, retrying because the system drops the service shortly after a package update), `images` (copies `testdata/ocr/*` into the app's internal files via `run-as`), `show <file> [default|swipe]` (opens the debug full-screen image viewer; with a mode the system bars are hidden as in fullscreen apps, see `overlay-system-ui.md`), `shot <out.png>`.
- Never use `am start -S` or `am force-stop` on the app while testing the overlay: force-stopping removes the accessibility service from the enabled list. Launch the app with `adb shell monkey -p com.vpr.screenlate.debug -c android.intent.category.LAUNCHER 1` (it brings back the task's last screen, such as the debug viewer; press Back to reach home). After a slip, `settings put secure enabled_accessibility_services com.vpr.screenlate.debug/com.vpr.screenlate.overlay.ScreenlateAccessibilityService` and `settings put secure accessibility_enabled 1` turn the service on again.
- App logs on Honor and Huawei phones (2026-10-20): the owner's Honor (MagicOS 9, Android 15) gives the app no
  lines from `logcat -d`, in the release, debug and diagnostics builds alike: About → Logs holds only the header, and
  the diagnostics build's `logcat -f` copy stays at 0 bytes (its own events file is fine). Honor and Huawei drop app
  log output unless a hidden log switch is on (service code `*#*#2846579#*#*` → Background settings → Log settings;
  reportedly reset by a reboot and not always enough on MagicOS 9; developer forum reports, nothing official).
  Diagnostics that must work on such phones need the app's own file log and `ApplicationExitInfo`, not logcat.
- Files pushed by adb into `/sdcard/Android/data/<pkg>` are not readable by the app (group `ext_data_rw`); copy through `run-as` into internal storage instead.
- `adb install` cannot open a host path over 260 characters ("failed to stat"; the scratchpad's Gradle outputs reach it): copy the APK to a short path inside the project first, e.g. `testdata/apk/` (git-ignored). Never at a drive root (the owner, 2026-10-28).
- Short /data (2026-10-20, Khmer run): with about 0.39 GB free the 126 MB debug APK fails with
  `INSTALL_FAILED_INSUFFICIENT_STORAGE`. Install an x86_64-only copy instead (82 MB): copy every zip entry except
  `lib/arm64-v8a/`, `lib/armeabi-v7a/` and the signature files in `META-INF/` (`*.SF`, `*.RSA`, `*.EC`, `*.MF`) with
  Python's `zipfile`, keeping each entry's `ZipInfo` (stored entries stay stored), then
  `zipalign -f -P 16 4 in.apk out.apk` (build-tools; `-P 16` keeps the native libraries page-aligned) and
  `JAVA_HOME="C:/Program Files/Java/jdk-21" apksigner sign --ks C:/Users/vpr/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey out.apk`
  (a `/c/...` JAVA_HOME or a `~` path fails here). The debug key matches, so `adb install -r` keeps the app data.
  `-Pandroid.injected.build.abi=x86_64` is no substitute: it writes to `build/intermediates/apk/`, and the app APK
  comes out test-only with the arm libraries still in it; the test APKs of library modules do shrink with it and
  install. The owner allows deleting, on the emulator, the dictionaries of languages already checked (2026-10-20;
  they download again); AnkiDroid and other data stay untouched.
- A 109 MB test APK (the overlay's, with every ABI) on a /data with about 250 MB free (2026-10-20, Persian run):
  Gradle's install hung, `system_server` died and came back without its package service ("cmd: Can't find service:
  package", zygote "stopping", load average about 55), and `adb reboot` timed out; `adb shell reboot -p` rebooted it.
  Make test APKs x86_64-only too (the same `zipfile` copy: the overlay's goes to 73 MB) and run a class with
  `am instrument -w -r -e class <class> <test package>/androidx.test.runner.AndroidJUnitRunner`, then uninstall the
  test package.
- Low commit memory (2026-10-20: about 2 GB of Windows commit left with the emulator and other sessions running):
  test executors fail to start ("Could not write standard input to Gradle Test Executor", "The pipe is being closed",
  "finished with non-zero exit value 1"), which looks like failed tests. Rerun with `--max-workers=2`; do not
  `gradlew --stop`, as other sessions share the daemons.
- A Gradle build outlives the session that started it (2026-10-27): after a session restart a build kept compiling
  MNN for three ABIs (ninja with about 20 `clang++` under the Gradle daemon) for over an hour, and with BlueStacks
  starting the Windows commit limit ran out ("insufficient memory for the Java Runtime Environment", an
  `hs_err_pid*.log` in the worktree). Before a new build, list `java`, `ninja` and `clang++` processes and check
  their worktree in the command line. The page file on E: is system-managed: it grew to 30 GB under that load and
  shrank to about 6.5 GB after it.
- A check build for the x86_64 emulator only (2026-10-28): an init script with
  `allprojects { afterEvaluate { p -> def a = p.extensions.findByName("android"); if (a != null) { a.defaultConfig.ndk.abiFilters.clear(); a.defaultConfig.ndk.abiFilters.add("x86_64") } } }`
  (`--init-script <file>`) builds native code for x86_64 alone and gives a 93 MB debug APK that installs without the
  `zipfile` copy. It runs before the plugins' own `afterEvaluate`, so the DSL is still open. CI still builds every
  ABI. A new worktree also needs `git submodule update --init --recursive` (hoshidicts' `external/` submodules),
  or CMake stops at "does not contain a CMakeLists.txt file".
- A `main` build next to the `languages/main` one (2026-10-28): `main`'s debug build is a downgrade over the
  language branch's (its data would not open), and uninstalling that loses its dictionaries and settings. A second
  init script
  `allprojects { pluginManager.withPlugin("com.android.application") { def c = extensions.getByName("androidComponents"); c.onVariants(c.selector().withBuildType("debug")) { v -> v.applicationId.set("com.vpr.screenlate.debugmain") } } }`
  gives the debug APK its own package (authorities follow `${applicationId}`; the test APK targets it too). Both
  apps show as "Screenlate Dev"; only one accessibility service should be on at a time. Uninstall it afterwards.
  `am instrument` force-stops its target package before it runs (the system does it when instrumentation starts),
  which the no force-stop rule may cover; that run checked `OpenAppTest`'s case by hand instead (ask the owner).
- A new worktree needs `local.properties` (git-ignored; copy it from another checkout) before Gradle finds the SDK.
- A small build to send to the phone (2026-10-27; a chat attachment takes at most 30 MiB): the release type with the
  Dev package and label, debug-signed, one ABI, compressed native code and dex. An init script
  (`--init-script dev-release.gradle`) sets on `:app` in `afterEvaluate`, for the release build type:
  `applicationIdSuffix = '.debug'`, `versionNameSuffix = '-<sha>-min'`, `manifestPlaceholders['appLabel'] =
  'Screenlate Dev'`, and `android.packaging.jniLibs.useLegacyPackaging = true`,
  `android.packaging.dex.useLegacyPackaging = true`. Build with
  `./gradlew --init-script <file> -Pscreenlate.debugSignedRelease :app:assembleRelease -x lintVitalRelease`, then
  keep only `lib/arm64-v8a/` with the `zipfile` copy above. Sizes: 87 MB debug, 38.6 MB minified with stored
  libraries, 20.6 MB compressed. It installs over local debug builds and keeps their data (same package and key).
  Since 2026-10-28 the same script also sets `ndk.abiFilters` to `arm64-v8a` on every project with an `android`
  extension (no `zipfile` step; 21.6 MB) and adds the debug icon with the bug badge:
  `p.android.sourceSets.getByName('release').res.srcDir('src/debug/res')` (`app/src/debug/res` holds only the two
  launcher drawables). Unlike a debug build it is not debuggable (no WebView DevTools, no `debug_image` or `debug_fullscreen` extras) and has Check for updates. Keep
  `app/build/outputs/mapping/release/mapping.txt` in `testdata/local-research/builds/` for stack traces.
- CI debug APKs are signed with the runner's own debug key: they do not install over a local debug build, nor a
  local one over them, without an uninstall, which deletes the app's data.
- Instrumented tests (2026-10-27): a comma-separated list in `-Pandroid.testInstrumentationRunnerArguments.class`
  ran only its first class; run one class per call. The app module's connected tests uninstall the app afterwards;
  reinstall the debug APK (it comes back at its first run).
- Gradle's incremental packaging reuses the old APK file: after assets are removed (e.g. `ocrCompareData` pointed at a smaller folder) the APK keeps its old size with unused bytes (168 MB for 74 MB of entries, 2026-10-20). Delete the APK before rebuilding.
- The OCR comparison test (`experiments:ocr-compare`) without models in its APK, for the emulator's short /data: stream each model into the test package with `adb exec-in "run-as <test pkg> sh -c 'cat > files/tessdata_fast/tessdata/<lang>.traineddata'" < file`; Tesseract wants a data folder with a `tessdata/` subfolder. This needs `OcrCompareTest` to look in `files/<kind>/` before copying from the assets: a local change of group 5b's run (2026-10-20, on `languages/ocr-compare` 7000a05c), not committed there.
- Overlay gestures with `adb shell input`: a single tap on the floating bubble starts a new scan, which is easier to time than a pull-out. A swipe that ends at the right edge often triggers the system Back gesture and closes the image viewer; start a dock swipe on the bubble itself and end it a few pixels short of the edge. To act while the cloud request is pending: `adb shell svc wifi disable` plus `adb emu network speed gprs` makes it time out after about 16–22 s, and the ML Kit draft arrives after 5–9 s (ML Kit is slow on the emulator; with a fast network the cloud result usually comes first and no draft is shown). Restore with `svc wifi enable` and `network speed full`. More reliable (2026-09-30): a host TCP server that accepts and never answers (`socket.bind(("127.0.0.1", 8899))`, keep the accepted sockets) plus `adb shell settings put global http_proxy 10.0.2.2:8899`; the cloud request hangs until its timeout (about 16-20 s in total) and the ML Kit draft comes after about 6.5 s. The proxy is not subject to `ACCESS_LOCAL_NETWORK`. Revert with `settings put global http_proxy :0`, then `settings delete global http_proxy` (deleting it alone leaves `global_http_proxy_host`/`_port` set and the proxy in force; `dumpsys connectivity | grep -A2 ProxyTracker` shows it). A scan in that state marks the cloud "unavailable" for the rest of that scan only; moving the bubble afterwards just looks up the final text again, so tap the bubble for a fresh scan.
- Chrome on the emulator shows a first-run screen that implies accepting its terms; do not click through it. Use the debug image viewer as the backdrop.
- Gestures: `adb shell input swipe x1 y1 x2 y2 ms` produces DOWN/MOVE/UP; a double tap needs `input motionevent DOWN/UP` twice in one `adb shell` call. The docked bubble sits in the gesture-navigation Back zone; it relies on `systemGestureExclusionRects`.
- `python scripts/text-image.py testdata/ocr/NAME.png "line" ...` renders test images with any text (Noto Sans JP / Yu Gothic); then `debug-device.sh images` and `show NAME.png`. Those fonts lack the Serbian Cyrillic letters (ђ ј љ ћ џ): set `FONT=C:/Windows/Fonts/arial.ttf` for such text.
- Pillow here has no shaping library, so `text-image.py` draws Thai and Khmer marks unstacked and unordered.
  `FONT=<file> FALLBACK=<file> SIZE=64 java scripts/TextImage.java testdata/ocr/NAME.png "line" ...` renders them
  shaped by Java2D. Pull the phone's own font (`adb pull /system/fonts/NotoSansThai-Regular.ttf`), so the image looks
  as the phone draws the script, and a Latin one (Roboto) for the digits and punctuation the script font lacks.
  Windows hands Java its arguments in the ANSI code page, which turns Thai and Khmer into question marks: put the
  lines in a UTF-8 file and pass `@FILE` (each line of the file is a line of the image). A line whose first strong
  letter is right to left (Arabic, Hebrew) is drawn right to left through `TextLayout`, each run in the font that
  has its characters and aligned right; for Arabic use the phone's `NotoNaskhArabic-Regular.ttf`.
- The bubble's aim point on the Pixel 10 Pro emulator is 134 px above the bubble's centre, at the same x: to aim at
  (x, y), hold the bubble and move it to (x, y + 134) before the UP. Dragged from its docked place at the right edge, the aim lands about 43 px right of the finger, so move to (x - 43, y + 134). The bubble stays where it is released; a DOWN at the edge where it no longer is starts the system Back gesture (it closes the debug viewer). Dock it again with `input swipe <bubble> 1262 <y> 400`.
- The popup WebView is debuggable in debug builds. `ADB="D:/Android/Sdk/platform-tools/adb.exe" node scripts/popup-eval.mjs "<js>"` evaluates JS in it (Node 22+, no packages; pass a Windows-style ADB path, Node does not understand `/d/...`). Useful for clicking links, reading rendered state, checking CSS.
- `scripts/ui-dump.sh [regex]` prints on-screen texts with bounds (uiautomator) for choosing tap coordinates. A uiautomator dump reconnects Screenlate's accessibility service (logcat "Service stopped" / "Service connected"), which docks the bubble and ends the scan; during overlay tests use screenshots instead.
- `am start` with `--es open <screen>` to a running `MainActivity` only brings the task forward; the app's own intents add `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` (`am start -f 0x14000000`), which recreates the activity so the request is read.
- AnkiDroid 2.24.1 (x86_64 build from its GitHub releases) is installed on the emulator with the "all files access" app op granted (`appops set --uid com.ichi2.anki MANAGE_EXTERNAL_STORAGE allow`); its collection lives in `/sdcard/AnkiDroid`. On 2026-09-27 it was reset to an empty collection without an AnkiWeb account (`pm clear`); the owner's synced collection was moved to `/sdcard/AnkiDroid-owner-backup` and pulled to `testdata/anki-backup/AnkiDroid` (1.1 GB). `testdata/anki-backup/3 Mining.apkg` is the owner's deck with the customized Senren note type, for importing into the test collection. Never log AnkiDroid into an account or sync from the emulator.
- AnkiDroid's content provider throws for note type ids that no longer exist (e.g. after switching collections); `AnkiDroid` queries return empty results instead.
- Opening a note in AnkiDroid: `anki://x-callback-url/browser?search=nid:<id>[,<id>]` (CardBrowser deep link, AnkiDroid 2.17+). `search=added:1` lists the notes added today, but only in the current deck: tap "Search all decks" within about 2 s of the snackbar. `adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/x.apkg -t application/apkg -n com.ichi2.anki/.IntentHandler` imports a deck.
- Android's regex engine (ICU) rejects an unescaped `}` that the JVM accepts; unit tests on the JVM will not catch it.
- Holding the bubble while moving it in steps (aim at a word, then move off): `input motionevent DOWN x y`, then one `adb shell "input motionevent MOVE x1 y1; input motionevent MOVE x2 y2; ..."`, then `UP`. Each `adb shell` call takes about 0.1 s, so put the MOVE steps into one call.
- `adb shell cmd locale set-app-locales <pkg> --locales es` switches the app's language for checking long labels; it restarts the app and the accessibility service stops (the start screen says the phone stopped it). Reset with `--locales "''"` and run `debug-device.sh install` again.
- Injected touches: every `input motionevent DOWN` needs an `UP`. A DOWN left without UP makes later injected gestures do nothing until an UP is sent.
- Enabling Google's Accessibility Menu once left a floating accessibility button (`settings put secure accessibility_button_mode 0` hides the mode, the button may remain until reboot); keep only Screenlate's service enabled.
- `android.hardware.uwb-service` crash-loops on this image (native SIGABRT in logcat); it is unrelated noise. App crashes show up as `FATAL EXCEPTION` in `adb logcat -d`.
- With /data at 95% and the host heavily loaded (2026-10-22, Slovenian run) the emulator restarted with "dm-verity
  device corrupted", its process then died, and the next boot panicked on an ext4 error of /data; the boot after
  that ran e2fsck (quota counts fixed) and stayed up, app and AnkiDroid data intact. The reason of the last restart
  is in `adb shell dumpsys dropbox --print SYSTEM_LAST_KMSG` ("Last boot reason"). After such restarts Google's
  "Sign in with ease" screen can come up over other apps: close it with Back, never tap through it.
- `scripts/popup-eval.mjs` can hang when the page is not attached; wrap it in `timeout 40`.
- Lens can be probed from the desktop without the app: a small Python client that builds the same protobuf request is handy for comparing full-screen and cropped recognition (the owner's phone screenshots are 1153×2560).
- A first launch (bundled dictionary install) can be checked without wiping the main user's data: `pm create-user t`, `pm install-existing --user N <pkg>`, `am switch-user N`, `am start --user N -n <pkg>/com.vpr.screenlate.MainActivity`; read its registry with `run-as <pkg> --user N cat databases/dictionaries.db*`. A background user cannot start the app (activities need the foreground user, the startup provider is not exported), and `screencap` shows black while user N is in front. Then `am switch-user 0` and `pm remove-user N`; the main user's accessibility service stays on.
- `connectedDebugAndroidTest` uninstalls the debug app when it finishes (settings and dictionaries go with it). Reinstall with `scripts/debug-device.sh install`, which also enables the accessibility service again.
- The system file picker (backup create/restore) is driven with uiautomator: tap the file's name text, not its thumbnail, which opens a preview. Backups created on the emulator land in `Download/`.
- The Quick Settings tile can be tested with `adb shell cmd statusbar add-tile|click-tile <pkg>/com.vpr.screenlate.overlay.BubbleTileService`.
- The owner's Anki desktop runs on this machine with a local audio add-on listening on `127.0.0.1:5050` and `127.0.0.1:8770`. Never bind a mock server there and never `adb reverse` those ports: on Windows Python's `allow_reuse_address` lets a second server bind next to a running one. Use another port (5077 worked) with `ThreadingHTTPServer.allow_reuse_address = False` and `adb reverse tcp:5077 tcp:5077` (loopback on the device needs no `ACCESS_LOCAL_NETWORK`); remove the reverse afterwards.
- Old dictionary copies (e.g. a kty-* Wiktionary) can be faked with a handmade Yomitan archive: `index.json` with the old title, `indexUrl`/`downloadUrl`, an old revision and `isUpdatable: true`, plus a one-entry `term_bank_1.json`. Push it to `Download/`, run the media scanner on it (`am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/<name>.zip`) and import it from file. `adb emu network speed edge` hardly slowed downloads (665 KB in 3 s); to watch a catalog card's ring, use a larger archive (wty-ja-ja, 13 MB, about 6 s). Reset with `adb emu network speed full`.
- Android 17 emulator with targetSdk 37: apps need `ACCESS_LOCAL_NETWORK` (runtime, "nearby devices" group) for private, link-local and `.local` addresses, and `10.0.2.2` counts as one. Without it a TCP connect just times out (`SocketTimeoutException`); `adb shell` (e.g. `nc 10.0.2.2 <port>`) is not affected, so it proves the host side works. Loopback stays allowed. A mock HTTP server on the desktop, reached at `http://10.0.2.2:<port>`, is the easiest way to test audio sources on the home network.
- Bundled dictionaries are cached in `dicts/bundled/` with the URL of each file next to it (`<name>.url`); a file whose
  URL changed is downloaded again, and a `.zip` must be a readable archive (`DownloadCache`). CI keys its copy of the
  folder by `app/build.gradle.kts` and `DownloadCache.kt`. Release builds read the version from `version.txt`, which
  the release workflow writes from the tag; other builds take the latest `vX.Y.Z` tag (pre-release tags are skipped).
- DataStore's file storage cannot rename its new file over an existing one on Windows (`Unable to rename …tmp`), so a
  JVM test that writes a file-backed store twice, or replaces a damaged file, fails locally. Use an in-memory
  `DataStore` in unit tests (`EInkSizesTest`), or skip the test on Windows when the file itself is under test
  (`SettingsDataStoreTest`; CI runs it on Linux).
- Cloud sessions have no Android SDK, so Gradle cannot build the Android modules. Plain-Kotlin classes and their JUnit
  tests (e.g. `overlay/fonts/CssCheck`) run with the compiler from the JetBrains GitHub release
  (`kotlin-compiler-<version>.zip`) and JUnit, Hamcrest, Truth, Guava and failureaccess jars from Maven Central:
  `kotlinc -cp <jars> -d out <class>.kt <test>.kt`, then `java -cp out:<jars>:kotlinc/lib/kotlin-stdlib.jar
  org.junit.runner.JUnitCore <test class>`. Page scripts run with `npm --prefix scripts/page-tests ci/test`, and the
  preinstalled global Playwright (`npm root -g`) drives Chromium to check how a browser reads CSS or URLs.
- `uiautomator dump` and `uiautomator events` connect a UiAutomation, which unbinds every other accessibility service
  while it is connected: Screenlate's service gets `onDestroy`, the bubble goes, and it reconnects when the command
  ends. Screens that show the service state (the start screen) change layout during a dump, so tap them by screenshot
  coordinates. The same effect reproduces "service enabled, process alive, service not bound" (the start screen's
  stopped state): keep `adb shell uiautomator events` running in the background and look at the app meanwhile.
- To reset one DataStore preference of the debug app (e.g. `notification_permission_asked`): take the service out of
  `enabled_accessibility_services` (`settings delete secure ...`), `am kill` the app, pull
  `files/datastore/settings.preferences_pb` with `run-as ... cat`, drop the map entry (top-level field 1 entries, key in
  field 1), write it back through `/data/local/tmp` with `run-as ... sh -c 'cat ... > ...'`, and put the service back.
  Git Bash rewrites device paths: set `MSYS_NO_PATHCONV=1`. Pass the device command as one argument,
  `adb shell "run-as <package> sh -c 'cat /data/local/tmp/x > files/...'"`: unquoted, the device's outer shell does the
  redirect as the shell user (permission denied, or a relative path that does not exist). `am kill` stops only a
  background process: press Home first. Never `am force-stop`: it drops the service from the enabled
  list.
- Running every module's unit tests at once can crash a test executor with "insufficient memory" (G1 virtual space
  mmap) while the emulator runs; Gradle reports "Test process encountered an unexpected problem" and the JVM leaves
  `hs_err_pid*.log` in the module (ignored by git). Rerun; it passes when memory is free.

## MNN (2026-10-17)

- `core:ocr-mnn` builds MNN 3.6.1 from the submodule `core/ocr-mnn/src/main/cpp/MNN` (shallow) with CMake
  `add_subdirectory`, CPU backend only, always at `-O3` (also in debug builds), into `libscreenlate_mnn.so`. A clean
  native build of all four ABIs takes several minutes; the debug APK with every ABI is about 124 MB.
- CI's Gradle wrapper validation scans the submodules too: MNN's flatbuffers sample carries a wrapper JAR it does not
  know. `.github/actions/setup` runs `gradle/actions/wrapper-validation` with that checksum allowed
  (`setup-gradle@v6` has no `allow-checksums` input) and turns off setup-gradle's own validation.
- The model converter is the pip package `mnn` of the same version (`mnnconvert`); it pip-installs a logging SDK on
  start. Paddle archives: `scripts/ocr-models/paddle_models.py` (see `docs/development.md`).

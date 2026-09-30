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
- Compose: `ScreenlateTheme` must compose its content on one path for every mode (only the color scheme and locals change). A separate branch for e-ink mode rebuilt the NavHost and sent the app back to the home screen when the mode was switched.
- `resValues` build feature is off by default in AGP 9; the app label per build type uses a manifest placeholder (`appLabel`).

## Agent tooling gotchas

- Bash heredocs in this environment turn `\\` into `\`, even with a quoted delimiter. Write files that contain backslashes (regexes, escapes) with the Write/Edit tools.
- `cd` inside a Bash call can move the session's working directory; use absolute paths.
- `/tmp` in Git Bash is not the same path Windows Python sees; pipe data into Python through stdin or use the scratchpad directory.

## Devices

- Emulator AVD `Pixel_10_Pro`: 1280×2856, density 480 (3.0), Android 17 (API 37.2), Play Store image with 16 KB pages. Native libraries (hoshidicts) must be 16 KB aligned: use NDK r28+ or the equivalent linker flags.
- `adb`: `D:\Android\Sdk\platform-tools\adb.exe`. In Git Bash set `MSYS_NO_PATHCONV=1`, otherwise device paths like `/sdcard/...` get rewritten to Windows paths.
- `scripts/debug-device.sh` wraps the routine: `install` (installs the debug APK and enables the accessibility service, retrying because the system drops the service shortly after a package update), `images` (copies `testdata/ocr/*` into the app's internal files via `run-as`), `show <file>` (opens the debug full-screen image viewer), `shot <out.png>`.
- Never use `am start -S` or `am force-stop` on the app while testing the overlay: force-stopping removes the accessibility service from the enabled list.
- Files pushed by adb into `/sdcard/Android/data/<pkg>` are not readable by the app (group `ext_data_rw`); copy through `run-as` into internal storage instead.
- Overlay gestures with `adb shell input`: a single tap on the floating bubble starts a new scan, which is easier to time than a pull-out. A swipe that ends at the right edge often triggers the system Back gesture and closes the image viewer; start a dock swipe on the bubble itself and end it a few pixels short of the edge. To act while the cloud request is pending: `adb shell svc wifi disable` plus `adb emu network speed gprs` makes it time out after about 16–22 s, and the ML Kit draft arrives after 5–9 s (ML Kit is slow on the emulator; with a fast network the cloud result usually comes first and no draft is shown). Restore with `svc wifi enable` and `network speed full`. More reliable (2026-09-30): a host TCP server that accepts and never answers (`socket.bind(("127.0.0.1", 8899))`, keep the accepted sockets) plus `adb shell settings put global http_proxy 10.0.2.2:8899`; the cloud request hangs until its timeout (about 16-20 s in total) and the ML Kit draft comes after about 6.5 s. The proxy is not subject to `ACCESS_LOCAL_NETWORK`. Revert with `settings delete global http_proxy`. A scan in that state marks the cloud "unavailable" for the rest of that scan only; moving the bubble afterwards just looks up the final text again, so tap the bubble for a fresh scan.
- Chrome on the emulator shows a first-run screen that implies accepting its terms; do not click through it. Use the debug image viewer as the backdrop.
- Gestures: `adb shell input swipe x1 y1 x2 y2 ms` produces DOWN/MOVE/UP; a double tap needs `input motionevent DOWN/UP` twice in one `adb shell` call. The docked bubble sits in the gesture-navigation Back zone; it relies on `systemGestureExclusionRects`.
- `python scripts/text-image.py testdata/ocr/NAME.png "line" ...` renders test images with any text (Noto Sans JP / Yu Gothic); then `debug-device.sh images` and `show NAME.png`.
- The popup WebView is debuggable in debug builds. `ADB="D:/Android/Sdk/platform-tools/adb.exe" node scripts/popup-eval.mjs "<js>"` evaluates JS in it (Node 22+, no packages; pass a Windows-style ADB path, Node does not understand `/d/...`). Useful for clicking links, reading rendered state, checking CSS.
- `scripts/ui-dump.sh [regex]` prints on-screen texts with bounds (uiautomator) for choosing tap coordinates. A uiautomator dump reconnects Screenlate's accessibility service (logcat "Service stopped" / "Service connected"), which docks the bubble and ends the scan; during overlay tests use screenshots instead.
- `am start` with `--es open <screen>` to a running `MainActivity` only brings the task forward; the app's own intents add `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` (`am start -f 0x14000000`), which recreates the activity so the request is read.
- AnkiDroid 2.24.1 (x86_64 build from its GitHub releases) is installed on the emulator with the "all files access" app op granted (`appops set --uid com.ichi2.anki MANAGE_EXTERNAL_STORAGE allow`); its collection lives in `/sdcard/AnkiDroid`. On 2026-09-27 it was reset to an empty collection without an AnkiWeb account (`pm clear`); the owner's synced collection was moved to `/sdcard/AnkiDroid-owner-backup` and pulled to `testdata/anki-backup/AnkiDroid` (1.1 GB). `testdata/anki-backup/3 Mining.apkg` is the owner's deck with the customized Senren note type, for importing into the test collection. Never log AnkiDroid into an account or sync from the emulator.
- AnkiDroid's content provider throws for note type ids that no longer exist (e.g. after switching collections); `AnkiDroid` queries return empty results instead.
- Opening a note in AnkiDroid: `anki://x-callback-url/browser?search=nid:<id>[,<id>]` (CardBrowser deep link, AnkiDroid 2.17+). `adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/x.apkg -t application/apkg -n com.ichi2.anki/.IntentHandler` imports a deck.
- Android's regex engine (ICU) rejects an unescaped `}` that the JVM accepts; unit tests on the JVM will not catch it.
- Injected touches: every `input motionevent DOWN` needs an `UP`. A DOWN left without UP makes later injected gestures do nothing until an UP is sent.
- Enabling Google's Accessibility Menu once left a floating accessibility button (`settings put secure accessibility_button_mode 0` hides the mode, the button may remain until reboot); keep only Screenlate's service enabled.
- `android.hardware.uwb-service` crash-loops on this image (native SIGABRT in logcat); it is unrelated noise. App crashes show up as `FATAL EXCEPTION` in `adb logcat -d`.
- `scripts/popup-eval.mjs` can hang when the page is not attached; wrap it in `timeout 40`.
- Lens can be probed from the desktop without the app: a small Python client that builds the same protobuf request is handy for comparing full-screen and cropped recognition (the owner's phone screenshots are 1153×2560).
- `connectedDebugAndroidTest` uninstalls the debug app when it finishes (settings and dictionaries go with it). Reinstall with `scripts/debug-device.sh install`, which also enables the accessibility service again.
- The system file picker (backup create/restore) is driven with uiautomator: tap the file's name text, not its thumbnail, which opens a preview. Backups created on the emulator land in `Download/`.
- The Quick Settings tile can be tested with `adb shell cmd statusbar add-tile|click-tile <pkg>/com.vpr.screenlate.overlay.BubbleTileService`.
- Android 17 emulator with targetSdk 37: apps need `ACCESS_LOCAL_NETWORK` (runtime, "nearby devices" group) for private, link-local and `.local` addresses, and `10.0.2.2` counts as one. Without it a TCP connect just times out (`SocketTimeoutException`); `adb shell` (e.g. `nc 10.0.2.2 <port>`) is not affected, so it proves the host side works. Loopback stays allowed. A mock HTTP server on the desktop, reached at `http://10.0.2.2:<port>`, is the easiest way to test audio sources on the home network.
- Bundled dictionaries are cached in `dicts/bundled/` with the URL of each file next to it (`<name>.url`); a file whose
  URL changed is downloaded again, and a `.zip` must be a readable archive (`DownloadCache`). CI keys its copy of the
  folder by `app/build.gradle.kts` and `DownloadCache.kt`. Release builds read the version from `version.txt`, which
  the release workflow writes from the tag; other builds take the latest `vX.Y.Z` tag (pre-release tags are skipped).
- DataStore's file storage cannot rename its new file over an existing one on Windows (`Unable to rename …tmp`), so a
  JVM test that writes a file-backed store twice, or replaces a damaged file, fails locally. Use an in-memory
  `DataStore` in unit tests (`EInkSizesTest`), or skip the test on Windows when the file itself is under test
  (`SettingsDataStoreTest`; CI runs it on Linux).

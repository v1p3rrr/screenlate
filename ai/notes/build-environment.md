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
- Chrome on the emulator shows a first-run screen that implies accepting its terms; do not click through it. Use the debug image viewer as the backdrop.
- Gestures: `adb shell input swipe x1 y1 x2 y2 ms` produces DOWN/MOVE/UP; a double tap needs `input motionevent DOWN/UP` twice in one `adb shell` call. The docked bubble sits in the gesture-navigation Back zone; it relies on `systemGestureExclusionRects`.
- `python scripts/text-image.py testdata/ocr/NAME.png "line" ...` renders test images with any text (Noto Sans JP / Yu Gothic); then `debug-device.sh images` and `show NAME.png`.
- The popup WebView is debuggable in debug builds. `ADB="D:/Android/Sdk/platform-tools/adb.exe" node scripts/popup-eval.mjs "<js>"` evaluates JS in it (Node 22+, no packages; pass a Windows-style ADB path, Node does not understand `/d/...`). Useful for clicking links, reading rendered state, checking CSS.
- `scripts/ui-dump.sh [regex]` prints on-screen texts with bounds (uiautomator) for choosing tap coordinates.
- AnkiDroid 2.24.1 (x86_64 build from its GitHub releases) is installed on the emulator with an empty collection and the "all files access" app op granted (`appops set --uid com.ichi2.anki MANAGE_EXTERNAL_STORAGE allow`); Screenlate Dev has the AnkiDroid permission and exports to Default / Basic.
- Android's regex engine (ICU) rejects an unescaped `}` that the JVM accepts; unit tests on the JVM will not catch it.
- The Quick Settings tile can be tested with `adb shell cmd statusbar add-tile|click-tile <pkg>/com.vpr.screenlate.overlay.BubbleTileService`.

# Screenlate

<img src="docs/images/icon.svg" width="96" height="96" alt="">

Android pop-up dictionary that works over any app. Pull a bubble out from the screen edge, point it at a word, and get a dictionary entry next to it.

- Cloud text recognition with an on-device draft (ML Kit), or the app's own text when it is available.
- Yomitan dictionaries, looked up, sorted and rendered the way Yomitan does it: structured content, images, frequencies, pitch accent, deinflection, kanji entries.
- Jitendex, a frequency list and pitch accents are bundled; more dictionaries come from a download catalog, from `.zip` files or from a Yomitan dictionary collection export.
- Anki export through AnkiDroid with a configurable deck, note type and field templates, duplicate handling, audio from Yomitan's set of sources and cropped screenshots.
- A search screen and a "Look up in Screenlate" entry in the text selection menu.
- Settings from a Yomitan settings export: dictionary order, Anki, audio, lookup and popup appearance.
- The popup's font, text size and custom CSS; kanji keep Japanese glyph forms.

Japanese is the only supported language for now.

## Status

Usable, still in development. [docs/usage.md](docs/usage.md) explains gestures and settings, [docs/architecture.md](docs/architecture.md) the internals. Progress and plans are in [ai/](ai).

## Requirements

- Android 11 (API 30) or newer.
- AnkiDroid for Anki export.
- Network access for cloud text recognition (on-device recognition works offline).

## Building

- JDK 17 or newer for Gradle (the daemon JVM is provisioned automatically).
- Android SDK with platform 37.

```
git submodule update --init --recursive
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Other checks:

- `npm --prefix scripts/page-tests ci && npm --prefix scripts/page-tests test` tests the popup page scripts in a simulated DOM (Node 22 or newer).
- `./gradlew connectedDebugAndroidTest` runs the instrumented tests on a device or emulator. The AnkiDroid test runs only where AnkiDroid is installed and adds notes to a "Screenlate Test" deck, so use a device whose AnkiDroid is not synced with a real account.

The dictionary engine is native code: the build needs NDK 29.0.14206865 and CMake 3.31.6 from the SDK manager. The first build downloads the bundled dictionaries (about 48 MB) into `dicts/bundled/`.

Debug builds install as `com.vpr.screenlate.debug` and can live next to a release build. `scripts/debug-device.sh` installs a debug build on a connected device and enables the accessibility service.

### Release signing

Release builds are signed only if `keystore.properties` exists in the project root:

```
storeFile=path/to/screenlate.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Both files are ignored by git.

## After installing

1. Enable the Screenlate accessibility service. The app's start screen links to the settings.
2. On devices with aggressive battery management, allow the app to run in the background, or the system may stop the service.
3. If the service switch is greyed out after installing from an APK file, use App info → ⋮ → Allow restricted settings.

## Project layout

| Module | Purpose |
|---|---|
| `app` | Application, Compose screens |
| `overlay` | Accessibility service, bubble, popup window |
| `core:common` | Shared models and settings, per-language behavior |
| `core:ocr` | Cloud and on-device OCR, hit testing |
| `core:anki` | AnkiDroid integration, note templates, audio sources |
| `dictionary:api` | Dictionary engine interface, dictionary registry |
| `dictionary:engine-hoshidicts` | Engine based on hoshidicts (GPL-3.0) |
| `dictionary:render-yomitan` | Yomitan-style entry renderer (GPL-3.0) |

GPL-licensed third-party code is kept in the last two modules so it can be replaced without touching the rest of the app.

## License

GPL-3.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for third-party components and dictionary attributions.

Cloud text recognition uses an unofficial endpoint and may stop working at any time; on-device recognition is the fallback.

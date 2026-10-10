# Development

[architecture.md](architecture.md) describes the modules and how a lookup flows through them. The development plan, decisions and working notes live in [ai/](../ai).

## Building

- JDK 17 or newer for Gradle (the daemon JVM is provisioned automatically).
- Android SDK with platform 37, NDK 29.0.14206865 and CMake 3.31.6 (the dictionary engine is native code).
- Node 22 or newer for the page script tests.

```
git submodule update --init --recursive
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The first build downloads the bundled dictionaries (about 25 MB) into `dicts/bundled/`; a file is downloaded again when its URL in `app/build.gradle.kts` changes.

## Dictionary catalog

The download catalog is `dictionary/api/src/main/assets/catalog/dictionaries-v2.json` (format 2); the app also fetches
it from the repository's `main` branch. Entries have a category (main, forms, frequency, pronunciation, short
translations, characters, on-device recognition model), the source and gloss language, a "recommended" mark (ticked when a language is turned on),
the archive size in bytes and, where measured, the size after import. Hand-written entries carry their descriptions in
all interface languages. Wiktionary entries come from `node scripts/catalog/generate.mjs`, which reads the Hugging Face
listing of wty-release for the supported source languages and the interface languages as gloss languages, skips
archives under 0.5 MB, and fills titles and descriptions from the templates in the file. It also writes
`dictionaries.json`, the format 1 copy with the Japanese entries that older app versions read. `--tree <file>` uses a
recorded listing, `--record <file>` saves one.

A model entry (category `OCR_MODEL`) has no dictionary kind but names its model id, engine, version and SHA-256; the
app installs the download only when the checksum matches. Entries of several languages may name the same model.

Frequency dictionaries for languages without one are built from wordfreq: `pip install wordfreq`, then
`python scripts/frequency/wordfreq_dictionary.py <language> <out.zip>` writes a rank-based Yomitan dictionary of the
100,000 most frequent words (`--size` changes it), each also in its capitalized form, with wordfreq's attribution in
the index; `--download-url` and `--index-url` make it updatable once hosted. Its data is CC BY-SA 4.0, and so is the
dictionary.

Debug builds install as `com.vpr.screenlate.debug` and can live next to a release build. `scripts/debug-device.sh install` installs a debug build on a connected device or emulator and enables the accessibility service.

## Tests

- `./gradlew testDebugUnitTest`: JVM unit tests of every module.
- `npm --prefix scripts/page-tests ci && npm --prefix scripts/page-tests test`: the popup page scripts (`popup.js`, `anki.js`, `note.js`, `definition.js`) in a simulated DOM.
- `node --test scripts/catalog/generate.test.mjs`: the catalog generator, against a recorded listing.
- `python -m unittest discover scripts/frequency`: the frequency dictionary builder (wordfreq itself is not needed).
- `./gradlew connectedDebugAndroidTest`: instrumented tests on a device or emulator. The AnkiDroid test runs only where AnkiDroid is installed and adds notes to a "Screenlate Test" deck, so use a device whose AnkiDroid is not synced with a real account.

## Project layout

| Module | Purpose |
|---|---|
| `app` | Application, Compose screens |
| `overlay` | Accessibility service, bubble, popup window |
| `core:common` | Shared models and settings, per-language behavior |
| `core:ocr` | Cloud and on-device OCR, hit testing |
| `core:anki` | AnkiDroid integration, note templates, audio sources |
| `core:translate` | Sentence translation through free online services |
| `dictionary:api` | Dictionary engine interface, dictionary registry |
| `dictionary:engine-hoshidicts` | Engine based on hoshidicts (GPL-3.0) |
| `dictionary:render-yomitan` | Yomitan-style entry renderer (GPL-3.0) |

GPL-licensed third-party code is kept in the last two modules so it can be replaced without touching the rest of the app. Every third-party addition gets an entry in [NOTICE](../NOTICE).

## Versions and releases

The version comes from git tags: `vX.Y.Z` gives the version name X.Y.Z and the version code X·10000 + Y·100 + Z.

GitHub Actions:

- `ci.yml` builds and tests every push and keeps the debug APKs of the last three runs.
- `release.yml` runs for a tag `vX.Y.Z` (or by hand): per-ABI and universal APKs, a source archive with submodules, and the GitHub Release. The notes come from `.github/release-notes/vX.Y.Z.md` if it exists, otherwise from the commits since the previous tag.
- `instrumented.yml` runs the instrumented tests on an emulator with AnkiDroid, for tags or by hand.

### Release signing

Release builds are signed only if `keystore.properties` exists in the project root:

```
storeFile=path/to/screenlate.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Both files are ignored by git. Write the path with forward slashes, also on Windows: a backslash in this file is an escape character, so a password should not contain one either.

For releases from CI, the same values go into the repository secrets `SIGNING_KEYSTORE_BASE64` (the key file in Base64), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Without them the release workflow builds but does not publish. Keep a backup of the key file and its passwords: updates must be signed with the same key, or users have to uninstall the app first.

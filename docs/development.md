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

Debug builds install as `com.vpr.screenlate.debug` and can live next to a release build; they are named "Screenlate Dev", also in the text selection menu ("Look up in Screenlate Dev", `app/src/debug/res`). `scripts/debug-device.sh install` installs a debug build on a connected device or emulator and enables the accessibility service.

Camera capture and Compose navigation are in `app`; persistent photos and the exclusive lookup-source contract are in
`core:photos`. `CameraViewModelTest` covers replacement, cancellation, failures and source lifetime with a fake store.
`PhotoDrawingTest` checks all EXIF transforms and display sampling; `PhotoViewerTest` checks the actual painted pixels
and reported viewport on a device, without Espresso's input injector. `LiveCameraTest` drives preview focus, pinch zoom
and zoom reset on API 30, with CAMERA granted to the debug app. Run a class by installing the app and its test APK,
then `adb shell am instrument -w -e class com.vpr.screenlate.camera.PhotoViewerTest com.vpr.screenlate.debug.test/androidx.test.runner.AndroidJUnitRunner`. Keep camera quality, flash and
real-page focus checks separate from the emulator's synthetic camera. Photographs stay outside cache and backup storage.

Photo recognition tests are independent of screen scans: `PhotoTilesTest`/`PhotoAssemblyTest` cover the grid and seams;
`PhotoRecognitionJobsTest` exercises terminal publication, two-worker bounds, retry and cancellation with fake engines.
`PhotoStoreTest`/`AttemptCodecTest` cover recoverable progress. Instrumented `PhotoRegionsTest` checks original pixels
for all EXIF transforms, and `PhotoDatabaseTest` verifies Room migration and progress restoration. `PhotoViewerGestureTest`
checks real Compose pan/zoom, unchanged source identity and fit on reopen on API 30 (API 37's Espresso input injector
is not supported). `PhotoWorkflowTest` replaces the current photo with a synthetic page only on emulators and runs
real local recognition; its opt-in `-e photoFixture partialCloud` uses cloud recognition and leaves one missing region
for manual warning/retry checks. It sends only that generated page. Neither workflow touches Anki.

`PhotoStoreTest` covers image pins through replacement/deletion and cleanup after restart. `PhotoImageTest` checks
fractional original-pixel crops for all eight EXIF orientations and crops larger than the sampled preview.
`PhotoGalleryTest` exercises publication/rollback; `PhotoGalleryDeviceTest` verifies ordinary JPEG/PNG bytes,
orientation and MediaStore visibility. `PhotoImportsTest` covers the import copy (kept, converted, rejected, no
partial file), `ShareImageActivityTest` the links a share may carry; instrumented `PhotoImportDeviceTest` checks that
a JPEG keeps its bytes and a BMP becomes a PNG the viewer reads. `CropPlacementTest`, `CropEditorTest` and
`ScreenNoteImageTest` cover source coordinates, screen regressions and cancellation, including a scan closed while the
preview loads; `WebpSizeTest` the note picture scaled under WebP's side limit, which instrumented `WebpLimitTest`
confirms on the device. Run the native crop/gallery classes on API 30 and API 37. `PhotoAnkiFixtureTest` is opt-in and
emulator-only: `-e photoAnki prepare` backs up Japanese Anki settings and selects a dedicated local test deck/model;
`verify` checks the generated sentence and image (`photoWord` selects the fixture word), and `restore` reinstates the
original settings. Use only an unsynced test collection and always restore the settings after a manual popup-to-Anki
check.

The photo history is tested in `PhotoStoreTest` (saving, order, limits, opening, deletion of the current photo),
`HistoryLimitsTest`, `CameraPreferencesTest` and the app's `CameraViewModelTest`, `PhotoHistoryViewModelTest` and
`CameraSettingsViewModelTest`, which share fakes in `HistoryFakes.kt`. Instrumented `PhotoDatabaseTest` covers the
v2→v3 migration and the upright thumbnail; `UnsavedPhotoDialogTest` (API 30) checks that the dialog's answer reaches
the caller before it is dismissed.

## Dictionary catalog

The download catalog is `dictionary/api/src/main/assets/catalog/dictionaries-v2.json` (format 2); the app also fetches
it from the repository's `main` branch. Entries have a category (main, forms, frequency, pronunciation, short
translations, characters, on-device recognition model), the source and gloss language, a "recommended" mark (ticked when a language is turned on),
the archive size in bytes and, where measured, the size after import. Hand-written entries carry their descriptions in
all interface languages. Wiktionary entries come from `node scripts/catalog/generate.mjs`, which reads the Hugging Face
listing of wty-release for the supported source languages and the interface languages as gloss languages, skips
archives under 0.5 MB, and fills titles and descriptions from the templates in the file. A language may read folders
under other codes (Norwegian: Bokmål's nb, the editions' no and Nynorsk's nn); their entries keep the folder's code in
the title and its language name in the description, and each gloss language is ticked from the first folder that has
it. A language whose forms only the English edition lists (`FORMS_SOURCES`: Finnish, Hungarian, Estonian, Azerbaijani, Uzbek) also gets a "Word forms" entry,
the source–English archive with `"build": "forms"`, mandatory beside the main dictionary; the download screen lists it only while the whole
English dictionary is not ticked, the app imports it without definitions, and an installed English dictionary
counts as it. It also writes
`dictionaries.json`, the format 1 copy with the Japanese entries that older app versions read. `--tree <file>` uses a
recorded listing, `--record <file>` saves one.

The download screen's size after import comes from the entry's `installedSize`, else from
`catalog/installed-sizes.json` (sizes measured on a device, scaled to the entry's current archive), else from a share
of the archive per kind. After importing new catalog dictionaries on an emulator, refresh the file with the debug
build's listing and registry: `python -I scripts/catalog/installed_sizes.py <du listing> <dictionaries.db>` (the
script's header has the `adb` commands); it keeps the sizes of entries not installed there.

A model entry (category `OCR_MODEL`) has no dictionary kind but names its model id, engine, version and SHA-256; the
app installs the download only when the checksum matches. Entries of several languages may name the same model.

Frequency dictionaries for languages without one are built from wordfreq on the device: a catalog entry with
`"build": "wordfreq"` points at one of wordfreq's word lists at a pinned commit, with its size, SHA-256 and wordfreq
version. The import job downloads the list, checks the checksum and converts it (`WordfreqDictionary`) into a
rank-based Yomitan dictionary of the 100,000 most frequent words, each also in its capitalized form, with wordfreq's
attribution in the index; the imported dictionary sorts its language's results unless another one was chosen.
`python scripts/frequency/wordfreq_catalog.py` writes these entries (template `wordfreq`) for a new commit or language;
it keeps the downloaded lists in `testdata/wordfreq/`, where a unit test also builds the English one. The same
dictionary can be built on a computer: `pip install wordfreq`, then
`python scripts/frequency/wordfreq_dictionary.py <language> <out.zip>` (`--size` changes the number of words). Its data
is CC BY-SA 4.0, and so is the dictionary.

A language wordfreq has no list for gets one from a corpus' word counts (`"build": "corpus-counts"`, Thai: the Thai
National Corpus counts that PyThaiNLP publishes, CC0, one `word<TAB>count` line per word). `CorpusCountsDictionary`
ranks them in the list's order, leaving out tokens without a letter or with a space.
Kazakh's, Kyrgyz's, Latin's, Esperanto's, Albanian's, Swahili's, Estonian's, Azerbaijani's, Uzbek's and Mongolian's come from Leipzig Corpora Collection archives (`kaz_newscrawl_2016_100K.tar.gz`,
`kir_newscrawl_2016_100K.tar.gz`, `lat_wikipedia_2021_100K.tar.gz`, `epo_web_2012_100K.tar.gz`, `sqi_news_2020_100K.tar.gz`, `swa_news_2020_30K.tar.gz`, `est_news_2020_100K.tar.gz`, `aze_news_2020_30K.tar.gz`, `uzb_news_2020_30K.tar.gz`, `mon_news_2020_100K.tar.gz`, CC BY): the builder reads the `.tar.gz` as a stream, keeps only the `-words.txt`
list (`id<TAB>word<TAB>count`, its place in the archive varies), lowercases the words, spells them as the dictionaries
do (`LanguageSupport.frequencyWord`: Latin i inside a Cyrillic word as і in Kazakh, Latin words without macrons and breves, Macedonian Latin è and look-alikes as ѐ and the Macedonian letters), adds up the counts of tokens that
become one word and leaves out tokens that are not words.
`python scripts/frequency/corpus_catalog.py` checks the list and writes its entry (templates `tnc`, `leipzig`, `leipzig-wikipedia` and `leipzig-web`),
keeping the downloads in `testdata/corpus/`, where unit tests also build them.

On-device recognition models are catalog entries of the category `OCR_MODEL` with the model's id, engine, version and
SHA-256. Tesseract's models are the traineddata files of `tessdata_fast` and `tessdata_best` at a pinned commit,
downloaded unchanged and kept as `tessdata/<code>.traineddata` (the entry's `file`):
`python -I scripts/ocr-models/tessdata_catalog.py` writes their entries (templates `tesseract-fast` and
`tesseract-best`) for a new commit or language and keeps the downloads in `testdata/tessdata/`. A model's version is the
start of its SHA-256, so moving the pin offers an update only for the files that changed.

PaddleOCR models run on MNN, built from the submodule `core/ocr-mnn/src/main/cpp/MNN` (CPU backend only) into
`libscreenlate_mnn.so`. Their archives are built by `python -I scripts/ocr-models/paddle_models.py --mnnconvert <path>`:
it downloads PaddlePaddle's ONNX models from Hugging Face at pinned commits (checked by SHA-256), converts them with
MNN's converter (pip package `mnn` of the same version as the submodule), takes a recognizer's character list from
its `inference.yml` into `dict.txt`, and zips each model with the Apache License and a note on its source; a detector
with its own thresholds gets `detector.properties`. It writes the archives to `testdata/ocr/paddle/archives` and the
catalog entries (templates `paddle-v5`, `paddle-v6`, `paddle-detector-v5`, `paddle-detector-v6`), whose download URLs
point at the GitHub release `ocr-models-1`, which holds model files only and is never marked latest; new archives go
into it, so the update check and the README link keep pointing at the app. A recognizer requires its detector (`requires`); the
detector is downloaded with it and not listed on its own. The recommended marks follow the measurements in
`ai/notes/ocr-engines.md`: one model per language, or every model listed in `MORE_ACCURATE` of either script for a
language that several read more accurately than the built-in recognizer (Welsh); a marked model of a language the
built-in recognizer reads stays unticked at setup.

The round flags that stand for languages (`overlay/src/main/res/drawable/flag_*.xml`) are generated from circle-flags
(MIT) at a pinned commit: `python scripts/flags/circle_flags.py overlay/src/main/res/drawable`. A language support
class lists its flag countries (`flagCountries`); add a new country to `COUNTRIES` in the script and to `Flags` in
`overlay`, whose unit test checks that every listed country has a drawable. A language with a flag of its own (Latin, Esperanto)
lists it as `lang_<code>`, taken from circle-flags' `language/<code>.svg`; a region's flag (Wales) by its circle-flags
name (`gb-wls`), whose drawable takes `_` for the hyphen (`flag_gb_wls`).

Downloadable fonts in `overlay/src/main/assets/fonts/catalog.json` are offered either for the languages they list in
`languages` (one download serves all of them) or by their Google Fonts subsets. Those offered by subsets list the
letters they have: `python scripts/fonts/font_letters.py overlay/src/main/assets/fonts/catalog.json --write`
(fontTools) writes them as hex ranges. Such a font is offered for a language only when it has the letters of the
language's `fontLetters`; run the script after adding or updating one.

The table of Simplified and Traditional Chinese characters (`ChineseScriptTable.kt` in `core:common`) is generated
from the Unihan database (Unicode License V3) and CC-CEDICT (CC BY-SA 4.0):
`python scripts/chinese-scripts/build.py <Unihan.zip> <CC-CEDICT.zip> core/common/src/main/java/com/vpr/screenlate/core/common/language/ChineseScriptTable.kt`,
with `Unihan.zip` from `https://www.unicode.org/Public/<version>/ucd/` (kept in `testdata/unihan/`) and the Yomitan
build of CC-CEDICT from the catalog. The table is CC BY-SA 4.0, like CC-CEDICT.

The Korean hanja dictionary (`dictionary/api/src/main/assets/catalog/unihan-hanja.zip`, installed from the APK through
its `asset:///` catalog URL) is built from the same Unihan archive:
`python scripts/hanja/unihan_hanja.py <Unihan.zip> dictionary/api/src/main/assets/catalog/unihan-hanja.zip`. The
archive is reproducible: the same Unihan gives the same bytes. Its catalog entry's size must follow a rebuild
(`CatalogFileTest` checks it against the asset).

Four converters build dictionaries that have no Yomitan release; their archives are not in the catalog yet, as they
need hosting. `python scripts/freedict/freedict_yomitan.py <freedict-deu-rus-<edition>.src.tar.xz> <out.zip>` (or the
`.tei` file itself) converts a FreeDict pair generated by WikDict (CC BY-SA 3.0) or an older FreeDict pair whose
header lacks the languages (slv-eng, GPL-3.0+; the languages then come from the file name, the authors and the
license from the header): one definition per sense, the source-language definition above its translations (below
them when it is longer than 120 characters; WikDict joins the definitions of several senses into one), parts of
speech and genders as tags (genders only on nouns and names), pinyin readings for Chinese; the Italian Wiktionary's
links to Wikipedia, Wikispecies and Wikiquote ("( approfondimento)") are dropped.
`python scripts/openrussian/openrussian_yomitan.py <csv folder> <en|de> <out.zip> --revision <export date>` converts
the OpenRussian.org word lists (CC BY-SA 4.0; the tab-separated `nouns.csv`, `verbs.csv`, `adjectives.csv` and
`others.csv` of `github.com/Badestrand/russian-dictionary`): headwords without stress marks and readings with them,
English or German translations without the words of other languages listed in `STRAY_WORDS` (Uzbek "gapirish" in
говорить), and every form of the declension and conjugation tables as a form-of row.
`python scripts/panlex/panlex_yomitan.py <kaz.tsv|kir.tsv> <rus.tsv|eng.tsv> <out.zip>` converts two per-language
files of the PanLex database (the 2024-03-01 snapshot, CC0 1.0, as mirrored in the Hugging Face dataset
`cointegrated/panlex-meanings` at the revision named in the script) into a Kazakh or Kyrgyz dictionary: one
definition per headword, its translations comma-separated with the ones most sources give first. Headwords outside
the language's alphabet, time zones, words with a capital after a lowercase letter (interface strings), loose word
groups, long lists and translations in another script are left out.
`python scripts/monwn/monwn_yomitan.py <wn-data-mon.tsv> <WNdb-3.0.tar.gz> <out.zip>` converts the Mongolian Wordnet
(MonWN, CC BY-SA 4.0, at the commit named in the script) with the English lemmas of the Princeton WordNet 3.0 synsets
its words belong to into a Mongolian-English dictionary: one row per headword and part of speech, the part of speech
as its tag, one definition per synset with MonWN's Mongolian definition under the English lemmas. Both inputs are
checked against the SHA-256 in the script. Notes after a headword (бутан /хий/) move into the definition; headwords
with letters outside the alphabet or other punctuation are left out.

## Language bundles

Languages other than Japanese are looked up through Yomitan's language code, built into one script per language in
`dictionary/engine-hoshidicts/src/main/assets/yomitan-language/<iso>.js`. The bundles are committed; to rebuild them,
check out Yomitan at the commit in `scripts/yomitan-language/yomitan-commit.txt`, then
`npm --prefix scripts/yomitan-language ci` and `node scripts/yomitan-language/build.mjs <yomitan checkout> <out dir>
<iso>...`. `node scripts/yomitan-language/generate-inflections.mjs <yomitan checkout> <iso>...` regenerates the
inflection rule names (`res/values/inflections_<iso>.xml` and `YomitanInflections.kt`) and lists the translations
that are missing or no longer needed; translations are edited by hand. The build fails when `own-transforms.js` or
`own-processors.js` leaves code in a bundle that imports nothing from it, as esbuild keeps top-level calls: a
descriptor built by calls is wrapped in `/* @__PURE__ */ (() => ({...}))()`, so other languages' bundles leave it
out. `glue.js` may replace a language's case
processors where its letter pairs differ from JavaScript's default (Turkish İ/i and I/ı). A language Yomitan has no
descriptor for is built from a related one's (`borrowed` in `build.mjs`: Malay from Indonesian, Kazakh and Kyrgyz from Mongolian, which gives only Cyrillic capitalization, Swahili from Czech, Azerbaijani from Turkish, with its case pairs, Uzbek from Turkish, without them, Macedonian from Bulgarian, Marathi from Hindi, Tamil and Telugu from Kannada, which has no processors or rules); `addedPreprocessors`
gives a language text processors its descriptor lacks (Georgian decapitalizing, for headings in Mtavruli capitals; the app's own ones in `own-processors.js`, such as Thai sara am, tone mark order and the spaces around ๆ, Khmer zero-width spaces and subscript TA read as DA, Irish dotted consonants read as consonant + h, the Scottish Gaelic acute read as the grave, the Uzbek apostrophes written as ʻ and ʼ, Uzbek Cyrillic read in Latin letters, the Bulgarian and Macedonian grave stress taken off (кнѝга), a standalone Bulgarian й read as the pronoun ѝ, the Macedonian Latin è read as ѐ, and Marathi text composed as the headwords are, with an old font's य़ read as ्य, the nukta dropped and अॅ, ऍ and ँ written as ॲ and ॅं, Tamil text composed, with the vowel sign ௌ and ெள read as each other and ஸ்ரீ as ஶ்ரீ, and Telugu and Kannada text composed, with zero-width characters and soft hyphens inside a word dropped) and
`addedTransforms` the app's own rules (`own-transforms.js`: Bengali noun endings, Marathi case endings, postpositions and particles over the oblique stems and the participle over verb stems, Tamil case endings, the plural and particles over the oblique stems, and the consonant doubled before the next word, which a rule marked `hidden` takes off and the shown chain leaves out, Telugu case endings, postpositions, the plural and particles over the direct and oblique stems, Kannada case endings, the plural and particles over the increments a stem takes before them, Persian endings and verb prefixes, Hebrew prefixes, Kazakh, Kyrgyz, Azerbaijani and Uzbek noun and verb endings, built from tables of endings by `turkicTransforms`; Mongolian noun and verb endings in every vowel-harmony variant; the Turkic rule names are shared by these five languages and generated once into `inflections_turkic.xml`; the Bulgarian article, plural, adjective and verb endings; Swahili verb prefixes, Welsh and Scottish Gaelic initial mutations, Finnish clitics and possessive suffixes, Hungarian verb prefixes, the Estonian negative forms of olema); `extendedTransforms` adds the app's rules to the ones Yomitan has (the Latin enclitics, the Albanian endings). `replacedTransforms` puts the app's rules in place of Yomitan's (Irish `irishMutations` for `irishTransforms`, which undoes noun eclipsis only): the descriptor's `replaces` names Yomitan's file and the git blob it was reviewed against, and the build fails when that file changes, so the replacement is reviewed again; Yomitan's conditions are kept. A transform marked `fallback` (the Albanian endings) gives its texts a rank, which `glue.js` passes to the engine (`fallbackRules`): the engine looks them up only when no other text found an entry, one rank at a time. The generator takes a language's own rules when Yomitan has none and writes their names as labels (Persian `-ها` is shown as "pl."); the string names come from the affix in Latin letters (`inflection_fa_ha`), or from the rule's name where it is in words (Swahili `past`, `inflection_sw_past`). A descriptor's `unmutated` lists
the lowercase words whose rule candidates the glue drops (Welsh mae, ar, dŵr; Irish chomh, cheana; Scottish Gaelic chan, bhathar); `node
scripts/yomitan-language/mutation-exceptions.mjs <iso> <corpus> <top N> <english zip> [<other zip>...]` prints the
candidates for the hand review: the commonest words of a corpus (a Leipzig sentences file or plain text, kept in
`testdata/corpus/<iso>/`; Scottish Gaelic, which Leipzig has no corpus of, used the article text of a Gaelic Wikipedia dump) that the rules turn into another headword, grouped by whether the English dictionary gives
that headword as the word's lemma, with the words most often before each, and the share of tokens the list spares
an extra entry. A text processor marked `ocrOnly` (`own-processors.js`: Welsh ý and û read as ŷ and ŵ, Hungarian ö, ü and ú as ő and ű, Estonian ö as õ, Azerbaijani e and i as ə and ı, Thai sara am in two parts) runs only for
text from OCR, which the engine passes on (`fromOcr`). The glue and
`generate-inflections.mjs` take a language's rules by its descriptor and folder, whatever code the rules name
(Georgian's say `kat`), and the generator gives English labels to rule names Yomitan leaves as identifiers (`labels`). The bundles and the strings are GPL-3.0, like the engine module that holds
them.

## Tests

- `./gradlew testDebugUnitTest`: JVM unit tests of every module.
- `npm --prefix scripts/page-tests ci && npm --prefix scripts/page-tests test`: the popup page scripts (`popup.js`, `anki.js`, `note.js`, `definition.js`, `reading.js`) in a simulated DOM.
- `node --test scripts/catalog/generate.test.mjs`: the catalog generator, against a recorded listing.
- `node --test scripts/yomitan-language/glue.test.mjs`: the committed language bundles, called as the engine calls them
  (Turkish case pairs).
- `node --test scripts/yomitan-language/mutation-exceptions.test.mjs`: the mutation exception candidates.
- `python -m unittest discover scripts/frequency`: the frequency dictionary builder (wordfreq itself is not needed).
- `python -m unittest discover scripts/flags`: the flag converter.
- `python -m unittest discover scripts/ocr-models`: the catalog entries of the recognition models.
- `python -m unittest discover scripts/freedict`, `python -m unittest discover scripts/openrussian`,
  `python -m unittest discover scripts/panlex` and `python -m unittest discover scripts/monwn`: the dictionary
  converters.
- `./gradlew connectedDebugAndroidTest`: instrumented tests on a device or emulator. The AnkiDroid test runs only where AnkiDroid is installed and adds notes to a "Screenlate Test" deck, so use a device whose AnkiDroid is not synced with a real account.

## Project layout

| Module | Purpose |
|---|---|
| `app` | Application, Compose screens |
| `overlay` | Accessibility service, bubble, popup window |
| `core:common` | Shared models and settings, per-language behavior |
| `core:ocr` | Cloud and on-device OCR, hit testing |
| `core:photos` | Stored photos with their recognized text, the bubble's lookup source |
| `core:anki` | AnkiDroid integration, note templates, audio sources |
| `core:translate` | Sentence translation through free online services |
| `dictionary:api` | Dictionary engine interface, dictionary registry |
| `dictionary:engine-hoshidicts` | Engine based on hoshidicts, Yomitan's language code in QuickJS (GPL-3.0) |
| `dictionary:render-yomitan` | Yomitan-style entry renderer (GPL-3.0) |

GPL-licensed third-party code is kept in the last two modules so it can be replaced without touching the rest of the app. Every third-party addition gets an entry in [NOTICE](../NOTICE).

Compose sees classes of modules built without it, and read-only collections, as unstable and recomposes whatever shows them on every update. `compose-stability.conf` declares such immutable types stable, so they are compared by value; add a model there when screens pass it to composables.

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

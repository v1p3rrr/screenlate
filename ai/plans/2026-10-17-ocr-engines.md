# On-device OCR engines (plan of 2026-10-17)

Part of the languages work (`2026-10-10-languages-implementation.md`, requests 60-63). That plan's decision rows bind
here; the ones this plan builds on are listed below by name, and changes to them are recorded there.

## Goal

Every language reads on the device with the model that suits it best, chosen per language:

- ML Kit, bundled: Japanese, Chinese and Korean models (as now) and the Latin model for Latin-script languages.
- Tesseract (Tesseract4Android 4.9.0, runtime in the APK): one traineddata download per language, in a fast and an
  accurate variant (tessdata_fast, tessdata_best), straight from the tesseract-ocr repositories at a pinned commit.
- PaddleOCR PP-OCRv5 mobile on MNN (runtime in the APK): a detector downloaded once and one recognizer per script
  (main for CJK, korean, latin, eslav, cyrillic, el, th, arabic, devanagari), converted from the official ONNX files
  and downloaded from a release of our repository.

APK growth per arm64 APK: Tesseract 8.3 MB, MNN 2.1 MB (universal APK about 29 MB in all).

## Decisions this plan builds on

Rows of the languages plan: "On-device OCR engines", "ML Kit Latin model", "Tesseract models", "Chinese
recognition", "OCR engine choice" (the recommendation rule), "OCR model choice placement", "OCR model names",
"Several OCR models at setup", "Installed shared OCR models", "Deleting OCR models", "Paddle model hosting", "Last
dictionary with definitions", "OCR runtime and models", "Turning on a language", "PP-OCRv6", "Latin look-alikes in
Cyrillic text", "Default OCR engines", "ML Kit at setup", "OCR speed marks".

Technical choices of the agent (the owner may object; recorded in the changelog when changed):

| Topic | Choice |
|---|---|
| Branch | `languages/ocr-engines` from `languages/answers`; merged with group 3 into `languages/main`. |
| PP-OCR pipeline | The detection, recognition and pipeline code with its unit tests from `languages/ocr-compare` (`core/ocr/.../paddle`), brought over as is. |
| MNN build | A git submodule (MNN at the tag the comparison used, 3.6.1) built from source by CMake through Gradle, CPU backend only, as a static library linked into one small JNI library, in its own module `core:ocr-mnn` so that only that module's native build is slow. Like the hoshidicts submodule. |
| Tesseract artifact | `cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0`, as in the comparison. |
| Model layout | A catalog model entry may name the file a plain download is stored as (`file`, e.g. `tessdata/rus.traineddata`, the layout Tesseract reads); Paddle models are zip archives with `model.mnn`, `dict.txt` (the recognizer's characters), `LICENSE` and `NOTICE` (converted by us). |
| Paddle detector | A model entry of its own, named by each Paddle recognizer entry (`requires`); never listed to the user; installed with the first Paddle recognizer, deleted with the last one. |
| Model ids | `tesseract-fast-<code>`, `tesseract-best-<code>` (Tesseract's language code, e.g. `rus`), `paddle-<script>`, `paddle-detector`; versions pinned (tessdata by commit, Paddle by our release number). |
| Which models a language has | The catalog: a language's model entries (its source language) name the models it may use, with the recommended one marked. `core:ocr` cannot read the catalog, so it gets them through an interface (`LanguageModels`) the app implements. |
| Choice per language | Stored in the preferences per language (the model id, or `mlkit`); absent means ML Kit where it reads the script, else the recommended model when installed, else the best installed one. The main model ticked at setup is stored as the choice (owner, 2026-10-18, question 40; before, an installed recommended model came first). |
| Choice key | `ocr_model` (another language adds its code); reset with the bubble settings, where the choice is made, and backed up with them, per language. |
| Device selection | `DeviceOcr` uses the language's chosen model when it is installed, else falls back as above; no installed model and no ML Kit model means no on-device recognition, as today (cloud only on Both, an error with a hint on Device). |
| Right to left | Paddle's Arabic recognizer reads its crop left to right; the engine turns words and letters into reading order (as the comparison does). Mirrored hit testing and highlight stay in group 4d. |

## Work

1. Branch; bring over the PP-OCR pipeline; ML Kit's Latin model for Latin-script languages (one more dependency,
   `MlKitOcrEngine` picks the recognizer by the language's script); unit tests.
2. Tesseract engine (`ModelOcrEngine` "tesseract"): init per language and model, automatic page segmentation, light
   text on dark inverted first, words, lines and paragraphs from the result iterator, symbol boxes kept only where
   there is one per character inside its word. The store learns entry file names. Catalog entries for the
   languages where Tesseract is usable, fast and best, with sizes and SHA-256 from the pinned commit (a script
   writes them, as for wordfreq). Testable end to end on the emulator and the phone, as nothing needs our hosting.
3. Choice per language: the preference, `LanguageModels` from the catalog, `DeviceOcr` selection; the Recognition
   section of the bubble settings lists each turned-on language with its model and opens the language's list
   (choose, download with size, delete by the rules, recommended marked, ⓘ per model); the download screen lists the
   language's usable models with the recommended one ticked, a "main" switch on the ticked ones and installed shared
   ones locked. Readiness (`LanguageInstallation`) counts any installed model of the language.
4. Paddle engine (`ModelOcrEngine` "paddle") on MNN: `core:ocr-mnn` with the submodule and the JNI wrapper; models
   loaded on first use and freed on `release`; `scripts/ocr-models/` converts the pinned ONNX files from PaddlePaddle's
   Hugging Face repositories (MNN's converter, the character list from `inference.yml`), adds the license and the
   conversion note, zips each model and prints the catalog fields. On the emulator the archives come from a local
   URL; the release is uploaded only after the owner approves the file list. PP-OCRv6 small replaces v5's main and
   Latin recognizers if the comparison says so. Latin look-alikes in Cyrillic words become Cyrillic (for every
   engine, in the language's support class).
5. Recommendations: the rule over the clean and hard screens fills the catalog's recommended marks and the
   unusable list; ⓘ texts from the measurements and `notes/ocr-engine-research.md`.
6. Missing parts (request 62, below), docs (`architecture.md`, `usage.md`, `development.md`: MNN build, model
   conversion and release), NOTICE (Tesseract, Leptonica and the image libraries Tesseract4Android bundles,
   Tesseract4Android, tessdata, MNN, PP-OCRv5 models), strings in all 14 locales, test-model cases.
7. Emulator checks of every case; a timing run on the phone when the owner connects it; then merge.

## Missing parts (request 62)

| Missing | Today | With this plan |
|---|---|---|
| A language's dictionaries with definitions | The last one of a turned-on language cannot be turned off or deleted (a hint says why). Files gone or not yet downloaded: home card, the popup says to add one, search offers the Dictionaries screen | Kept (row "Last dictionary with definitions") |
| The last language | Cannot be turned off | Kept |
| The sort dictionary | The first other frequency dictionary sorts | Kept |
| An on-device model for the language | Engines Both: cloud alone, no draft, nothing offline; Engines Device: the scan fails with a hint; home lists the missing model | The active model cannot be deleted, only replaced; the scan error on Device gets a button to the language's models |
| A model another language uses | (none) | Deleted only while no language uses it as active, with a dialog naming the languages that lose it |
| Model files gone or damaged | (none) | Treated as not installed; home card with "Download again", as for dictionaries |
| A newer model version in the catalog | The language counts as missing its model | The installed version keeps working; the Dictionaries screen's "Check for updates" lists the model with the dictionaries (question 35) |
| Network or the cloud | Messages, the device result is kept | Kept |
| A model deleted during a scan | (none) | Deleted once the scan has released it |

## Comparison results for step 4 and 5

From `languages/ocr-compare` (commits d27009b and 07f4213), character error rate on clean and hard screens:

- Lowest error: ML Kit for ja, zh, ko, es, hi; ML Kit Latin for en, de, fr, it, pt, pl, tr, lv, vi; Paddle v5 for
  ru, kk, el, ar, fa, th; tessdata_best for he, bn, hy, ka. By the recommendation rule (tilted screens count) ru
  stays on tessdata_fast.
- Paddle reads tilted lines at 66-98% error: its line crops are not rotated. The comparison session adds rotated
  crops to `core/ocr/.../paddle` in its own commit, which this branch takes over; step 4 does not touch that package
  until then.
- PP-OCRv6 small against v5: hard screens CJK 15.7 vs 18.8%, Latin 21.1 vs 24.2%, vertical Japanese 0.8 vs 37.0%;
  larger (detector 9.8 MB, recognizer 21.1 MB), slower on Latin, no Vietnamese stacked diacritics. The v5/v6 choice
  is put to the owner after the re-measurement with rotated crops.

## Progress (2026-10-17, end of session)

Branch `languages/ocr-engines`, stacked on the whole group 1-3 stack (`languages/main` … `languages/ru`), pushed.

- Steps 1-4 done. Paddle runs on MNN 3.6.1 (`core:ocr-mnn`, submodule, CPU only); the engine takes a detector's
  thresholds from `detector.properties` in its archive (v6: 0.2 / 0.45, unclip 1.5 as measured; v5 defaults) and
  puts right-to-left recognizers' lines into reading order. Latin look-alikes in Cyrillic words become Cyrillic for
  every engine (`LanguageSupport.ocrWord`).
- Models: six archives built by `scripts/ocr-models/paddle_models.py` from the pinned ONNX files (the converted files
  the comparison measured), uploaded with the owner's approval to the GitHub prerelease `ocr-models-1` (not latest;
  the latest release stays v0.2.3). Offered per language: v6 for ja, zh and the Latin languages, v5 Latin for the
  Latin languages, v5 Cyrillic for ru, v5 Korean for ko; vi has none.
- Step 5 done for the 14 languages: recommended marks by the rule (v6 ja, zh, de, fr, it, pl, lv; v5 Latin en, es,
  pt, tr; Cyrillic ru; ko and vi keep ML Kit; Tesseract recommends none of them). The ⓘ of a catalog model is its
  template description (written in all 14 locales).
- Fixed on the way: the language download screen listed the Paddle detector as its own item and left it out of the
  download; now it comes with the ticked model and is counted in its size (`setupDownloads`, LanguageSetupTest). The
  catalog test no longer expects an installed title from model entries. CI: Gradle wrapper validation in its own
  step that allows the wrapper JAR of a sample inside the MNN submodule.
- Checked on the emulator (Dev build): Russian turned on → PaddleOCR v5 ticked as main, 12 MB with the detector,
  downloaded and unpacked; a scan's draft came from Paddle (1.8 s) and the lookup ran on it. Japanese: PaddleOCR v6
  downloaded from the model dialog (26 MB with its detector, `detector.properties` unpacked); offline scan: draft
  1.4 s, whole screen 21 paragraphs in 6.1 s on the x86 emulator. With no explicit choice, French, German and
  Italian switched to v6 as soon as it was installed (decision "Choice per language").

Left:

- Step 6: `docs/usage.md` and `docs/architecture.md` for Paddle (development.md and NOTICE are done); check each row
  of "Missing parts" on the emulator; test-model cases OCR18-OCR21 added, others for request 62 still to write.
- Step 7: emulator checks of every case (the group 3 checks of the languages plan included), a timing run on the
  phone (the v6 whole screen took 6.1 s on the emulator: compare with ML Kit's 1.6 s rule on the phone), then
  fast-forward `languages/main` through the stack and this branch.
- The code review of all modules after the owner's build (languages plan, row "Build of groups 1-3").

## Progress (2026-10-18)

- Missing parts built: the device-only scan message opens the language's models, and the turned-off languages'
  block counts their models (dee772e); scans hold their models, a held model's delete is deferred, damaged model
  files are found by size at start and by hash after an engine error, with a home card, and "Check for updates" lists
  model updates (321d79f); every row of "Missing parts" now has its code.
- Questions 38 and 40 built: ML Kit first at setup for the languages it reads, the other models unticked with
  "More accurate" on the recommended one, and the choice rule ML Kit, then the recommended model, then the best
  installed one (1fd9a00).
- Docs: `usage.md` and `architecture.md` describe ML Kit at setup, the choice rule, held and damaged models and model
  updates. Test-model cases OCR22-OCR25 added (N).
- Left: the timing run (with the test session), then the row "Default OCR engines", the speed marks in words and the
  Both-mode hint (row "OCR speed marks"); the emulator check of OCR22-OCR25; step 7.

## Questions

35. Answered on 2026-10-17: (a). Model updates: when the catalog names a newer version of an installed model (a new tessdata commit, a new
    PP-OCR release). Options: (a, recommended) listed by the Dictionaries screen's "Check for updates" with the
    dictionaries, the old version working until the new one is installed; (b) downloaded by itself on Wi-Fi; (c)
    not offered: a model keeps its version until it is deleted and downloaded again.

Request 68 of the languages plan (2026-10-18): on the phone the downloaded models seem about as slow as cloud
recognition and ML Kit clearly faster. The comparison measured per clean screen on the emulator: cloud ~0.8 s,
ML Kit Latin ~1.0 s, Paddle v5 ~0.9 s, Paddle Japanese 1.4 s, ML Kit Japanese 1.6 s; a whole screen of 21 paragraphs
took Paddle v6 6.1 s. The in-app timing run (emulator, then the phone) is with the test session.

36. Default engines for new installs. Today Both: the device gives a draft, the cloud the final text, the device's
    whole screen becomes final offline. Options: (a, recommended) decided by the in-app timing run: Cloud by default
    when the device's draft comes less than 0.5 s before the cloud result on the phone, else Both stays; with Cloud
    by default, turning a language on downloads no on-device model; (b) Cloud by default now; (c) Both stays.
37. Where the other engine settings are offered (Both for a draft and offline scans, the device alone for no
    cloud). Options: (a, recommended) the first run and the language download screen show the engine choice with
    one line per option, and the on-device model category says it is needed for Both and the device alone; (b) only
    the bubble settings, with a hint; (c) the first run only.
38. ML Kit as the built-in model. ML Kit reads Japanese, Chinese, Korean and the Latin-script languages without a
    download, but the download screen ticks the recommended Paddle model and says nothing about ML Kit. Options:
    (a, recommended) the on-device category lists ML Kit first as built in, needing no download, and chosen by
    default; the other models are offered unticked, marked "more accurate" where the comparison shows it; a model is
    ticked by default only for languages ML Kit does not read (Russian); (b) the recommended model stays ticked, with
    a line that ML Kit is built in and the tick can be removed; (c) as now.
39. Speed in the model choice. Options: (a, recommended) the model list marks each model "faster" or "more
    accurate" from the timing run, and with Engines on Both a hint says that the device model only gives the draft,
    so a faster one helps more; (b) with Engines on Both the draft always uses ML Kit where it reads the language,
    and the chosen model is used for the device alone and offline; (c) no marks.
40. Which model is active when ML Kit reads the language and another one is downloaded. Options: (a, recommended) a
    model ticked at setup becomes the main one (the "main" switch can stay on ML Kit); one downloaded from the model
    list is only added; with no choice ML Kit is used; (b) always ML Kit until another is chosen in the list; (c) as
    now (an installed recommended model is used when nothing was chosen).

Answered on 2026-10-18: 36 (a), row "Default OCR engines"; 37 postponed by the owner: the engine choice becomes a
separate setting, discussed later (languages plan, "Open items"); 38 (a), row "ML Kit at setup"; 39: speed only in
words, no figures for the user, row "OCR speed marks"; 40 (a), rows "ML Kit at setup" and "Choice per language".

## Changelog

- 2026-10-17: plan written from requests 60-63 and the answers to questions 31-34 of the languages plan.
- 2026-10-17: question 35 answered (model updates listed by "Check for updates" with the dictionaries).
- 2026-10-17: requests 64 and 65 of the languages plan (PP-OCRv6 small in the comparison; Latin look-alikes in
  Cyrillic words) added to step 4.
- 2026-10-17: step 3 done; comparison results recorded; rotated Paddle crops handed to the comparison session.
  Paddle stays in this plan before the owner's build of groups 1-3 (languages plan, row "Build of groups 1-3").
- 2026-10-17: step 4 done (Paddle on MNN, models on the prerelease `ocr-models-1` approved by the owner) and step 5 for
  the 14 languages; the owner's build comes from this branch's CI run. Progress section added.
- 2026-10-18: questions 36-39 from request 68 of the languages plan (default engines, where they are offered, ML Kit
  as the built-in model, speed marks); the in-app timing run is with the test session.
- 2026-10-18: questions 36-40 answered (37 postponed); row "Choice per language" changed by question 40.
- 2026-10-18: missing parts and questions 38 and 40 built; Progress (2026-10-18) added.

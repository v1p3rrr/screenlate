# More languages (phase 8)

Status: research and experiments. The owner answered the general topics (see Decisions); concrete questions follow
in the language branch once the hypotheses below are checked. Research findings: `notes/languages.md`.

## Owner requests

1. (2026-10-09) List the tasks left, then move on to support for other languages: collect what is needed, including
   how Yomitan works with other languages, and prepare an outline for the discussion: what we need to find out and
   which general topics the owner answers first; concrete questions come after.
2. (2026-10-09) First answer: can hoshidicts serve the other languages, or is it tied to Japanese? If it is, another
   engine would be needed, or a port of Yomitan's engine to Android, which the owner would rather avoid (a rewrite
   from scratch risks many bugs).
3. (2026-10-09) Check whether most languages, at least the main ones, have dictionaries we may put in the catalog
   (licenses and other conditions). Dictionaries without a license or with a bad one go on a separate list for a
   decision; the owner may accept the risk while the app is not on Google Play.
4. (2026-10-09) Find out the size of the on-device recognition models; if small, bundle them, else offer them as
   optional downloads like catalog dictionaries, if that is possible at all. Look for on-device recognition for the
   languages ML Kit does not read before settling for cloud and app text only.
5. (2026-10-09) Owner's question: Chinese in simplified and traditional characters, separate profiles or one?
6. (2026-10-09) Propose how global settings and per-language settings combine, page by page.
7. (2026-10-09) Work order: research deeper (search, tests, the emulator), try one of English or Chinese (whichever is
   easier) in an experimental branch to check the hypotheses, then ask concrete questions in the language branch,
   agree on the implementation plan, and build it from scratch there (the experiments may be thrown away, at most
   their pieces reused).
8. (2026-10-09) Owner's questions: is hoshidicts only for Japanese or for every language like Yomitan, and would the
   fix be a library in another language built in? Answered: storage, query and sorting are language-agnostic; only
   text processing and deinflection are Japanese. The fix tried in the experiment is Yomitan's own language code
   run in QuickJS (a small C JavaScript engine, MIT, compiled into the same native library, about +1.3 MB per ABI)
   plus native form-of following; alternatives are `androidx.javascriptengine` or a Kotlin port. The choice is a
   concrete question for later.
9. (2026-10-09) Also experiment with a language that has no deinflection rules in Yomitan and gets its forms only
   from the dictionary's form-of entries, e.g. Russian.
10. (2026-10-09) Dictionaries: every language needs at least a dictionary into English, preferably also into
    Russian, optionally into the other main interface languages.

## Decisions

| Topic | Decision |
|---|---|
| Languages and order | English, Chinese, Korean first (English and Chinese before Korean); then the European languages, Russian, Ukrainian and others; in the end every language Yomitan supports (owner, 2026-10-09) |
| Audience | Every user, not only the owner: the app goes to a community of learners of Japanese, where others learn other languages, mostly English and Chinese; later anyone with their own target language (owner, 2026-10-09) |
| Depth | Full support (deinflection, on-device recognition, fonts, audio, Anki), if it is feasible without writing our own Yomitan from scratch (owner, 2026-10-09) |
| Language profiles | Separate language profiles, switched on the home screen; dictionaries and the rest are set up within the profile (owner, 2026-10-09) |
| Settings | Global settings with per-language overrides, decided per settings page where the language matters (CSS and fonts yes, bubble size probably not); the agent proposes the design (owner, 2026-10-09) |
| Required dictionaries | A dictionary a language cannot work without (e.g. its inflection data) is offered for download when the profile is turned on, or imported from a file by the user; without it the language is not usable. Bundling only if small (owner, 2026-10-09) |
| Catalog | Dictionaries for every language with a license that allows it; those without a license or with a bad one listed separately for a decision (owner, 2026-10-09) |
| Phrases | As Yomitan does, if the engine allows it (owner, 2026-10-09) |
| On-device recognition | Bundle the models if small, else optional downloads if possible; cloud only is not wanted; look for models for the languages ML Kit lacks; right-to-left scripts are supported for their languages (owner, 2026-10-09) |
| Popup | The fields change with the language (preliminary, owner, 2026-10-09) |
| Anki | A separate deck per language without asking; each language's Anki settings start empty, with a first guess by some signs where possible, then the user sets them (owner, 2026-10-09) |
| Search and "Look up in Screenlate" | Use the active language profile (owner, 2026-10-09) |
| Branches | `main`; a branch for the multi-language version; branches per language (or per batch) off it; a separate experimental branch for the hypotheses, which may be thrown away (owner, 2026-10-09) |
| First language | English or Chinese, whichever is easier, as the experiment (owner, 2026-10-09) |
| Gloss languages | Every language: at least a dictionary into English, preferably into Russian, optionally into the other main interface languages (owner, 2026-10-09) |

## General topics for the owner

1. Languages and order: which languages first (English, Chinese, Korean, European languages, Russian or Ukrainian
   for people learning them), and whether "any language with a Yomitan dictionary" (generic support without rules
   of its own) is part of the first round.
2. Who it is for: the owner's own study languages, or users in general (affects the catalog and the defaults).
3. Depth per language: full (deinflection, on-device recognition, fonts, audio, Anki presets) or basic (dictionary
   lookup with case and diacritics handling, form-of entries).
4. Choosing the language of the text: one active language switched by hand (as Yomitan profiles), several at once
   chosen by the script under the aim, a language per app, or a mix. Ambiguous scripts: Han (Japanese or Chinese),
   Latin (English, German, …), Cyrillic (Russian, Ukrainian). English words inside Japanese text.
5. Settings per language (Anki deck and note type, audio sources, font, translation, scan length) or shared.
6. Words and phrases in languages with spaces: the whole word under the aim, multi-word entries first ("look up
   to", "New York"), case and accents ignored.
7. Dictionaries: what the catalog offers per language (Wiktionary main, glossary and IPA, CC-CEDICT, KRDICT, others
   with checked licenses), whether anything is bundled in the APK, character dictionaries (Hanzi, Hanja).
8. On-device recognition: bundle the Chinese and Korean models (APK +~4 MB per script and ABI), download them
   through Google Play services, or cloud only; languages without an on-device model (Russian, Ukrainian, Arabic,
   Hebrew, Thai); right-to-left scripts in or out.
9. Popup per language: readings (pinyin, romanization), IPA in place of pitch accents, audio sources, fonts
   ("Only for Japanese text" becomes the language's text), the dock glyph.
10. Anki per language: note types and suggested templates, Japanese-only markers, a deck per language.
11. Search screen and "Look up in Screenlate": which language they use.
12. How to proceed: a prototype with one language on the emulator first, then the rest; releases marked
    experimental or not.

Technical choices (where deinflection runs: an extended hoshidicts or a Kotlin layer over its exact query; porting
Yomitan's rules, which stay in the GPL modules) come with a recommendation after the general answers.

## To find out (agent)

Status 2026-10-09 after the English and Chinese experiment (`notes/languages.md`, "Experiment"):

- Done: wty en→en, en→ru and zh→en imported in the app and looked up in the popup; form-of followed natively.
  Import time of en→en was not recorded.
- Done: extending hoshidicts is not needed for the lookup itself: Yomitan's language code in QuickJS makes the
  candidates, hoshidicts answers exact queries without a submodule change. Optional submodule changes: form-of at
  import (the "redirects" field), per-row part-of-speech rules.
- Done: lookup cost (candidates 0.2–18 ms per call by language, queries 0.1–4 ms).
- Done: Chinese on device costs about 0 MB more in ML Kit, Korean about 0.8 MB; other engines measured.
- Open: cloud recognition on Korean screenshots, and which engine read the English and Chinese test screenshots
  (cloud or ML Kit); word boxes and `content_language`.
- Open: whole-word hit testing and highlight for Latin text beyond the experiment's word start; sentence splitting
  with abbreviations.
- In progress: licenses of catalog candidates per language, a list of risky ones.
- Done: Russian as a language without transforms (request 9): works through the dictionary's form-of entries;
  the form-of rows make wty ru→en 273 MB on disk (notes, "Russian").
- Request 5: CC-CEDICT stores every term under both headwords and wty zh→en links simplified forms to traditional
  ones, so one Chinese profile works for both scripts; only the `lang` tag and fonts (SC or TC glyph forms) differ.
  To be asked with the concrete questions.
- Test material: `testdata/ocr/en-sample.png` and `zh-sample.png`.

## Handoff (2026-10-09, end of the research session)

Everything found is in `notes/languages.md`: Yomitan's language support, hoshidicts, the experiment ("Experiment",
"Russian"), dictionaries with licenses and gloss-language coverage ("Catalog candidates and licenses"), OCR sizes.

Branch `experiment/languages` (pushed; may be thrown away, pieces reusable):
- 0732dea English and Chinese, 89f9073 Russian, acf21f1 Korean (`KoreanSupport`, letter resolution) plus
  `scripts/yomitan-language/harness/` (native lookup harness for the emulator) and `scripts/yomitan-language/tools/`
  (`ru_peek.py` rows of a dictionary, `split_formof.py` split lemma and form-of rows, `wty_pairs.py` table of wty
  sizes from the Hugging Face tree API `https://huggingface.co/api/datasets/daxida/wty-release/tree/main/latest/dict/<src>?recursive=true`).
- Bundles: `node scripts/yomitan-language/build.mjs <Yomitan checkout> <out dir> <iso>...` (after `npm ci` there),
  output goes to `dictionary/engine-hoshidicts/src/main/assets/yomitan-language/` (en, zh, ru, ko are committed).
- The overlay's language comes from the file `files/experiment-language` in the debug app (`echo ko | adb shell
  run-as com.vpr.screenlate.debug tee files/experiment-language`); a uiautomator dump reconnects the service, which
  re-reads it. Test images: `scripts/text-image.py` (Cyrillic needs `FONT=C:/Windows/Fonts/arial.ttf`),
  `scripts/debug-device.sh images` and `show <name>.png`; drag the bubble with `input motionevent DOWN/MOVE/UP`, the
  aim sits about 133 px above the bubble's center.

Emulator state (Pixel_10_Pro, started with `-no-snapshot-save`): debug app with the experiment build (Korean);
imported in the app: wty-en-ru, wty-en-en, wty-zh-en, wty-ru-en, KRDICT RU (ko→ru); `experiment-language` = `ko`.
`/sdcard/Download` has wty-en-ru, wty-zh-en, wty-ru-en, KO-RU.KRDICT.No.Examples, Frequency.CC100.Korean.
`/data/local/tmp/hd`: hoshidicts-cli `hd`, harness `ll`, bundles `y-*.js`, imported wty-en-ru, wty-ko-en,
wty-zh-en, wty-ru-en. Space on `/data` is short (under 1 GB free); delete test copies there first.

Was in progress:
- Korean in the popup: KRDICT RU imported (its index.json says target "ko", so the app recorded "ko → ko": the
  language guess trusts a wrong index); the CC100 frequency dictionary not imported yet; no popup screenshot yet.
  Next: import the frequency list, render a Korean sample (needs a Korean font for `text-image.py`, e.g.
  `C:/Windows/Fonts/malgun.ttf`), check deinflection (먹었습니다 → 먹다) and timing (9–18 ms per candidate call).

Still to do before the concrete questions (told to the owner):
1. Audio per language: whether Lingua Libre and Wiktionary find clips for en, zh, ru, ko when given the language
   (today the Japanese sources and settings are used).
2. Compact form-of storage: estimate the size of a form → (lemma, tags) table against the 231 MB of rows.
3. ML Kit Chinese and Korean recognizers: the real APK delta; Tesseract or PaddleOCR for Cyrillic.
4. Settings design "global + per language", page by page, as a draft in chat. Starting point: language-bound
   settings live in the language profile (dictionaries, Anki deck and note type, audio sources, lookup rules such as
   scan length and text replacements, popup font and CSS), the rest stays global (bubble, appearance, background
   work, translation services and target language, backup), with per-language overrides only where the owner wants
   them (e.g. text size).
5. Then the concrete questions (AskUserQuestion, Russian, recommended option first): QuickJS against
   `androidx.javascriptengine` or a Kotlin port; form-of at import (hoshidicts "redirects" field via a patch or
   fork, or our own side table); translating inflection names (en 17, ko ~450 rules); following form-of a second
   level (ёжика → ёжик → ёж); showing every rule chain; frequency dictionaries we build from wordfreq (CC BY-SA 4.0)
   for the catalog; the risky dictionaries list; one Chinese profile for both scripts (request 5); the multi-language
   version branch and per-language branches. Then the implementation plan, built from scratch on that branch.

## Changelog

- 2026-10-09: plan started with the owner's request, the research (`notes/languages.md`) and the topics above.
- 2026-10-09: owner's answers to the general topics (Decisions) and requests 2-7.
- 2026-10-09: English and Chinese experiment in `experiment/languages`; findings in the notes; requests 8 and 9.
- 2026-10-09: Russian experiment (request 9) in the same branch.
- 2026-10-09: request 10 (gloss languages) and its decision row; license survey in the notes.
- 2026-10-09: Korean started in the experiment branch; handoff section for the next session (owner's request).

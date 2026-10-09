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

- A real Wiktionary dictionary (e.g. en→en, 103 MB) in our engine: import time, lookup speed, how its form-of
  entries look in the popup today.
- What extending hoshidicts takes: a language parameter for text processors and deinflection, following form-of
  entries; a fork of the submodule or patches; upstream interest.
- Lookup cost of case and diacritics variants and phrase prefixes.
- Cloud recognition on English, Chinese and Korean screenshots: word boxes, spaces, `content_language`.
- Whole-word hit testing and highlight for Latin text; sentence splitting with abbreviations.
- Licenses of catalog candidates, English–Russian in particular.
- Test material: screenshots in the chosen languages for `testdata/ocr/`.

## Changelog

- 2026-10-09: plan started with the owner's request, the research (`notes/languages.md`) and the topics above.
- 2026-10-09: owner's answers to the general topics (Decisions) and requests 2-7.

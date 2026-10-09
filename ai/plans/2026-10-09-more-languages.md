# More languages (phase 8)

Status: discussion. Nothing is decided yet; the owner answers the general topics first, then concrete questions
follow in batches. Research findings: `notes/languages.md`.

## Owner requests

1. (2026-10-09) List the tasks left, then move on to support for other languages: collect what is needed, including
   how Yomitan works with other languages, and prepare an outline for the discussion: what we need to find out and
   which general topics the owner answers first; concrete questions come after.

## Decisions

| Topic | Decision |
|---|---|
| (none yet) | |

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

# Language-specific rules

Every rule that applies to some languages only, where it lives, the tests that show it works for its language
(positive) and the tests that show it does not fire for the others (negative). Collected 2026-10-19 on
`languages/ocr-engines` (67daf02) from the code, the plan `plans/2026-10-10-languages-implementation.md`,
`test-model.md` (LK1–LK27, AUD8–AUD12, OCR10, POP23), `yomitan-behavior.md` and `languages.md`.

When a language gets a new rule, add its row here, a positive test, and a negative case in
`LanguageRulesIsolationTest` (or next to the rule when it lives outside `core:common`).

## Paths and test names

| Short | Path |
|---|---|
| `lang/` | `core/common/src/main/java/com/vpr/screenlate/core/common/language/` |
| `api/` | `dictionary/api/src/main/java/com/vpr/screenlate/dictionary/api/` |
| `glue` | `scripts/yomitan-language/glue.js` (built into `dictionary/engine-hoshidicts/src/main/assets/yomitan-language/<iso>.js`) |
| `popup/` | `overlay/src/main/assets/popup/` |
| `render/` | `dictionary/render-yomitan/src/main/assets/yomitan-render/` |
| `overlay/` | `overlay/src/main/java/com/vpr/screenlate/overlay/` |
| `ocr/` | `core/ocr/src/main/java/com/vpr/screenlate/core/ocr/` |
| `Sentence.kt` | `core/anki/src/main/java/com/vpr/screenlate/core/anki/note/Sentence.kt` |

Tests: `Isolation` is `LanguageRulesIsolationTest` (core:common), `<Lang>Test` is `<Lang>SupportTest`, `glue` is
`scripts/yomitan-language/glue.test.mjs` (`node --test scripts/yomitan-language`), `reading.mjs`, `note.mjs`,
`popup.mjs`, `definition.mjs` are the page tests in `overlay/src/test/js`, `I:` marks an instrumented test.

Status: **ok** (works, both kinds of tests), **fixed** (a bug this review fixed, with a test), **limit** (known, left
as it is; see below), **ask** (a behavior choice for the owner).

## Text side: word starts, sentences, OCR text (`core:common`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| spaced (all but ja, zh) | The lookup starts at the start of the aimed word; `'’-` between letters and combining marks stay inside it | `lang/SpacedLanguageSupport.kt:72` | GermanTest (hyphen), FrenchTest, Isolation (apostrophe words in every spaced language) | Isolation (ja, zh start at the aim; Korean has its own rule) | ok |
| fr, it, pt | An elided word (l', qu', d') starts a new word after the apostrophe | `lang/SpacedLanguageSupport.kt:156`, `FrenchSupport.kt:24`, `ItalianSupport.kt:25`, `PortugueseSupport.kt:24` | FrenchTest, ItalianTest, PortugueseTest | Isolation (the others keep "l'eau", "d'água" whole) | ok |
| fr, it | A letter between two apostrophes (rock’n’roll) is no elision | `lang/SpacedLanguageSupport.kt:156` | FrenchTest `a letter between two apostrophes…` | Isolation (rock’n’roll whole everywhere) | fixed |
| vi | The lookup also starts one and two syllables before the aimed one | `lang/SpacedLanguageSupport.kt:93`, `VietnameseSupport.kt:26`; `api/EarlierStarts.kt:13`; `overlay/OverlayController.kt:1322` | VietnameseTest, EarlierStartsTest | VietnameseTest `other languages start only…`, Isolation | ok |
| ko | The lookup starts at the start of the spaced word (보면서 → 보다); Latin letters belong to the word, digits do not | `lang/KoreanSupport.kt:85` | KoreanTest, I: KoreanLookupTest | Isolation (ja, zh start at the aim) | ok |
| ja, zh, ko | Latin words inside CJK text start at their first letter; a lone Latin word counts only as a whole | `lang/CjkText.kt:32`, `:54` | JapaneseTest, ChineseTest, KoreanTest | Isolation (`Ｔシャツ and wa|lk`) | ok |
| de, pl, tr, lv | A period after a short number (or a longer one before a lowercase word) is an ordinal, not a sentence end | `lang/SpacedLanguageSupport.kt:135` | GermanTest, PolishTest, TurkishTest, LatvianTest, SentenceTest | Isolation (others end at "3." and "1918."; "1918. Dann" ends everywhere) | ok |
| each spaced language | Its abbreviations ("Mr.", "z. B.", "т. е.", "TP.") do not end a sentence | `lang/SpacedLanguageSupport.kt:56`, each `<Lang>Support.abbreviations` | each `<Lang>Test`, SentenceTest | Isolation (each list holds only its own language's sentences) | ok |
| tr | Abbreviations in capitals match under Turkish case rules (ŞTİ.), and under the root rules for capitals read without their dots | `lang/SpacedLanguageSupport.kt:42`, `TurkishSupport.kt:31` | TurkishTest `abbreviations in capitals…` | the other languages keep `Locale.ROOT` | fixed |
| spaced, ko | A period followed at once by a letter or digit (3.14, p.m, example.com) does not end a sentence | `lang/SpacedLanguageSupport.kt:56`, `KoreanSupport.kt:67` | EnglishTest, KoreanTest, SentenceTest | Isolation (holds in every spaced language) | ok |
| ja, zh | A full-width period between digits (３．１４) is a decimal point | `lang/CjkText.kt:44` | JapaneseTest, ChineseTest | JapaneseTest ("第３．次に", "終わり．次" still end) | fixed |
| each language | Quote pairs: a terminator inside quotes opened before the word does not end the sentence | each `quotePairs`; `Sentence.kt:18` | SentenceTest (ja, en, es, fr, de, it, pt, pl, tr, lv, vi, ru), RussianTest, GermanTest | SentenceTest (balanced quotes unchanged) | ok |
| each language | A quote or bracket the paragraph does not close (“ja” in German text, a list's "1)") no longer takes in the whole paragraph: the nearest sentence end counts | `Sentence.kt:18`, `:71` | SentenceTest `aQuoteTheParagraphDoesNotClose…` (de, en, pl) | the same test (a closed quote still holds its terminators; Japanese 「」) | fixed |
| ja | Respellings: 1人 → 一人, 々, ッ, dots and spaces, radical look-alikes | `lang/JapaneseSupport.kt:126`; `api/LookupVariants.kt:12` | JapaneseTest, LookupVariantsTest, I: LookupPipelineTest | Isolation (no other language respells) | ok |
| ja | Romaji to kana when the setting is on (Mozc table) | `lang/JapaneseSupport.kt:77`, `MozcRomaji.kt` | JapaneseTest, LookupVariantsTest | Isolation (`fromLatin`, `convertsLatin` only ja) | ok |
| ja, zh, ko | Character entries of the kanji/hanzi/hanja after the first (single kanji below the results) | `JapaneseSupport.kt:139`, `ChineseSupport.kt:98`, `KoreanSupport.kt:103` | JapaneseTest, ChineseTest, KoreanTest | spaced languages return none (`SpacedLanguageSupport.kt:118`) | ok |
| ja, zh, ko | Scripts without spaces are shortened by letters (`SearchResolution.LETTER`); ja and zh join words with no separator | each `searchResolution`, `wordSeparator` | Isolation | Isolation | ok |
| ru (Cyrillic script) | OCR Latin look-alikes in a Cyrillic word become Cyrillic | `lang/LanguageSupport.kt:27`, `CyrillicLookAlikes.kt` | CyrillicLookAlikesTest | CyrillicLookAlikesTest `only Cyrillic-script languages…`, Isolation | ok |
| en, fr, de, es, pt | Audio and transcription regions (us/uk, fr/ca…, de/at/ch, es/mx…, br/pt) | each `audioRegions`, `transcriptionRegionNames`; `lang/AudioRegion.kt:12`, `TranscriptionRegions.kt:11` | TranscriptionRegionsTest, TranscriptionsTest, each `<Lang>Test` | languages without regions (it, pl, tr, lv, ru, vi, zh, ko): `<Lang>Test` | ok |

## Lookup pipeline (`dictionary:api`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| de | A compound without an entry is split into the longest words, a linking s/es/n skipped | `api/Compounds.kt:12`, `api/DictionaryLookup.kt:92`, `GermanSupport.kt:21` | CompoundsTest | Isolation (`compoundLinks` only de) | ok |
| de | A compound part's capitalized headwords (nouns) come first | `api/Compounds.kt:30`, `api/DictionaryLookup.kt:77` | CompoundsTest | Isolation (`capitalizedNouns` only de) | ok |
| vi | A word reached only through a "classifier" form-of entry is left out | `api/ShownResults.kt:48`, `VietnameseSupport.kt:29` | ShownResultsTest | ShownResultsTest (every other language keeps it), Isolation | ok |
| zh | A word found through its Simplified/Traditional spelling shows the spelling read | `api/ShownResults.kt:14`, `ChineseSupport.kt:121` | ShownResultsTest | ShownResultsTest `a spelling tag of another language's dictionary…`, Isolation | ok |
| zh | Numbered pinyin in parentheses is dropped from readings; readings spaced differently are one word | `ChineseSupport.kt:124`, `:128`; `api/ShownResults.kt:59` | ChineseTest, ShownResultsTest | ShownResultsTest `readings spaced differently stay apart outside Chinese`, Isolation | ok |
| zh | Fixed script: a word gives way to its spelling in the chosen script; Anki script markers | `api/ShownResults.kt:73`, `:107`; `ChineseScripts.kt` | ShownResultsTest, ChineseScriptsTest, ReadingWordsTest | Isolation (`scriptOf`, `inScript` only zh); `scriptTag` is null for English | ok |
| zh | Search by typed pinyin (reading index) | `ChineseSupport.kt:133`, `:135`; `api/DictionaryLookup.kt:124` | ChinesePinyinTest, ReadingWordsTest | Isolation (`readingSearch`, `readingKey` only zh) | ok |
| spaced | A match that ends before a combining mark is left out (ко in ко́шка, hoa in hoà typed apart) | `api/ShownResults.kt:31`, `api/DictionaryLookup.kt:89` | ShownResultsTest `a match that ends before a stress or tone mark…` | the same test (Japanese か + dakuten keeps its match; a match up to a space stays) | fixed |

## Engine (`dictionary:engine-hoshidicts`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| ja | Native hoshidicts lookup with its deinflector; other languages through their Yomitan bundle in QuickJS | `HoshidictsEngine.kt:101` | I: HoshidictsEngineTest, LookupPipelineTest, LanguageLookupTest | — (one path per language) | ok |
| all but ja | Form-of entries are followed, a second form-of step allowed (шокирующее → шокировать) | `cpp/language/candidate_lookup.cpp:39` | I: LanguageLookupTest, HoshidictsEngineTest | language-agnostic by design | ok |
| tr | Turkish case pairs (I ↔ ı, İ ↔ i) | `glue:44` | glue `turkish capitals…` | glue `other languages keep the default case pairs`, `turkish case pairs do not reach english` | ok |
| en | Untyped phrasal verbs ("picked up" → "pick up") for dictionaries without parts of speech | `glue:86`, `:113` | glue `english phrasal verbs…`, I: LanguageLookupTest | glue ("closed to" gets none; de, fr, es never) | ok |
| ru | Stress marks dropped, ё ↔ е (Yomitan's Russian processors) | `ru.js` bundle | glue `russian stress marks…`, I: LanguageLookupTest | glue (en, de, pl, lv do neither) | ok |
| vi | Tone marks in the old and the new placement (hoà ↔ hòa) | `vi.js` bundle | glue `vietnamese tone marks…` | glue (fr, es, pt, it do not) | ok |
| zh | Kangxi radicals read as their ideographs (⽇本 → 日本) | `zh.js` bundle | glue, I: ChineseLookupTest | — | ok |

## Page scripts (popup and Anki notes)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| ru | A reading that only adds stress replaces the headword (ко́шка); another reading goes over it | `popup/reading-ru.js:5` | reading.mjs, popup.mjs | reading.mjs `russian and chinese rules stay with their languages` (uk, be, bg, en, de) | ok |
| zh | Pinyin split into syllables, one over each character, with tone colors | `popup/reading-zh.js:5`, `popup/popup.js:383`, `:735` | reading.mjs, popup.mjs | reading.mjs (ja, vi, ko get no syllables) | ok |
| ja | Definition copy rules of Japanese dictionaries | `popup/definition-ja.js:5` | definition.mjs | definition.mjs `a language without rules of its own…` | ok |
| ja (default) | Furigana by Yomitan's Japanese distribution for every language without reading rules | `popup/note.js:84`, `render/render.js:570` | note.mjs, anki.test.mjs | — | limit |
| ru | `{furigana}`, `{furigana-plain}`, `{sentence-furigana}` put the stressed reading over the word | `popup/note.js:84`, `:122` | note.mjs | — | ask |
| ja | `{cloze-body-kana}`, pitch-accent markers offered only for Japanese | `JapaneseSupport.kt:19`, `popup/note.js:320`, `:354` | FieldTemplateTest, note.mjs | `SpacedLanguageSupport.kt:21` (no markers) | ok |
| zh | `{expression-simplified}`, `{expression-traditional}` | `ChineseSupport.kt:24` | FieldTemplateTest, ShownResultsTest | other languages have no script markers | ok |
| spaced | `{sentence-furigana}`: unknown text is passed over a word at a time, so no match starts inside a word | `overlay/anki/SentenceParser.kt:54` | SentenceParserTest `in a language looked up by words…` | the same test (ja, ko still step by character) | fixed |

## Recognition and app text (`core:ocr`, overlay)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| each OCR script | ML Kit model by script; Cyrillic has none | `ocr/mlkit/MlKitOcrEngine.kt:53` | — | — | limit (no test of the mapping) |
| each language | OCR words are joined by the language's separator (empty for ja, zh) | `ocr/mlkit/MlKitOcrEngine.kt:87`, `ocr/tesseract/TesseractOcrEngine.kt:187` | TesseractPageTest, TextLayoutTest | — | ok |
| spaced, ko | App text puts the language's separator between lines a space or line break wraps | `overlay/capture/AccessibilityText.kt:139`, `overlay/OverlayController.kt:1092` | AccessibilityTextTest `a line wrapped at a space…` | AccessibilityTextTest (Japanese joins without one; a wrap inside a word joins) | fixed |
| all | Vertical lines are detected by geometry; right-to-left by script | `ocr/TextOrientation.kt:9`, `:13` | ReadingOrderTest, TextLayoutTest | — | limit |

## Bugs fixed in this review

1. French and Italian took the n of rock’n’roll for an elided word, so a word aimed after it started at "roll".
2. Turkish "ŞTİ." in capitals missed its abbreviation (root lowercase gives i with a combining dot).
3. "３．１４" ended a Japanese or Chinese sentence at the full-width period.
4. Yomitan's word resolution cuts a word at a combining mark (`[^\p{Letter}]` in `_getNextSubstring`): stressed Russian
   text typed with U+0301 also showed entries for "ко", NFD Vietnamese for "hoa". The lookup now drops such matches in
   languages looked up by words.
5. `{sentence}` took in every earlier sentence of the paragraph when a closing quote had no opening one (“ja” in
   German, Russian or Latvian text, where “ closes „) or a list item's ")" stood before the word; forward, a „…“ in
   Polish text ran the sentence to the paragraph end.
6. App text joined the last word of a line and the first of the next without a space when the wrap or line break had
   no character location ("Helloworld"), so in spaced languages the aimed word or phrase across a wrap was wrong.
7. `{sentence-furigana}` stepped one letter after unknown text and found words inside words (happy in unhappy).

## Limits left as they are

- German one-letter abbreviations ("z. B.") keep "Plan B." from ending a sentence; Polish "ok" likewise.
- `Compounds.word` stops at a combining mark in NFD text; German text is NFC in practice.
- Furigana of languages without reading rules goes through Yomitan's Japanese distribution, as Yomitan does;
  `{part-of-speech}` uses Japanese names, as Yomitan does.
- `∥` example lines are dropped from definition copies in every language (`popup/definition.js:320`).
- `render/render.js:151`: data keys starting with Hangul or Cyrillic get `data-sc-`, Yomitan gives none (CJK keys
  are kept verbatim).
- reading-ru rules are for Russian only; Ukrainian and Belarusian (group 4a) would need their own.
- No joining of words hyphenated at a line end ("impor- tant") in any language; a feature, not a rule gap.
- Paddle lines are paragraphs of their own; Lens takes separators from its response.
- The frequency chip's initial is uppercased without the Turkish locale (cosmetic).
- English quotes in German text: a closing ” right after a terminator starts the next sentence ('” Dann ging er.'),
  as German pairs „ with “.
- No tests for `MlKitOcrEngine.model()`, the overlay's `wordStart`/`earliestStart` (WORD_LOOKBACK 32), or a
  `{phonetic-transcriptions}` note; Korean and Chinese have no SentenceTest case.

## Open questions

- Russian furigana markers: `{furigana}` gives `<ruby>кошка<rt>ко́шка</rt></ruby>`, `{furigana-plain}` `кошка[ко́шка]`,
  and `{sentence-furigana}` puts the lemma's stressed form over an inflected word (кошки[ко́шка]). The decision table
  settles only `{expression}` кошка and `{reading}` ко́шка.

## Device checks

`test-model.md`: LK28 (stress and tone marks typed apart), LK29 (rock’n’roll), OCR26 (app text across a wrap),
ANK12 (`{sentence}` edge cases: ３．１４, LTD. ŞTİ., German text with English quotes), ANK13 (sentence furigana in a
language looked up by words).

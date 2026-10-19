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
| spaced (all but ja, zh) | The lookup starts at the start of the aimed word; `'’-` between letters and combining marks stay inside it | `lang/SpacedLanguageSupport.kt:89` | GermanTest (hyphen), FrenchTest, Isolation (apostrophe words in every spaced language) | Isolation (ja, zh start at the aim; Korean has its own rule) | ok |
| fr, it, pt | An elided word (l', qu', d') starts a new word after the apostrophe | `lang/SpacedLanguageSupport.kt:227`, `FrenchSupport.kt:24`, `ItalianSupport.kt:25`, `PortugueseSupport.kt:24` | FrenchTest, ItalianTest, PortugueseTest | Isolation (the others keep "l'eau", "d'água" whole) | ok |
| fr, it | A letter between two apostrophes (rock’n’roll) is no elision | `lang/SpacedLanguageSupport.kt:227` | FrenchTest `a letter between two apostrophes…` | Isolation (rock’n’roll whole everywhere) | fixed |
| vi | The lookup also starts one and two syllables before the aimed one | `lang/SpacedLanguageSupport.kt:119`, `VietnameseSupport.kt:26`; `api/EarlierStarts.kt:13`; `overlay/LookupStarts.kt:22` | VietnameseTest, EarlierStartsTest, LookupStartsTest | VietnameseTest `other languages start only…`, Isolation, LookupStartsTest (English starts at the word) | ok |
| spaced | A word hyphenated at a line end ("impor- tant") is tried joined (lowercase second half) and with its hyphen kept ("well-known"); the text as written keeps its results; aimed at the second half, the lookup starts at the first half as well | `lang/SpacedLanguageSupport.kt:148`, `:197`; `api/LookupVariants.kt:12`; `overlay/LookupStarts.kt:22` | Isolation `a word hyphenated at a line end…`, LookupVariantsTest, LookupStartsTest | Isolation (dashes between words, hyphens inside a word; zh, ko, ja none), LookupStartsTest (Japanese) | decided |
| spaced, ko, ja, zh | The aimed word's start and the earlier starts look back at most 32 characters (code points) | `overlay/LookupStarts.kt:9` | LookupStartsTest | LookupStartsTest (Japanese starts at the aim) | ok |
| ko | The lookup starts at the start of the spaced word (보면서 → 보다); Latin letters belong to the word, digits do not | `lang/KoreanSupport.kt:85` | KoreanTest, I: KoreanLookupTest | Isolation (ja, zh start at the aim) | ok |
| ja, zh, ko | Latin words inside CJK text start at their first letter; a lone Latin word counts only as a whole | `lang/CjkText.kt:32`, `:54` | JapaneseTest, ChineseTest, KoreanTest | Isolation (`Ｔシャツ and wa|lk`) | ok |
| de, pl, tr, lv | A period after a short number (or a longer one before a lowercase word) is an ordinal, not a sentence end | `lang/SpacedLanguageSupport.kt:206` | GermanTest, PolishTest, TurkishTest, LatvianTest, SentenceTest | Isolation (others end at "3." and "1918."; "1918. Dann" ends everywhere) | ok |
| each spaced language | Its abbreviations ("Mr.", "z.B.", "т. е.", "TP.") do not end a sentence | `lang/SpacedLanguageSupport.kt:70`, each `<Lang>Support.abbreviations` | each `<Lang>Test`, SentenceTest | Isolation (each list holds only its own language's sentences) | ok |
| de | Single letters abbreviate only beside another one written apart (z. B., d. h., o. Ä., u. v. m.), so "Plan B." ends a sentence; "S." stands alone | `lang/SpacedLanguageSupport.kt:38`, `:177`, `GermanSupport.kt:59` | GermanTest `a single letter abbreviates only beside another one` | Isolation ("Plan B. Dann" ends everywhere; only German has paired letters) | fixed |
| pl | "ok." (około) holds the sentence only before a number or a lowercase word ("Jest ok. Idziemy." ends) | `lang/SpacedLanguageSupport.kt:44`, `PolishSupport.kt:34` | PolishTest `ok holds the sentence only…` | other languages have no such words | fixed |
| tr | Abbreviations in capitals match under Turkish case rules (ŞTİ.), and under the root rules for capitals read without their dots | `lang/SpacedLanguageSupport.kt:56`, `TurkishSupport.kt:31` | TurkishTest `abbreviations in capitals…` | the other languages keep `Locale.ROOT` | fixed |
| spaced, ko | A period followed at once by a letter or digit (3.14, p.m, example.com) does not end a sentence | `lang/SpacedLanguageSupport.kt:70`, `KoreanSupport.kt:67` | EnglishTest, KoreanTest, SentenceTest | Isolation (holds in every spaced language) | ok |
| ja, zh | A full-width period between digits (３．１４) is a decimal point | `lang/CjkText.kt:44` | JapaneseTest, ChineseTest | JapaneseTest ("第３．次に", "終わり．次" still end) | fixed |
| each language | Quote pairs: a terminator inside quotes opened before the word does not end the sentence | each `quotePairs`; `Sentence.kt:22` | SentenceTest (ja, zh, ko, en, es, fr, de, it, pt, pl, tr, lv, vi, ru), RussianTest, GermanTest | SentenceTest (balanced quotes unchanged) | ok |
| each language | A quote or bracket the paragraph does not close (“ja” in German text, a list's "1)") no longer takes in the whole paragraph: the nearest sentence end counts | `Sentence.kt:22`, `:77` | SentenceTest `aQuoteTheParagraphDoesNotClose…` (de, en, pl) | the same test (a closed quote still holds its terminators; Japanese 「」) | fixed |
| each language | ” and ’ right after a terminator end that sentence even where the language pairs its quotes otherwise (English quotes in German text) | `Sentence.kt:12` | SentenceTest `aClosingQuoteOfAnotherLanguageStaysWithItsSentence` | the same test (’Tis at the paragraph start; Polish „Idę.” as before) | fixed |
| zh, ko | Chinese ends at 。！？ but not at a Latin period; Korean at spaced periods but not at 3.14 | `ChineseSupport.kt:17`, `KoreanSupport.kt:21`, `Sentence.kt:22` | SentenceTest `chineseSentence…`, `koreanSentence…` | the same tests (quoted speech holds its terminator) | ok |
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
| ru (be, uk, sh with their branches) | At import, form-of headwords are also indexed without stress marks and the lemmas they name are stored without them (U+0301, U+0300 after a vowel; sh also U+030F, U+0311, U+0304, U+0302, and after r) | `lang/RussianSupport.kt` `headwordMarks`, `cpp/language/archive_split.cpp` `MarkStripper` | I: LanguageLookupTest `stressMarksOfFormsAndTheirLemmasAreDroppedAtImport` | the same test (ё keeps its diaeresis); languages without `headwordMarks` pass nothing | fixed |
| vi | Tone marks in the old and the new placement (hoà ↔ hòa) | `vi.js` bundle | glue `vietnamese tone marks…` | glue (fr, es, pt, it do not) | ok |
| zh | Kangxi radicals read as their ideographs (⽇本 → 日本) | `zh.js` bundle | glue, I: ChineseLookupTest | — | ok |

## Page scripts (popup and Anki notes)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| ru | A reading that only adds stress replaces the headword (ко́шка); another reading goes over it | `popup/reading-ru.js:5` | reading.mjs, popup.mjs | reading.mjs `russian and chinese rules stay with their languages` (uk, be, bg, en, de) | ok |
| zh | Pinyin split into syllables, one over each character, with tone colors | `popup/reading-zh.js:5`, `popup/popup.js:383`, `:735` | reading.mjs, popup.mjs | reading.mjs (ja, vi, ko get no syllables) | ok |
| ja | Definition copy rules of Japanese dictionaries | `popup/definition-ja.js:5` | definition.mjs | definition.mjs `a language without rules of its own…` | ok |
| ja (default) | Furigana by Yomitan's Japanese distribution for every language without reading rules | `popup/note.js:86`, `render/render.js:570` | note.mjs, anki.test.mjs | — | limit |
| all | `{phonetic-transcriptions}`: a list of every transcription the dictionaries give, escaped; an English word has empty pitch markers, a Japanese pitch dictionary no transcriptions | `popup/note.js:358` | note.mjs `phonetic transcriptions list each one…` | the same test | ok |
| tr (page language) | The frequency chip's initial is uppercased by the page language's case rules (ingilizce → İ in Turkish) | `popup/popup.js:910`, `:916` | popup.mjs `the first letter of a long dictionary name…` | the same test (en and no language give I) | fixed |
| all (render) | Structured content `data` keys become attributes as Yomitan names them: data-sc- and kebab case for Latin keys, data-sc and the key for others (data-sc日本, data-sc한국, data-scКлюч); a key the dataset refuses is skipped | `render/render.js:156` | anki.test.mjs `structured content data attributes…` | the same test | fixed |
| ja, zh, ru | Furigana markers (`{furigana}`, `{sentence-furigana}`, their plain forms) offered in the + menu only where readings go over or into words; a field that has them is still filled | `LanguageSupport.kt:67`, `FieldTemplate.kt:78` | FieldTemplateTest `offersFuriganaMarkersOnlyWhereReadingsGoOverOrIntoWords` | the same test (en, ko and the others get none; `guessField` gives none for English) | decided |
| zh, ru | A second line under each furigana marker in the + menu says what it gives (pinyin, stress marks) | `languages/LanguageStrings.kt:71`, `anki/AnkiSettingsScreen.kt:467` | FuriganaMarkerHintsTest | the same test (ja and the others get none) | decided |
| ru | `{furigana}`, `{furigana-plain}`: the stressed word itself (ко́шка), no ruby; another reading (ёж for еж) goes over the word | `popup/note.js:86`, `:99` | note.mjs `russian furigana put the stress into the word` | note.mjs (zh, ja keep their ruby) | decided |
| ru | `{sentence-furigana}`: stress marks only in words written in their dictionary form; inflected forms stay as written (рука́, ру́ки) | `popup/reading-ru.js:34`, `popup/note.js:138` | note.mjs `russian sentence furigana…`, reading.mjs `a russian word in a sentence…` | reading.mjs (zh, ja have no sentence rules) | decided |
| ja | `{cloze-body-kana}`, pitch-accent markers offered only for Japanese | `JapaneseSupport.kt:19`, `popup/note.js:320`, `:354` | FieldTemplateTest, note.mjs | `SpacedLanguageSupport.kt:21` (no markers) | ok |
| zh | `{expression-simplified}`, `{expression-traditional}` | `ChineseSupport.kt:24` | FieldTemplateTest, ShownResultsTest | other languages have no script markers | ok |
| spaced | `{sentence-furigana}`: unknown text is passed over a word at a time, so no match starts inside a word | `overlay/anki/SentenceParser.kt:54` | SentenceParserTest `in a language looked up by words…` | the same test (ja, ko still step by character) | fixed |

## Recognition and app text (`core:ocr`, overlay)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| each OCR script | ML Kit model by script, Japanese and Chinese apart; Cyrillic has none | `ocr/mlkit/MlKitOcrEngine.kt:53` | MlKitOcrEngineTest | MlKitOcrEngineTest (only Cyrillic-script languages are not read) | ok |
| each language | OCR words are joined by the language's separator (empty for ja, zh) | `ocr/mlkit/MlKitOcrEngine.kt:87`, `ocr/tesseract/TesseractOcrEngine.kt:187` | TesseractPageTest, TextLayoutTest | — | ok |
| spaced, ko | App text puts the language's separator between lines a space or line break wraps | `overlay/capture/AccessibilityText.kt:139`, `overlay/OverlayController.kt:1092` | AccessibilityTextTest `a line wrapped at a space…` | AccessibilityTextTest (Japanese joins without one; a wrap inside a word joins) | fixed |
| all | App text keeps a combining mark typed apart (empty box in a text view) with its letter, in the letter's box | `overlay/capture/AccessibilityText.kt:158` | AccessibilityTextTest `a combining mark without a box stays with its letter` | AccessibilityTextTest (a mark after a character without a box, or at the start, is dropped) | fixed |
| spaced | Lens gives no separator after a word hyphenated at a line end; the language's separator is put there, so the break rules see it | `ocr/HyphenBreaks.kt:9`, `ocr/lens/LensOcrEngine.kt:76` | HyphenBreaksTest | HyphenBreaksTest (a dash of its own, a hyphen after a digit, a separator Lens gave, the last line, Japanese) | fixed |
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
8. German "Plan B." and "Vitamin A." did not end a sentence (single letters were listed as abbreviations alone), and
   "o. Ä." did end one at "Ä." (ä was missing).
9. Polish "Jest ok. Idziemy." did not end at "ok.".
10. English quotes in German text: 'Er sagte “Komm.” Dann…' gave the next sentence a leading ” and left it out of
    the first.
11. Structured content data keys starting with Hangul, Cyrillic, a digit or another non-Latin character got
    `data-sc-`, unlike Yomitan, so a dictionary's CSS written for Yomitan missed them; keys Yomitan skips (foo-bar)
    were kept.
12. The frequency chip's initial ignored Turkish case rules (I for ingilizce instead of İ).
13. App text dropped a combining mark typed apart, whose box a text view reports empty, and ended the line there:
    hoà written with U+0300 was looked up as "hoa" (the flower only), and ко́шка was highlighted as two overlapping
    boxes. Found on the emulator (LK28); the language engine itself handles such text (glue test).
14. Lens joins the halves of a word hyphenated at a line end with no separator ("impor-tant"), so the break rules
    never matched and neither half found anything (LK30).

## Limits left as they are

- `Compounds.word` stops at a combining mark in NFD text; OCR and app text give NFC, so a German compound typed in
  NFD is not split. Not worth the mapping it would need.
- Furigana of languages without reading rules goes through Yomitan's Japanese distribution, as Yomitan does;
  `{part-of-speech}` uses Japanese names, as Yomitan does.
- `∥` example lines are dropped from definition copies in every language (`popup/definition.js:320`): by design,
  ∥ separates an example from its translation wherever it appears.
- reading-ru rules are for Russian only; Ukrainian and Belarusian (group 4a) need their own when they are added.
- Paddle lines are paragraphs of their own; Lens takes separators from its response.
- ML Kit leaves a line-final hyphen out of its words ("impor" / "tant"), so a hyphenated word is not joined with
  it and each half is looked up alone. Joining every word across a line break without a hyphen would find wrong
  words ("the" / "rapist"). Whether ML Kit's line text keeps the hyphen is not checked.
- The frequency chip's initial follows the page language, not the dictionary title's (unknown): an English title in
  a Turkish profile gets İ.
- A word hyphenated at a line end is joined only when the second half is in lowercase; a real hyphen with a capital
  after it (Max- Planck) is tried with the hyphen only. A hyphen at a line end in Japanese, Chinese or Korean text
  is left alone.
- `{phonetic-transcriptions}` lists the transcriptions without their tags; Yomitan puts the tags in italics before
  each one. Not language-specific; left for the Anki template review.

## Owner decisions in this review

Words hyphenated at a line end (owner: "try to join, since it may not be one word"): the lookup tries the joined
word and the word with its hyphen, and keeps the text as written when neither is found.

Furigana markers outside Japanese (to be recorded in the plan's decision table):

- The names stay Yomitan's (`{furigana}`, `{furigana-plain}`, `{sentence-furigana}`, `{sentence-furigana-plain}`).
- They are offered only for Japanese, Chinese and Russian: languages whose readings go over or into the words. Korean
  is left out (hanja are rare in modern text). A field that already has them keeps getting them in any language.
- Chinese and Russian show what the markers give as a second line under each name in the + menu.
- Russian: the stress goes into the text. `{furigana}` and `{furigana-plain}` give ко́шка; in a sentence only words
  written in their dictionary form get stress marks, since an inflected form may stress another syllable.
  Before: `<ruby>кошка<rt>ко́шка</rt></ruby>`, `кошка[ко́шка]`, and the lemma's stress over an inflected word.

## Device checks

`test-model.md`: LK28 (stress and tone marks typed apart), LK29 (rock’n’roll), OCR26 (app text across a wrap),
ANK12 (`{sentence}` edge cases: ３．１４, LTD. ŞTİ., German text with English quotes), ANK13 (sentence furigana in a
language looked up by words), ANK14 (furigana markers by language, their hints, Russian stress in the text), LK30
(a word hyphenated at a line end), ANK12 extended (Plan B., ok., English quotes in German), POP24 (the Turkish chip
initial).

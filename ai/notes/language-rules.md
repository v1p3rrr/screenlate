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
| de, pl, tr, lv, cs, sh | A period after a short number (or a longer one before a lowercase word) is an ordinal, not a sentence end | `lang/SpacedLanguageSupport.kt:206` | GermanTest, PolishTest, TurkishTest, LatvianTest, CzechTest, SerboCroatianTest, SentenceTest | Isolation (others end at "3." and "1918."; "1918. Dann" ends everywhere) | ok |
| each spaced language | Its abbreviations ("Mr.", "z.B.", "т. е.", "TP.") do not end a sentence | `lang/SpacedLanguageSupport.kt:70`, each `<Lang>Support.abbreviations` | each `<Lang>Test`, SentenceTest | Isolation (each list holds only its own language's sentences) | ok |
| de, cs, sh | Single letters abbreviate only beside another one written apart (z. B., d. h., o. Ä., u. v. m.; Czech př. n. l., also with the l read as a capital I, as OCR reads it in sans-serif text; Serbo-Croatian p. n. e., п. н. е.), so "Plan B." ends a sentence; "S." stands alone | `lang/SpacedLanguageSupport.kt:38`, `:177`, `GermanSupport.kt:59`, `CzechSupport.kt:41`, `SerboCroatianSupport.kt` `pairedLetters` | GermanTest `a single letter abbreviates only beside another one`, CzechTest (also `the l of the era read as a capital I`), SerboCroatianTest `before the common era…`, SentenceTest | Isolation ("Plan B. Dann" ends everywhere; only German, Czech and Serbo-Croatian have paired letters, "n. l." holds only in Czech) | fixed |
| pl, sh | A word that holds the sentence only before a number or a lowercase word: Polish "ok.", Serbo-Croatian "Kr." and "Хр." after "pr." ("pr. Kr. godine" holds, "pr. Kr. Onda" ends) | `lang/SpacedLanguageSupport.kt:44`, `SerboCroatianSupport.kt` `abbreviationsBeforeLowercase` | SerboCroatianTest `before the common era, kr holds only before a lowercase word` | Isolation ("Kr. godine" ends in the other languages) | ok |
| pl | "ok." (około) holds the sentence only before a number or a lowercase word ("Jest ok. Idziemy." ends) | `lang/SpacedLanguageSupport.kt:44`, `PolishSupport.kt:34` | PolishTest `ok holds the sentence only…` | other languages have no such words | fixed |
| tr | Abbreviations in capitals match under Turkish case rules (ŞTİ.), and under the root rules for capitals read without their dots | `lang/SpacedLanguageSupport.kt:56`, `TurkishSupport.kt:31` | TurkishTest `abbreviations in capitals…` | the other languages keep `Locale.ROOT` | fixed |
| spaced, ko | A period followed at once by a letter or digit (3.14, p.m, example.com) does not end a sentence | `lang/SpacedLanguageSupport.kt:70`, `KoreanSupport.kt:67` | EnglishTest, KoreanTest, SentenceTest | Isolation (holds in every spaced language) | ok |
| ja, zh | A full-width period between digits (３．１４) is a decimal point | `lang/CjkText.kt:44` | JapaneseTest, ChineseTest | JapaneseTest ("第３．次に", "終わり．次" still end) | fixed |
| each language | Quote pairs: a terminator inside quotes opened before the word does not end the sentence | each `quotePairs`; `Sentence.kt:22` | SentenceTest (ja, zh, ko, en, es, fr, de, it, pt, pl, tr, lv, nl, cs, id, ms, tl, vi, ru, uk, be), RussianTest, GermanTest | SentenceTest (balanced quotes unchanged) | ok |
| each language | A quote or bracket the paragraph does not close (“ja” in German text, a list's "1)") no longer takes in the whole paragraph: the nearest sentence end counts | `Sentence.kt:22`, `:77` | SentenceTest `aQuoteTheParagraphDoesNotClose…` (de, en, pl) | the same test (a closed quote still holds its terminators; Japanese 「」) | fixed |
| each language | ” and ’ right after a terminator end that sentence even where the language pairs its quotes otherwise (English quotes in German text) | `Sentence.kt:12` | SentenceTest `aClosingQuoteOfAnotherLanguageStaysWithItsSentence` | the same test (’Tis at the paragraph start; Polish „Idę.” as before) | fixed |
| zh, ko | Chinese ends at 。！？ but not at a Latin period; Korean at spaced periods but not at 3.14 | `ChineseSupport.kt:17`, `KoreanSupport.kt:21`, `Sentence.kt:22` | SentenceTest `chineseSentence…`, `koreanSentence…` | the same tests (quoted speech holds its terminator) | ok |
| ja | Respellings: 1人 → 一人, 々, ッ, dots and spaces, radical look-alikes | `lang/JapaneseSupport.kt:126`; `api/LookupVariants.kt:12` | JapaneseTest, LookupVariantsTest, I: LookupPipelineTest | Isolation (no other language respells) | ok |
| ja | Romaji to kana when the setting is on (Mozc table) | `lang/JapaneseSupport.kt:77`, `MozcRomaji.kt` | JapaneseTest, LookupVariantsTest | Isolation (`fromLatin`, `convertsLatin` only ja) | ok |
| ja, zh, ko | Character entries of the kanji/hanzi/hanja after the first (single kanji below the results) | `JapaneseSupport.kt:139`, `ChineseSupport.kt:98`, `KoreanSupport.kt:103` | JapaneseTest, ChineseTest, KoreanTest | spaced languages return none (`SpacedLanguageSupport.kt:118`) | ok |
| ja, zh, ko | Scripts without spaces are shortened by letters (`SearchResolution.LETTER`); ja and zh join words with no separator | each `searchResolution`, `wordSeparator` | Isolation | Isolation | ok |
| ru, sh-Cyrl, uk, be (Cyrillic script) | OCR Latin look-alikes in a Cyrillic word, or in a word of one letter (у, В, а; the Ukrainian and Belarusian і), become Cyrillic; in sh-Cyrl also j → ј (Serbian ј looks like the Latin j), in uk i, ï → і, ї, in be i, ŭ → і, ў; a longer word without a Cyrillic letter stays (Јој read as joj finds the Latin entry, which the dictionaries have too; TOP stays) | `lang/LanguageSupport.kt:27`, `CyrillicLookAlikes.kt`, `SerboCroatianSupport.kt`, `UkrainianSupport.kt`, `BelarusianSupport.kt` `ocrWord` | CyrillicLookAlikesTest (`a word of one letter…`), SerboCroatianTest `a latin j in a cyrillic word…`, UkrainianSupportTest, BelarusianSupportTest | CyrillicLookAlikesTest `only Cyrillic-script languages…`, Isolation (ј only in sh-Cyrl, a lone i only in uk and be, TOP nowhere) | fixed |
| sh | Two entries, sh-Latn and sh-Cyrl, each with its own code (settings, models, routes, backups), names "Serbo-Croatian (Latin)" / "(Cyrillic)", voice hr / sr, flag hr / rs, bubble glyph Đ / Ћ, e-ink code SH / СХ; one dictionary code sh for dictionaries, the Yomitan bundle, wordfreq, cloud recognition, `{language}` and headword marks | `core/common/Language.kt` `dictionaryCode`, `hasCode`; `SerboCroatianSupport.kt`; catalog `CatalogEntry.isFor` | SerboCroatianTest `each entry has its script…`, `the names say the script`, LanguageSetupTest, OcrModelsTest | Isolation `only the Serbo-Croatian entries share their dictionaries…`, `only Filipino and the Serbo-Croatian entries have their own names…` | ok |
| sh | Wiktionary's recordings are Serbian and titled in Cyrillic: a Latin word is looked for in Cyrillic under Sr- (kuća → Sr-кућа.flac); wordfreq's Latin words also rank their Cyrillic spellings (кућа W 553); system locales hr, sr, bs and cnr count as the language, so the translation does not default to them | `SerboCroatianSupport.kt` `recordingTitle`, `recordingPrefix`, `frequencySpellings`, `localeLanguages`; `SerboCroatianScripts.kt`; `core/anki/audio/AudioFinder.kt`; `core/translate/TranslationLanguages.kt` | SerboCroatianTest, SerboCroatianScriptsTest, WordfreqDictionaryTest, TranslationLanguagesTest `serbo-croatian is croatian in latin…` | Isolation (the other languages use their code; no Cyrillic spellings) | fixed |
| uk | wordfreq writes the apostrophe as ' only; its words with one also rank their ’ and ʼ spellings (wty uk-fr writes п’ять) | `UkrainianSupport.kt` `frequencySpellings`, `api/imports/WordfreqDictionary.kt` | UkrainianSupportTest `wordfreq words with an apostrophe…` | Isolation (only uk gets other apostrophes) | fixed |
| en, fr, de, es, pt | Audio and transcription regions (us/uk, fr/ca…, de/at/ch, es/mx…, br/pt) | each `audioRegions`, `transcriptionRegionNames`; `lang/AudioRegion.kt:12`, `TranscriptionRegions.kt:11` | TranscriptionRegionsTest, TranscriptionsTest, each `<Lang>Test` | languages without regions (it, pl, tr, lv, nl, cs, id, ms, tl, ru, vi, zh, ko): `<Lang>Test` | ok |

## Lookup pipeline (`dictionary:api`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| de, nl | A compound without an entry is split into the longest words, a linking s/es/n (Dutch s/en/e) skipped; the page names the parts above their entries ("Compound: A + B") | `api/Compounds.kt:12`, `api/DictionaryLookup.kt:92`, `GermanSupport.kt:21`, `DutchSupport.kt:24` | CompoundsTest | Isolation (`compoundLinks` only de, nl) | ok |
| de | A compound part's capitalized headwords (nouns) come first | `api/Compounds.kt:30`, `api/DictionaryLookup.kt:77` | CompoundsTest | Isolation (`capitalizedNouns` only de) | ok |
| vi | Chains through a "classifier" form-of entry are dropped, a word reached only through one is left out | `api/ShownResults.kt:48`, `VietnameseSupport.kt:29` | ShownResultsTest | ShownResultsTest (every other language keeps it), Isolation | ok |
| tl | Chains through a "Baybayin" form-of entry are dropped (basa → ᜊᜐ → basa, Basa), a word reached only through one is left out | `api/ShownResults.kt:48`, `FilipinoSupport.kt` | ShownResultsTest, FilipinoSupportTest | Isolation | ok |
| id, ms | Chains through a "Jawi" form-of entry are dropped (baca → باچ → baca), a word reached only through one is left out | `api/ShownResults.kt:48`, `IndonesianSupport.kt`, `MalaySupport.kt` | ShownResultsTest, MalaySupportTest | ShownResultsTest (other languages keep it), Isolation | ok |
| zh | A word found through its Simplified/Traditional spelling shows the spelling read | `api/ShownResults.kt:14`, `ChineseSupport.kt:121` | ShownResultsTest | ShownResultsTest `a spelling tag of another language's dictionary…`, Isolation | ok |
| zh | Numbered pinyin in parentheses is dropped from readings; readings spaced differently are one word | `ChineseSupport.kt:124`, `:128`; `api/ShownResults.kt:59` | ChineseTest, ShownResultsTest | ShownResultsTest `readings spaced differently stay apart outside Chinese`, Isolation | ok |
| zh | Fixed script: a word gives way to its spelling in the chosen script; Anki script markers | `api/ShownResults.kt:73`, `:107`; `ChineseScripts.kt` | ShownResultsTest, ChineseScriptsTest, ReadingWordsTest | Isolation (`scriptOf`, `inScript` only zh); `scriptTag` is null for English | ok |
| zh | Search by typed pinyin (reading index) | `ChineseSupport.kt:133`, `:135`; `api/DictionaryLookup.kt:124` | ChinesePinyinTest, ReadingWordsTest | Isolation (`readingSearch`, `readingKey` only zh) | ok |
| spaced | A match that ends before a combining mark is left out (ко in ко́шка, hoa in hoà typed apart) | `api/ShownResults.kt:31`, `api/DictionaryLookup.kt:89` | ShownResultsTest `a match that ends before a stress or tone mark…` | the same test (Japanese か + dakuten keeps its match; a match up to a space stays) | fixed |

## Engine (`dictionary:engine-hoshidicts`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| ja | Native hoshidicts lookup with its deinflector; other languages through their Yomitan bundle in QuickJS | `HoshidictsEngine.kt:101` | I: HoshidictsEngineTest, LookupPipelineTest, LanguageLookupTest | — (one path per language) | ok |
| all but ja | Form-of entries are followed, a second form-of step allowed (шокирующее → шокировать), never back to the text it started from (Jansen → Janssen → Jansen), nor back to the scanned text after a Yomitan rule (книга as the plural of книго, the vocative of книга) | `cpp/language/candidate_lookup.cpp:39`, `:185`, `:199` | I: LanguageLookupTest (`formStepsDoNotLeadBackToTheText`, `ukrainian…`), HoshidictsEngineTest | language-agnostic by design; a form that is its own lemma (kuća) keeps its one step | fixed |
| tr | Turkish case pairs (I ↔ ı, İ ↔ i) | `glue:45` | glue `turkish capitals…` | glue `other languages keep the default case pairs`, `turkish case pairs do not reach english` | ok |
| en | Untyped phrasal verbs ("picked up" → "pick up") for dictionaries without parts of speech | `glue:98`, `:125` | glue `english phrasal verbs…`, I: LanguageLookupTest | glue ("closed to" gets none; de, fr, es never) | ok |
| id, ms | Combining marks dropped (Yomitan's Indonesian `removeAlphabeticDiacritics`); Malay, which has no Yomitan descriptor, is built from Indonesian's under its own code | `id.js`, `ms.js` bundles; `scripts/yomitan-language/build.mjs:30` | glue `malay takes the indonesian text processors…` | glue (en keeps "bacá") | ok |
| tl | Affixes (mag-, ipag-, -in), the linker -ng and hyphens undone (Yomitan's `tagalogTransforms`, no infixes); combining marks dropped | `tl.js` bundle | glue `tagalog affixes and the linker ng…` | glue (id keeps "kainin") | ok |
| tl | Shown as "Filipino (Tagalog)" (Android names fil Filipino, Yomitan and Wiktionary say Tagalog); the voice is asked for fil; the code fil (text classifier, other packagers) reads as tl | `FilipinoSupport.kt` `nameIn`, `speechTag`; `Language.kt` `languageName`, `Language.of` | FilipinoSupportTest | Isolation `only Filipino and the Serbo-Croatian entries have their own names…` | ok |
| ru | Stress marks dropped, ё ↔ е (Yomitan's Russian processors) | `ru.js` bundle | glue `russian stress marks…`, I: LanguageLookupTest | glue (en, de, pl, lv, nl, cs do neither; id, ms and tl drop the marks as well, through their diacritics processors) | ok |
| ru, uk, be, sh | At import, form-of headwords are also indexed without stress marks and the lemmas they name are stored without them (U+0301, U+0300 after a vowel; sh also U+030F, U+0311, U+0304, U+0302, and after r) | `lang/RussianSupport.kt`, `UkrainianSupport.kt`, `BelarusianSupport.kt`, `SerboCroatianSupport.kt` `headwordMarks`, `cpp/language/archive_split.cpp` `MarkStripper` | I: LanguageLookupTest `stressMarksOfFormsAndTheirLemmasAreDroppedAtImport`, `serboCroatianWithoutDiacriticsFindsTheLettersInEitherEntry`, `ukrainian…` (а̀втостради), `belarusian…` (вады́) | the same test (ё keeps its diaeresis); languages without `headwordMarks` pass nothing | fixed |
| sh | Letters restored in text typed without diacritics (c → č, ć; s → š; z → ž; dj → đ) and the accent marks of readings dropped from Latin and Cyrillic vowels and r (Yomitan's Serbo-Croatian processors; the glue replaces the accent one, which knows Latin letters only); at most 4096 variants a processor, as Yomitan cuts them (scan length 16: 2–10 ms per lookup on the emulator, 140–260 ms for "cccc…") | `sh.js` bundle, `glue:45` | glue `serbo-croatian text without diacritics…`, I: LanguageLookupTest `serboCroatian…` | glue (Czech kuca finds no kuća, Russian ку̏ћа no кућа) | fixed |
| uk | Yomitan's 35 rules; stress marks dropped from the text (U+0301); the apostrophes ' ‘ ’ ʼ ` ´ each also tried as ', ’ and ʼ | `uk.js` bundle | glue `ukrainian deinflects…`, I: LanguageLookupTest `ukrainian…` | glue (ru gets no other apostrophe) | ok |
| be | Ukrainian's stress mark and apostrophe processors added to Belarusian's descriptor, which has capitalization only (сям’я → сям'я, ва́да → вада); the build fails if Yomitan gives Belarusian more processors | `be.js` bundle, `scripts/yomitan-language/build.mjs` `addedPreprocessors` | glue `belarusian takes the ukrainian…`, I: LanguageLookupTest `belarusian…` | glue (ru gets no other apostrophe) | ok |
| all but ja | Yomitan's console messages (a text processor's variants cut at 4096) are dropped: QuickJS has no console, and the messages carry the looked-up text | `scripts/yomitan-language/console-stub.js` | glue `a text with more variants than yomitan keeps…`, I: LanguageLookupTest `serboCroatian…` | — | fixed |
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

Found while adding Serbo-Croatian:

15. Yomitan writes a warning through `console` when a text processor gives more than 4096 variants. QuickJS has no
    console, so the warning threw and the whole lookup failed. Serbo-Croatian Latin text with many c, s and z
    letters reaches the cut ("cecescecescecesc"). Node's `vm` contexts always have a console, so the glue tests
    missed it; the timing check on the emulator found it.
16. With the system in Croatian, Serbian or Bosnian, the sentence translation of Serbo-Croatian text defaulted to that
    language, which only repeats the text.
17. Yomitan's Serbo-Croatian accent processor drops the marks of readings from Latin vowels only: Cyrillic text
    written with them (ку̏ћа, as in textbooks and Wiktionary) found nothing.

Found while adding Ukrainian and Belarusian:

18. Yomitan's Ukrainian "nominative plural" takes книга for the plural of книго, and Wiktionary lists книго as the
    vocative of книга, so a feminine noun in -а typed or read in its dictionary form showed "nominative plural «
    vocative singular" beside it. The engine drops a form-of step back to the scanned text through another text.
19. OCR reads a word of one letter in Latin as often as in Cyrillic (і, у, В, а): no other letter shows its script,
    and the look-alike fix needed a Cyrillic letter in the word, so such a word found nothing, in Russian too.

## Limits left as they are

- `Compounds.word` stops at a combining mark in NFD text; OCR and app text give NFC, so a German compound typed in
  NFD is not split. Not worth the mapping it would need.
- Furigana of languages without reading rules goes through Yomitan's Japanese distribution, as Yomitan does;
  `{part-of-speech}` uses Japanese names, as Yomitan does.
- `∥` example lines are dropped from definition copies in every language (`popup/definition.js:320`): by design,
  ∥ separates an example from its translation wherever it appears.
- reading-ru rules are for Russian only. Ukrainian and Belarusian need none: their dictionaries give lemmas no
  reading, and the forms' stress is dropped from the key at import.
- wty uk-ru has some words twice, under ' and ’ (п'ятниця, п’ятниця): both are found, as two entries that look
  the same. Merging them would need an apostrophe rule in the grouping of every language.
- Ukrainian "див." (see) is also the genitive plural of диво, and "пор." (compare) of пора (a pore); a sentence ending in
  either does not end there.
- Paddle lines are paragraphs of their own; Lens takes separators from its response.
- ML Kit leaves a line-final hyphen out of its words ("impor" / "tant"), so a hyphenated word is not joined with
  it and each half is looked up alone. Joining every word across a line break without a hyphen would find wrong
  words ("the" / "rapist"). Whether ML Kit's line text keeps the hyphen is not checked.
- The frequency chip's initial follows the page language, not the dictionary title's (unknown): an English title in
  a Turkish profile gets İ.
- A word hyphenated at a line end is joined only when the second half is in lowercase; a real hyphen with a capital
  after it (Max- Planck) is tried with the hyphen only. A hyphen at a line end in Japanese, Chinese or Korean text
  is left alone.
- Compounds split greedily, the longest word first: Dutch stadsbestuurder gives stadsbestuur + der, not stads +
  bestuurder, since the dictionary has stadsbestuur and "der". Wiktionary's s-forms (werkgevers, dorps) mostly take
  the linking s with the first part.
- Czech Roman ordinals ("Karel IV. byl") end the sentence at their period; only digits are ordinals.
- A word that is also a form of itself (kuća, genitive plural of kuća) shows the form's name beside the headword,
  as Yomitan does: the empty chain of the direct match is not shown, the form's chain is.
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
initial), LK37 (Ukrainian), LK38 (Belarusian).

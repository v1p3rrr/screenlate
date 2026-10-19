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
| spaced (all but ja, zh) | The lookup starts at the start of the aimed word; `'’-` between letters, combining marks (the vowel signs of Indic scripts, spacing marks drawn before their letter at times: कि, पढ़ा) and the zero-width joiners stay inside it | `lang/SpacedLanguageSupport.kt` `wordStartOffset`, `isWordCharacter` | GermanTest (hyphen), FrenchTest, HindiSupportTest `vowel signs, nukta, virama and joiners…`, Isolation (apostrophe words, and `vowel signs and joiners stay inside their word…`, in every spaced language) | Isolation (ja, zh start at the aim; Korean has its own rule) | ok |
| fr, it, pt, el | An elided word (l', qu', d'; Greek σ', τ', μ', θ', ν', κ', απ', γι') starts a new word after the apostrophe; Greek θα'ναι, where the apostrophe takes the next word's vowel, stays whole | `lang/SpacedLanguageSupport.kt:227`, `FrenchSupport.kt:24`, `ItalianSupport.kt:25`, `PortugueseSupport.kt:24`, `GreekSupport.kt` `elisions` | FrenchTest, ItalianTest, PortugueseTest, GreekTest `a word after an elided one…` | Isolation (the others keep "l'eau", "d'água", "σ'αγαπώ" whole) | ok |
| fr, it | A letter between two apostrophes (rock’n’roll) is no elision | `lang/SpacedLanguageSupport.kt:227` | FrenchTest `a letter between two apostrophes…` | Isolation (rock’n’roll whole everywhere) | fixed |
| vi | The lookup also starts one and two syllables before the aimed one | `lang/SpacedLanguageSupport.kt:119`, `VietnameseSupport.kt:26`; `api/EarlierStarts.kt:13`; `overlay/LookupStarts.kt:22` | VietnameseTest, EarlierStartsTest, LookupStartsTest | VietnameseTest `other languages start only…`, Isolation, LookupStartsTest (English starts at the word) | ok |
| spaced | A word hyphenated at a line end ("impor- tant") is tried joined (lowercase second half) and with its hyphen kept ("well-known"); the text as written keeps its results; aimed at the second half, the lookup starts at the first half as well | `lang/SpacedLanguageSupport.kt:148`, `:197`; `api/LookupVariants.kt:12`; `overlay/LookupStarts.kt:22` | Isolation `a word hyphenated at a line end…`, LookupVariantsTest, LookupStartsTest | Isolation (dashes between words, hyphens inside a word; zh, ko, ja none), LookupStartsTest (Japanese) | decided |
| spaced, ko, ja, zh | The aimed word's start and the earlier starts look back at most 32 characters (code points) | `overlay/LookupStarts.kt:9` | LookupStartsTest | LookupStartsTest (Japanese starts at the aim) | ok |
| ko | The lookup starts at the start of the spaced word (보면서 → 보다); Latin letters belong to the word, digits do not | `lang/KoreanSupport.kt:85` | KoreanTest, I: KoreanLookupTest | Isolation (ja, zh start at the aim) | ok |
| ja, zh, ko | Latin words inside CJK text start at their first letter; a lone Latin word counts only as a whole | `lang/CjkText.kt:32`, `:54` | JapaneseTest, ChineseTest, KoreanTest | Isolation (`Ｔシャツ and wa|lk`) | ok |
| de, pl, tr, lv, cs, hr, sr | A period after a short number, or after a longer one or a Roman numeral in capitals before a lowercase word ("1918. gada", "Karel IV. byl"), is an ordinal, not a sentence end | `lang/SpacedLanguageSupport.kt:208` | GermanTest, PolishTest, TurkishTest, LatvianTest, CzechTest, SerboCroatianTest, SentenceTest | Isolation (others end at "3.", "1918." and "IV."; "1918. Dann" and "IV. Potom" end everywhere), GermanTest (IIII., iv.) | fixed |
| each spaced language | Its abbreviations ("Mr.", "z.B.", "т. е.", "TP.", "π.χ.") do not end a sentence | `lang/SpacedLanguageSupport.kt:70`, each `<Lang>Support.abbreviations` | each `<Lang>Test`, SentenceTest | Isolation (each list holds only its own language's sentences) | ok |
| de, cs, hr, sr | Single letters abbreviate only beside another one written apart (z. B., d. h., o. Ä., u. v. m.; Czech př. n. l., also with the l read as a capital I, as OCR reads it in sans-serif text; Croatian and Serbian p. n. e., п. н. е.), so "Plan B." ends a sentence; "S." stands alone | `lang/SpacedLanguageSupport.kt:38`, `:177`, `GermanSupport.kt:59`, `CzechSupport.kt:41`, `SerboCroatianSupport.kt` `pairedLetters` | GermanTest `a single letter abbreviates only beside another one`, CzechTest (also `the l of the era read as a capital I`), SerboCroatianTest `before the common era…`, SentenceTest | Isolation ("Plan B. Dann" ends everywhere; only German, Czech and Serbo-Croatian have paired letters, "n. l." holds only in Czech) | fixed |
| pl, hr, sr | A word that holds the sentence only before a number or a lowercase word: Polish "ok.", Croatian and Serbian "Kr." and "Хр." after "pr." ("pr. Kr. godine" holds, "pr. Kr. Onda" ends) | `lang/SpacedLanguageSupport.kt:44`, `SerboCroatianSupport.kt` `abbreviationsBeforeLowercase` | SerboCroatianTest `before the common era, kr holds only before a lowercase word` | Isolation ("Kr. godine" ends in the other languages) | ok |
| pl | "ok." (około) holds the sentence only before a number or a lowercase word ("Jest ok. Idziemy." ends) | `lang/SpacedLanguageSupport.kt:44`, `PolishSupport.kt:34` | PolishTest `ok holds the sentence only…` | other languages have no such words | fixed |
| tr | Abbreviations in capitals match under Turkish case rules (ŞTİ.), and under the root rules for capitals read without their dots | `lang/SpacedLanguageSupport.kt:56`, `TurkishSupport.kt:31` | TurkishTest `abbreviations in capitals…` | the other languages keep `Locale.ROOT` | fixed |
| spaced, ko | A period followed at once by a letter or digit (3.14, p.m, example.com) does not end a sentence | `lang/SpacedLanguageSupport.kt:70`, `KoreanSupport.kt:67` | EnglishTest, KoreanTest, SentenceTest | Isolation (holds in every spaced language) | ok |
| ja, zh | A full-width period between digits (３．１４) is a decimal point | `lang/CjkText.kt:44` | JapaneseTest, ChineseTest | JapaneseTest ("第３．次に", "終わり．次" still end) | fixed |
| each language | Quote pairs: a terminator inside quotes opened before the word does not end the sentence | each `quotePairs`; `Sentence.kt:22` | SentenceTest (ja, zh, ko, en, es, fr, de, it, pt, pl, tr, lv, nl, cs, id, ms, tl, vi, ru, uk, be, el, hi), RussianTest, GermanTest | SentenceTest (balanced quotes unchanged) | ok |
| each language | A quote or bracket the paragraph does not close (“ja” in German text, a list's "1)") no longer takes in the whole paragraph: the nearest sentence end counts | `Sentence.kt:22`, `:77` | SentenceTest `aQuoteTheParagraphDoesNotClose…` (de, en, pl) | the same test (a closed quote still holds its terminators; Japanese 「」) | fixed |
| each language | ” and ’ right after a terminator end that sentence even where the language pairs its quotes otherwise (English quotes in German text) | `Sentence.kt:12` | SentenceTest `aClosingQuoteOfAnotherLanguageStaysWithItsSentence` | the same test (’Tis at the paragraph start; Polish „Idę.” as before) | fixed |
| el | The Greek question mark ; (and U+037E) ends a sentence; the ano teleia · (a semicolon) does not | `GreekSupport.kt` `sentenceTerminators` | GreekTest `the question mark is a semicolon…`, SentenceTest `greekSentences…` | Isolation `only Greek ends a sentence at a semicolon…` | ok |
| hi | The danda । and the double danda ॥ end a sentence, as a period does | `HindiSupport.kt` `sentenceTerminators` | HindiSupportTest `the danda and the double danda…`, SentenceTest `hindiSentences…` | Isolation `only Hindi ends a sentence at the danda` | ok |
| hi | Abbreviations (डॉ., प्रो., ई.पू., कि.मी.; the vowel sign of डॉ is a mark, part of the word) and Latin letters written as their names, as initials joined by periods (ए.पी.जे., आई.आई.टी.) or apart (बी. आर.), hold the sentence; आई, जी and पी, which are words too (वह आई.), hold it only joined to another letter name | `lang/SpacedLanguageSupport.kt` `initials`, `endsSentence`; `HindiSupport.kt` `abbreviations`, `initials` | HindiSupportTest `abbreviations and initials…`, SentenceTest `hindiSentences…` | Isolation (`initials` only hi; डॉ. and ए.पी.जे. hold only in hi) | ok |
| bn | The danda । and the double danda ॥ end a sentence, as in Hindi | `BengaliSupport.kt` `sentenceTerminators` | BengaliSupportTest `the danda and the double danda…`, SentenceTest `bengaliSentences…` | Isolation `only Hindi and Bengali end a sentence at the danda` | ok |
| bn | Abbreviations (ড., ডা., মো., খ্রি., কি.মি.) and Latin letters written as their names, as initials joined by periods (এ.কে., বি.এন.পি.) or apart (এ. কে.), hold the sentence; এল, এস and ও, which are words too (তুমি এস.), end it | `BengaliSupport.kt` `abbreviations`, `initials` | BengaliSupportTest `abbreviations and initials…`, SentenceTest `bengaliSentences…` | Isolation (`initials` only hi and bn; মো. and এ.কে. hold only in bn) | ok |
| bn | The language is called Bengali in English and tiếng Bengal in Vietnamese, where Android and Java say Bangla; other locales take the phone's name | `BengaliSupport.kt` `nameIn` | BengaliSupportTest `the language is called Bengali…` | Isolation `only Filipino and Bengali have names of their own…`, BengaliSupportTest (Russian is the phone's name) | ok |
| ka | Abbreviations (ქ. city, წ. year, მაგ., ე.ი., ძვ. წ., გვ., ტ., იხ., შდრ., პროფ., აკად.) hold the sentence, also in a heading in Mtavruli capitals (Ქ., matched lowercased); ა.შ. (and so on), which mostly ends one, is left out | `GeorgianSupport.kt` `abbreviations`; `lang/SpacedLanguageSupport.kt` `lowercaseIn` | GeorgianSupportTest `abbreviations hold the sentence`, SentenceTest `georgianSentences…` | Isolation (ქ. and წ. hold only in ka) | ok |
| zh, ko | Chinese ends at 。！？ but not at a Latin period; Korean at spaced periods but not at 3.14 | `ChineseSupport.kt:17`, `KoreanSupport.kt:21`, `Sentence.kt:22` | SentenceTest `chineseSentence…`, `koreanSentence…` | the same tests (quoted speech holds its terminator) | ok |
| ja | Respellings: 1人 → 一人, 々, ッ, dots and spaces, radical look-alikes | `lang/JapaneseSupport.kt:126`; `api/LookupVariants.kt:12` | JapaneseTest, LookupVariantsTest, I: LookupPipelineTest | Isolation (no other language respells) | ok |
| ja | Romaji to kana when the setting is on (Mozc table) | `lang/JapaneseSupport.kt:77`, `MozcRomaji.kt` | JapaneseTest, LookupVariantsTest | Isolation (`fromLatin`, `convertsLatin` only ja) | ok |
| ja, zh, ko | Character entries of the kanji/hanzi/hanja after the first (single kanji below the results) | `JapaneseSupport.kt:139`, `ChineseSupport.kt:98`, `KoreanSupport.kt:103` | JapaneseTest, ChineseTest, KoreanTest | spaced languages return none (`SpacedLanguageSupport.kt:118`) | ok |
| ja, zh, ko | Scripts without spaces are shortened by letters (`SearchResolution.LETTER`); ja and zh join words with no separator | each `searchResolution`, `wordSeparator` | Isolation | Isolation | ok |
| ru, sr, uk, be (Cyrillic script), el (Greek) | OCR Latin look-alikes in a Cyrillic or Greek word, or in a word of one letter (у, В, а; the Ukrainian and Belarusian і; the Greek articles ο, Η), become letters of the script; in sr also j → ј (Serbian ј looks like the Latin j), in uk i, ï → і, ї, in be i, ŭ → і, ў; Greek takes the capitals it shares with Latin and the closest lowercase shapes (v → ν, n → η, c → ς, u → υ, w → ω); a longer word without a letter of the script stays (TOP, TAXI, iPhone stay; Serbian decides by the line, next rows) | `lang/LanguageSupport.kt` `ocrWord`, `LookAlikes.kt`, `SerboCroatianSupport.kt`, `UkrainianSupport.kt`, `BelarusianSupport.kt` | CyrillicLookAlikesTest (`a word of one letter…`), SerboCroatianTest `a latin j in a cyrillic word…`, UkrainianSupportTest, BelarusianSupportTest, GreekTest `latin look-alikes in a greek word…` | CyrillicLookAlikesTest `only Cyrillic-script languages…`, Isolation (ј only in sr, a lone i only in uk, be and el, a Latin T before ο only in el, TOP nowhere) | fixed |
| hr, sr | Two languages on one dictionary code sh, each with its own code (settings, models, routes, backups), the system's names Croatian / Serbian, voice hr / sr, flag hr / rs, bubble glyph Đ / Ћ, e-ink code HR / SR, page `lang` hr / sr; the dictionary code sh for dictionaries, the Yomitan bundle, wordfreq, cloud recognition, `{language}` and headword marks | `core/common/Language.kt` `dictionaryCode`, `hasCode`, `withDictionaries`; `SerboCroatianSupport.kt`; catalog `CatalogEntry.isFor` | SerboCroatianTest `each language has its script…`, `the names are the system's`, LanguageSetupTest, OcrModelsTest | Isolation `only Croatian and Serbian share their dictionaries…`, `only Filipino has its own name…` | ok |
| sr | Serbian is written in both scripts: OCR look-alikes become Cyrillic only on a line whose letters are mostly Cyrillic (Cyrillic letters less Latin letters with no Cyrillic twin; a line of look-alikes alone follows the page), and there also in a word of look-alikes alone (Ја, је, Јој, which engines read as Latin), except a Latin word in capitals (OK, TOP, TEXT stay) unless the line is a heading in capitals (Cyrillic letters, no lowercase ones: JOJ, OBO JE КУЋА → ЈОЈ, ОВО ЈЕ КУЋА); a Latin line keeps i, a, u, o. The same for every engine, whichever model is chosen (owner, question 65) | `SerboCroatianSupport.kt` `ocrLines`, `scriptBalance`; `LookAlikes.kt` `fixInScript`; `core/ocr/OcrModels.kt` `inLanguage` | SerboCroatianTest `serbian fixes look-alikes only on cyrillic lines…`, CyrillicLookAlikesTest `in text known to be Cyrillic…`, SerboCroatianTest `serbian keeps latin words in capitals…`, OcrPageLanguageTest `Serbian fixes…` | Isolation (only sr decides by the page: a lone y on a page of nothing else stays Latin) | fixed |
| hr, sr | Wiktionary recordings: Croatian looks for Hr-<word>, then the Serbian one in Cyrillic (ljubav → Hr-ljubav.ogg, Sr-љубав.flac); Serbian for Sr- in Cyrillic, then in Latin letters (a few files: Sr-grad.ogg); a Commons file counts only when its whole title is the recording (the search also matches inside titles: LL-Q7737 (rus)-…-вода.wav for Sr-вода). Lingua Libre: Serbian titles are in Latin letters, so a Cyrillic word is searched in them (вода → …-voda.wav); Croatian searches Q6654 and Q9299, Croatian speakers first. wordfreq's Latin words also rank their Cyrillic spellings (кућа W 553); system locales hr, sr, bs and cnr count as the language, so the translation does not default to them | `SerboCroatianSupport.kt` `recordings`, `linguaLibreItems`, `linguaLibreTitle`, `frequencySpellings`, `localeLanguages`; `SerboCroatianScripts.kt`; `core/anki/audio/AudioFinder.kt`, `AudioPages.kt` `wiktionaryFiles`, `linguaLibreSearch`; `core/translate/TranslationLanguages.kt` | SerboCroatianTest, SerboCroatianScriptsTest, AudioPagesTest, AudioFinderTest `croatian wiktionary recordings…`, `serbian lingua libre…`, WordfreqDictionaryTest, TranslationLanguagesTest | Isolation (the other languages search their dictionary code and one item; no Cyrillic spellings) | fixed |
| sr | The voice gets Latin words in Cyrillic (kuća → кућа); Latin text goes to the translation services as sr-Latn where they have it (Bing, Edge), else as Serbian (Google, which reads both) | `SerboCroatianSupport.kt` `speechText`, `translationTag`; `core/translate/SentenceTranslator.kt` | SerboCroatianTest `the serbian voice…`, `serbian text in latin letters…`, SentenceTranslatorTest `serbian in latin letters goes as sr-Latn…` | Isolation (only sr has a translation tag) | ok |
| el | wordfreq writes the final sigma as σ (της as τησ); a σ at a word end, also before an apostrophe, becomes ς before the ranks are written (`frequencyWord`), a lone σ stays | `GreekSupport.kt` `frequencyWord`, `api/imports/WordfreqDictionary.kt` | GreekTest `wordfreq's sigma…`, WordfreqDictionaryTest `a greek archive…` | Isolation (only el changes τησ) | ok |
| hi | wordfreq mostly writes anusvara (हूं) where headwords write candrabindu (हूँ); each word also ranks its spelling with the other mark | `HindiSupport.kt` `frequencySpellings`, `api/imports/WordfreqDictionary.kt` | HindiSupportTest `wordfreq's anusvara…`, WordfreqDictionaryTest `hindi words rank…` | Isolation (only hi respells हूं) | ok |
| uk | wordfreq writes the apostrophe as ' only; its words with one also rank their ’ and ʼ spellings (wty uk-fr writes п’ять) | `UkrainianSupport.kt` `frequencySpellings`, `api/imports/WordfreqDictionary.kt` | UkrainianSupportTest `wordfreq words with an apostrophe…` | Isolation (only uk gets other apostrophes) | fixed |
| en, fr, de, es, pt | Audio and transcription regions (us/uk, fr/ca…, de/at/ch, es/mx…, br/pt) | each `audioRegions`, `transcriptionRegionNames`; `lang/AudioRegion.kt:12`, `TranscriptionRegions.kt:11` | TranscriptionRegionsTest, TranscriptionsTest, each `<Lang>Test` | languages without regions (it, pl, tr, lv, nl, cs, id, ms, tl, ru, vi, zh, ko): `<Lang>Test` | ok |

## Lookup pipeline (`dictionary:api`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| de, nl | A compound without an entry is split into the fewest words, then the split whose shortest part is longest (stads + bestuurder, not stadsbestuur + der), then the longest first, a linking s/es/n (Dutch s/en/e) skipped; the page names the parts above their entries ("Compound: A + B") | `api/Compounds.kt:12`, `api/DictionaryLookup.kt:92`, `GermanSupport.kt:21`, `DutchSupport.kt:24` | CompoundsTest | Isolation (`compoundLinks` only de, nl), CompoundsTest (the lookup limit) | fixed |
| de | A compound part's capitalized headwords (nouns) come first | `api/Compounds.kt:30`, `api/DictionaryLookup.kt:77` | CompoundsTest | Isolation (`capitalizedNouns` only de) | ok |
| vi | Chains through a "classifier" form-of entry are dropped, a word reached only through one is left out | `api/ShownResults.kt:48`, `VietnameseSupport.kt:29` | ShownResultsTest | ShownResultsTest (every other language keeps it), Isolation | ok |
| tl | Chains through a "Baybayin" form-of entry are dropped (basa → ᜊᜐ → basa, Basa), a word reached only through one is left out | `api/ShownResults.kt:48`, `FilipinoSupport.kt` | ShownResultsTest, FilipinoSupportTest | Isolation | ok |
| id, ms | Chains through a "Jawi" form-of entry are dropped (baca → باچ → baca), a word reached only through one is left out | `api/ShownResults.kt:48`, `IndonesianSupport.kt`, `MalaySupport.kt` | ShownResultsTest, MalaySupportTest | ShownResultsTest (other languages keep it), Isolation | ok |
| zh | A word found through its Simplified/Traditional spelling shows the spelling read | `api/ShownResults.kt:14`, `ChineseSupport.kt:121` | ShownResultsTest | ShownResultsTest `a spelling tag of another language's dictionary…`, Isolation | ok |
| zh | Numbered pinyin in parentheses is dropped from readings; readings spaced differently are one word | `ChineseSupport.kt:124`, `:128`; `api/ShownResults.kt:59` | ChineseTest, ShownResultsTest | ShownResultsTest `readings spaced differently stay apart outside Chinese`, Isolation | ok |
| zh | Fixed script: a word gives way to its spelling in the chosen script; Anki script markers | `api/ShownResults.kt:73`, `:107`; `ChineseScripts.kt` | ShownResultsTest, ChineseScriptsTest, ReadingWordsTest | Isolation (`scriptOf`, `inScript` only zh); `scriptTag` is null for English | ok |
| zh | Search by typed pinyin (reading index) | `ChineseSupport.kt:133`, `:135`; `api/DictionaryLookup.kt:124` | ChinesePinyinTest, ReadingWordsTest | Isolation (`readingSearch`, `readingKey` only zh) | ok |
| spaced | A match that ends before a combining mark is left out (ко in ко́шка, hoa in hoà typed apart) | `api/ShownResults.kt:31`, `api/DictionaryLookup.kt:89` | ShownResultsTest `a match that ends before a stress or tone mark…` | the same test (Japanese か + dakuten keeps its match; a match up to a space stays) | fixed |
| ka | 24 characters from the aim by default, Georgian words running long (5% of the forms are longer than 16), until the scan length is set; the setting stays per language | `lang/LanguageSupport.kt` `defaultScanLength`, `GeorgianSupport.kt`, `api/settings/LookupSettings.kt` | LookupSettingsRepositoryTest `georgianScansTwentyFour…`, GeorgianSupportTest | Isolation `only Georgian considers more characters…`, LookupSettingsRepositoryTest (English 16) | ok |

## Engine (`dictionary:engine-hoshidicts`)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| ja | Native hoshidicts lookup with its deinflector; other languages through their Yomitan bundle in QuickJS | `HoshidictsEngine.kt:101` | I: HoshidictsEngineTest, LookupPipelineTest, LanguageLookupTest | — (one path per language) | ok |
| all but ja | Form-of entries are followed, a second form-of step allowed (шокирующее → шокировать), never back to the text it started from (Jansen → Janssen → Jansen), nor back to the scanned text after a Yomitan rule (книга as the plural of книго, the vocative of книга) | `cpp/language/candidate_lookup.cpp:39`, `:185`, `:199` | I: LanguageLookupTest (`formStepsDoNotLeadBackToTheText`, `ukrainian…`), HoshidictsEngineTest | language-agnostic by design; a form that is its own lemma (kuća) keeps its one step | fixed |
| tr | Turkish case pairs (I ↔ ı, İ ↔ i) | `glue:45` | glue `turkish capitals…` | glue `other languages keep the default case pairs`, `turkish case pairs do not reach english` | ok |
| en | Untyped phrasal verbs ("picked up" → "pick up") for dictionaries without parts of speech | `glue:98`, `:125` | glue `english phrasal verbs…`, I: LanguageLookupTest | glue ("closed to" gets none; de, fr, es never) | ok |
| id, ms | Combining marks dropped (Yomitan's Indonesian `removeAlphabeticDiacritics`); Malay, which has no Yomitan descriptor, is built from Indonesian's under its own code | `id.js`, `ms.js` bundles; `scripts/yomitan-language/build.mjs:30` | glue `malay takes the indonesian text processors…` | glue (en keeps "bacá") | ok |
| tl | Affixes (mag-, ipag-, -in), the linker -ng and hyphens undone (Yomitan's `tagalogTransforms`, no infixes); combining marks dropped | `tl.js` bundle | glue `tagalog affixes and the linker ng…` | glue (id keeps "kainin") | ok |
| tl | Shown as "Filipino (Tagalog)" (Android names fil Filipino, Yomitan and Wiktionary say Tagalog); the voice is asked for fil; the code fil (text classifier, other packagers) reads as tl | `FilipinoSupport.kt` `nameIn`, `speechTag`; `Language.kt` `languageName`, `Language.of` | FilipinoSupportTest | Isolation `only Filipino and Bengali have names of their own…` | ok |
| ru | Stress marks dropped, ё ↔ е (Yomitan's Russian processors) | `ru.js` bundle | glue `russian stress marks…`, I: LanguageLookupTest | glue (en, de, pl, lv, nl, cs do neither; id, ms and tl drop the marks as well, through their diacritics processors) | ok |
| el | At import, every headword with definitions is also keyed without its tonos (U+0301 on α ε η ι ο υ ω) in the form-of table, a step with no tags back to itself; form rows are keyed the same way and keep their accented lemma; the table is written for dictionaries without form rows too. So text without tonos and capitals (ΣΚΥΛΟΣ, lowercased σκυλος) find σκύλος, and text with tonos finds only that spelling (πότε, not ποτέ) | `GreekSupport.kt` `headwordMarks` (`everyHeadword`), `cpp/language/archive_split.cpp` `split_bank`, `cpp/jni_bridge.cpp` | I: LanguageLookupTest `greekWithoutTonosFindsHeadwords…`, `greekDictionaryWithoutForms…` | Isolation (`everyHeadword` only el and hi); ru, uk, be, hr, sr still key forms only (`stressMarksOfFormsAndTheirLemmasAreDroppedAtImport`) | ok |
| hi | At import, every headword with definitions is also keyed without nukta (U+093C after क–ह, in NFC as headwords write it), with anusvara for candrabindu (`HeadwordMarks.replacements`), and with both, as a step with no tags back to itself; form rows are keyed the same way and keep their lemma. So पढता and लडकियां find पढ़ना and लड़की, हूं finds हूँ, and a word with nukta finds only that spelling (ख़ाना, not खाना; खाना finds both) | `HindiSupport.kt` `headwordMarks`, `cpp/language/archive_split.cpp` `MarkStripper::keys`, `HoshidictsEngine.kt` (`replacements` in the marks JSON) | I: LanguageLookupTest `hindiWithoutNuktaOrWithAnusvara…` | Isolation `only Greek and Hindi key every headword…` (`replacements` only hi) | ok |
| ru, uk, be, hr, sr | At import, form-of headwords are also indexed without stress marks and the lemmas they name are stored without them (U+0301, U+0300 after a vowel; hr and sr also U+030F, U+0311, U+0304, U+0302, and after r) | `lang/RussianSupport.kt`, `UkrainianSupport.kt`, `BelarusianSupport.kt`, `SerboCroatianSupport.kt` `headwordMarks`, `cpp/language/archive_split.cpp` `MarkStripper` | I: LanguageLookupTest `stressMarksOfFormsAndTheirLemmasAreDroppedAtImport`, `croatianAndSerbianWithoutDiacriticsFindTheLettersInEitherScript`, `ukrainian…` (а̀втостради), `belarusian…` (вады́) | the same test (ё keeps its diaeresis); languages without `headwordMarks` pass nothing | fixed |
| hr, sr | Letters restored in text typed without diacritics (c → č, ć; s → š; z → ž; dj → đ) and the accent marks of readings dropped from Latin and Cyrillic vowels and r (Yomitan's Serbo-Croatian processors; the glue replaces the accent one, which knows Latin letters only); at most 4096 variants a processor, as Yomitan cuts them (scan length 16: 2–10 ms per lookup on the emulator, 140–260 ms for "cccc…") | `sh.js` bundle, `glue:45` | glue `serbo-croatian text without diacritics…`, I: LanguageLookupTest `croatianAndSerbian…` | glue (Czech kuca finds no kuća, Russian ку̏ћа no кућа) | fixed |
| uk | Yomitan's 35 rules; stress marks dropped from the text (U+0301); the apostrophes ' ‘ ’ ʼ ` ´ each also tried as ', ’ and ʼ | `uk.js` bundle | glue `ukrainian deinflects…`, I: LanguageLookupTest `ukrainian…` | glue (ru gets no other apostrophe) | ok |
| el | Capitals lowered with a final sigma (ΣΚΥΛΟΣ → σκυλος; QuickJS's `toLowerCase` follows Unicode's final sigma rule), a second acute accent dropped (πρόσωπό → πρόσωπο), and Yomitan's one rule, ξανα- (ξαναρώτησε → ρώτησε, also without its accents, then the form row → ρωτάω) | `el.js` bundle | glue `greek capitals lower with a final sigma…`, I: LanguageLookupTest `greek…` | glue (πότε does not give ποτε) | ok |
| be | Ukrainian's stress mark and apostrophe processors added to Belarusian's descriptor, which has capitalization only (сям’я → сям'я, ва́да → вада); the build fails if Yomitan gives Belarusian more processors | `be.js` bundle, `scripts/yomitan-language/build.mjs` `addedPreprocessors` | glue `belarusian takes the ukrainian…`, I: LanguageLookupTest `belarusian…` | glue (ru gets no other apostrophe) | ok |
| all but ja | Yomitan's console messages (a text processor's variants cut at 4096) are dropped: QuickJS has no console, and the messages carry the looked-up text | `scripts/yomitan-language/console-stub.js` | glue `a text with more variants than yomitan keeps…`, I: LanguageLookupTest `serboCroatian…` | — | fixed |
| hi | Text composed as headwords are (NFC: क़ U+0958 → क + nukta; Bengali ে + া → ো) and candrabindu read as anusvara (हैँ → हैं): the app's own text processors, added to Hindi's descriptor, which has none | `hi.js` bundle, `scripts/yomitan-language/own-processors.js`, `build.mjs` `addedPreprocessors` | glue `hindi text is composed…`, I: LanguageLookupTest (U+0959 finds ख़ाना) | glue (en, ru, de, pl do neither; el, id and vi compose the text through their accent processors) | ok |
| bn | Hindi's descriptor under bn, with the text composed as headwords are (NFC: ে + া → ো; য় U+09DF → য + nukta, as NFC writes it) | `bn.js` bundle, `build.mjs` `borrowed`, `addedPreprocessors` | glue `bengali noun endings…` (ো typed apart, য় as one character), I: LanguageLookupTest `bengaliEndings…` | glue `bengali endings and untyped matches stay out of hindi` | ok |
| bn | Noun endings (classifiers টি টা খানা গুলো গুলি, plurals রা দের, cases র কে তে এ and their combinations) undone once, after at least two letters; the results match entries without a part of speech, which Wiktionary's Bengali entries never name | `scripts/yomitan-language/own-transforms.js`, `build.mjs` `addedTransforms`, `glue.js` `untypedRules`, `cpp/language/candidate_lookup.cpp` `fits` | glue `bengali noun endings…`, I: LanguageLookupTest `bengaliEndings…` | glue (one ending only: ছেলেদেরকে does not reach ছেলে; ভয় is not cut to ভ; hi has neither) | ok |
| ka | Yomitan's Georgian rules (13 case and plural endings cut, -გნ/-ნ → -გნი/-ნი, -გ → -გა, chaining), filed under `kat`: the glue takes each language's rules by the descriptor's iso; they match entries without a part of speech, which Wiktionary's Georgian entries never name, when three letters or more stay (owner, question 11 of the group 4b plan) | `glue.js` `prepare`, `untypedRules`; `cpp/language/candidate_lookup.cpp` `fits` | glue `georgian rules load…`, I: LanguageLookupTest `georgianRules…` | glue `georgian rules stay out of other languages`, the მას case (not cut to მა) in both tests | fixed |
| ka | Mtavruli capitals lowercased (ᲡᲐᲥᲐᲠᲗᲕᲔᲚᲝ → საქართველო): Yomitan's `decapitalize` added to Georgian's descriptor, which has no text processors | `ka.js` bundle, `build.mjs` `addedPreprocessors` | glue `georgian headings in mtavruli…`, I: LanguageLookupTest `georgianRules…` | — (every language with decapitalize lowercases Mtavruli as well, harmlessly) | ok |
| ka | Inflection names: Yomitan's identifiers (noun-adj-suffix-stripping) shown as declension, nominative, dropped vowel, in the 14 locales | `generate-inflections.mjs` `labels`, `transformsOf`; `res/values*/inflections_ka.xml` | the generator reports no missing translation | other languages keep Yomitan's names | ok |
| all (wty) | At import, a form row written with template leftovers is keyed by its words: a romanization or note after whitespace and `(` or `,` is cut off, two spellings split at a whitespace-led `/` are keyed both, whitespace before a combining mark is dropped (মুখ ে → মুখে), and a row of an unfilled template (`{{{3}}}ে`) is not keyed | `cpp/language/archive_split.cpp` `form_headwords` | I: LanguageLookupTest `bengaliEndings…` (করেন, করিস, করো) | every other LanguageLookupTest (well-formed rows keep their keys) | fixed |
| vi | Tone marks in the old and the new placement (hoà ↔ hòa) | `vi.js` bundle | glue `vietnamese tone marks…` | glue (fr, es, pt, it do not) | ok |
| zh | Kangxi radicals read as their ideographs (⽇本 → 日本) | `zh.js` bundle | glue, I: ChineseLookupTest | — | ok |

## Page scripts (popup and Anki notes)

| Language | Rule | Code | Positive tests | Negative tests | Status |
|---|---|---|---|---|---|
| ru | A reading that only adds stress replaces the headword (ко́шка); another reading goes over it | `popup/reading-ru.js:5` | reading.mjs, popup.mjs | reading.mjs `russian and chinese rules stay with their languages` (uk, be, bg, en, de) | ok |
| zh | Pinyin split into syllables, one over each character, with tone colors | `popup/reading-zh.js:5`, `popup/popup.js:383`, `:735` | reading.mjs, popup.mjs | reading.mjs (ja, vi, ko get no syllables) | ok |
| ja | Definition copy rules of Japanese dictionaries | `popup/definition-ja.js:5` | definition.mjs | definition.mjs `a language without rules of its own…` | ok |
| ja (default) | Furigana by Yomitan's Japanese distribution for every language without reading rules | `popup/note.js:86`, `render/render.js:570` | note.mjs, anki.test.mjs | — | limit |
| all | `{phonetic-transcriptions}`: a list of every transcription the dictionaries give, escaped, each with its dictionary tags before it in italics as Yomitan writes them (`<i>(UK, US)</i> /…/`); an English word has empty pitch markers, a Japanese pitch dictionary no transcriptions | `popup/note.js:365`, `api/Transcriptions.kt:27` | note.mjs `phonetic transcriptions list each one…`, TranscriptionsTest | the same tests (no tags, no brackets) | fixed |
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
| each OCR script | ML Kit model by script, Japanese and Chinese apart, Devanagari bundled and used for Bengali too; Armenian, Cyrillic, Georgian and Greek have none | `ocr/mlkit/MlKitOcrEngine.kt` `model`, `options` | MlKitOcrEngineTest (Hindi and Bengali are read) | MlKitOcrEngineTest (only the languages of scripts without a model are not read) | ok |
| ka | No ML Kit or Paddle model; Tesseract `kat`, the fast model recommended (22.9% CER over clean and hard screens against best's 22.8%, 2.5 MB against 4.5 MB) | `scripts/ocr-models/tessdata_catalog.py` `RECOMMENDED`, catalog `tesseract-fast-kat` | MlKitOcrEngineTest (Georgian has no model) | the other languages recommend no Tesseract model | ok |
| each language | OCR words are joined by the language's separator (empty for ja, zh) | `ocr/mlkit/MlKitOcrEngine.kt:87`, `ocr/tesseract/TesseractOcrEngine.kt:187` | TesseractPageTest, TextLayoutTest | — | ok |
| spaced, ko | App text puts the language's separator between lines a space or line break wraps | `overlay/capture/AccessibilityText.kt:139`, `overlay/OverlayController.kt:1092` | AccessibilityTextTest `a line wrapped at a space…` | AccessibilityTextTest (Japanese joins without one; a wrap inside a word joins) | fixed |
| all | App text keeps a combining mark typed apart (empty box in a text view) with its letter, in the letter's box | `overlay/capture/AccessibilityText.kt:158` | AccessibilityTextTest `a combining mark without a box stays with its letter` | AccessibilityTextTest (a mark after a character without a box, or at the start, is dropped) | fixed |
| spaced | Lens gives no separator after a word hyphenated at a line end; the language's separator is put there, so the break rules see it | `ocr/HyphenBreaks.kt:9`, `ocr/lens/LensOcrEngine.kt:76` | HyphenBreaksTest | HyphenBreaksTest (a dash of its own, a hyphen after a digit, a separator Lens gave, the last line, Japanese) | fixed |
| spaced | ML Kit keeps a line-end hyphen in the line's text but not in its last element; it is put back after the word, its box reaching the line's end | `ocr/HyphenBreaks.kt:38`, `ocr/mlkit/MlKitOcrEngine.kt:113` | HyphenBreaksTest `the hyphen ml kit leaves out…`, MlKitLineEndHyphenTest (instrumented, German) | HyphenBreaksTest (no hyphen in the text, a vertical line, a word that has it, a hyphen after a digit) | fixed |
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

Found while adding Greek:

20. The Lingua Libre search asked for the language's ISO 639-3 code after its Wikidata item (`LL-Q9129 (ell)-…`).
    Greek recordings carry `ell` (167), `gre` (130) or `mis` (84), so more than half were never found (μητέρα has one
    under `gre` only). The search now takes any three-letter code after the item, for every language; the other
    languages checked have one code each (fra, deu, nld, ces, msa, cmn, srp, bel, tgl), with 105 more French files and
    9 more Mandarin ones under other codes.

Found while splitting Croatian and Serbian:

21. Cloud recognition reads Serbian Јој (all three letters look Latin) as the Latin "Joj"; the look-alike fix needs a
    Cyrillic letter in the word, so the Cyrillic text showed the Latin entry joj. On a Cyrillic line such words are
    now written in Cyrillic (found on the emulator).
22. The Commons search for `Sr-вода` also matched `LL-Q7737 (rus)-Tatiana Kerbush-вода.wav`: its regular expression
    is not anchored to the title's start. Wiktionary files now count only when the whole title matches.

Found while adding Hindi:

23. Only non-spacing marks counted as part of a word. Devanagari vowel signs such as ि and ा are spacing marks, so a
    word aimed after one started there (कि|ताब looked up from ताब), and the word before the period of "डॉ."
    came out empty, so no abbreviation matched. Spacing and enclosing marks and the zero-width joiners now belong to the word in every spaced
    language.

Fixed afterwards from the limits below (2026-10-19, the owner asked which are worth fixing):

23. ML Kit leaves a line-final hyphen out of its words ("impor" / "tant"), so a hyphenated word was never joined with
    it. Its line text keeps the hyphen (checked on the emulator, German), so the hyphen is put back after the word.
24. Czech Roman ordinals ("Karel IV. byl") ended the sentence at their period. A Roman numeral in capitals before a
    lowercase word is now an ordinal in every ordinal language (cs, de, pl, lv, tr, hr, sr).
25. Compounds split greedily, the longest word first: Dutch stadsbestuurder gave stadsbestuur + der. The split now
    takes the fewest parts, then the longest shortest part (stads + bestuurder).
26. `{phonetic-transcriptions}` left out the transcriptions' tags, which Yomitan puts in italics before each one.

Found while adding Bengali:

27. Wiktionary exports write some form rows with leftovers of their templates, keyed under the whole text and so
    never found: in wty bn-en 5,972 of 53,646 form rows have a romanization after the form ("মাসটি (maśṭa
    (colloquial), maśṭi (formal))", "কলকাতাকে  / kolkatake"), 337 an ending set apart (মুখ ে; 90 such rows in
    hi-en), 78 an unfilled template ({{{3}}}ে); hy-en has 1,883 of 942,487 with a romanization or two spellings
    (ծագմանը / ծագմանն), ka-en 11, and 73 unfilled templates. They are now keyed by their words.

Found while adding Georgian:

28. Yomitan's Georgian descriptor files its rules under `kat`, not `ka`, and the glue looked rules up by the language
    code, so none loaded (Yomitan itself never applies them either). The glue now takes each language's rules by
    the descriptor's iso; the inflection generator takes the transforms in the language's folder, whatever code
    they name.
29. A rule that strips a whole word (Georgian -მა from მა) left an empty text, which was queried; the glue drops
    empty candidates in every language (glue `georgian rules load…`).

## Limits left as they are

- Bengali endings are undone once: ছেলেদেরকে finds ছেলেদের, not ছেলে. Verb forms come only from Wiktionary's form
  rows. The endings are tried on every word that ends like one (সাগর also queries সাগ); such a word shows only if a
  dictionary has it, after the full word.
- An ending also reaches the form rows under the word it leaves: বইটির shows বই first, then বু (of which বই is a
  spelling) and the verb বওয়া (of which বই is the first person present), as no part of speech rules them out.
- An ending set apart as য় (কলকাতা য়, 89 rows in wty bn-en) is not joined, since য় is a letter, not a mark; the -য়
  ending finds কলকাতা anyway, without the form's tag (locative).
- wty bn-pl writes romanizations as forms ("biṣ / bish" → বিষ); they are keyed as written, Latin text that Bengali
  text never matches.
- Georgian rules chain and are tried on every word that ends like an ending; with three letters left, some words
  also show a wrong neighbor after the right entry (არის, "is", also shows არი). Verbs come only from the form rows.

- `Compounds.word` stops at a combining mark in NFD text; OCR and app text give NFC, so a German compound typed in
  NFD is not split. Not worth the mapping it would need.
- Furigana of languages without reading rules goes through Yomitan's Japanese distribution, as Yomitan does;
  `{part-of-speech}` uses Japanese names, as Yomitan does.
- `∥` example lines are dropped from definition copies in every language (`popup/definition.js:320`): by design,
  ∥ separates an example from its translation wherever it appears.
- reading-ru rules are for Russian only. Ukrainian and Belarusian need none: their dictionaries give lemmas no
  reading, and the forms' stress is dropped from the key at import.
- wty uk-ru has some words twice, under ' and ’ (п'ятниця, п’ятниця): both are found, as two entries that look
  the same. Only 3 of its 99,684 headwords are such pairs; merging them would need an apostrophe rule in the grouping
  of every language.
- Ukrainian "див." (see) is also the genitive plural of диво, and "пор." (compare) of пора (a pore); a sentence ending in
  either does not end there.
- Paddle lines are paragraphs of their own, so a word hyphenated at a line end in PaddleOCR text is looked up as two
  halves; Lens takes separators from its response. Joining every word across a line break without a hyphen would
  find wrong words ("the" / "rapist").
- The frequency chip's initial follows the page language, not the dictionary title's (unknown): an English title in
  a Turkish profile gets İ.
- A word hyphenated at a line end is joined only when the second half is in lowercase; a real hyphen with a capital
  after it (Max- Planck) is tried with the hyphen only. A hyphen at a line end in Japanese, Chinese or Korean text
  is left alone.
- Wiktionary's s-forms (werkgevers, dorps, stads) are words of their own, so a compound split mostly takes the
  linking s with the first part.
- The compound split (fewest parts, then the longest shortest part) errs where a linking s could start the next
  word: urlaub + stag for Urlaubstag, geschlecht + steil; adverbs in -erweise (glücklich + erweise) and numerals
  (fünf + zehnmal) split wrong too, but dictionaries usually have them whole. Checked on 20,000 long lemma headwords
  of wty de-en and nl-en each, the headword hidden from the lookup: the rule changes 2% (de) and 4% (nl) of the
  longest-first splits; of 80 changed ones 47 got better and 26 worse in German, about 50 and 13 in Dutch (the worse
  Dutch ones mostly derived words where both splits are wrong: aanvaar + ding, aanvaard + ing). A wordfreq tie-break
  among splits with as many parts was worse: short frequent words win (middag + ten, dan + school, pijl + taart).
- A Roman ordinal before a capital ("Ludwig XIV. König von Frankreich") ends the sentence, as a year does.
- A word that is also a form of itself (kuća, genitive plural of kuća) shows the form's name beside the headword,
  as Yomitan does: the empty chain of the direct match is not shown, the form's chain is.
- Serbian text that mixes scripts within a line (a Latin name in a Cyrillic sentence) follows the line's majority:
  a Latin word made only of look-alikes there and not in capitals (Joe, Hey, copy, Oppo) becomes Cyrillic; other
  Latin words stay. The reverse: a Serbian word in capitals of look-alikes alone in running text (СССР read as CCCP)
  stays Latin. Both look the same on screen; only the copied text, the lookup and the translation differ.
- Serbian's model list has no ML Kit (the owner: Serbian is mostly Cyrillic; question 66): the Latin choice is
  PaddleOCR v6. Tesseract has srp only, as decided (no srp_latn).
- Greek π.Χ. (before Christ) lowercases to π.χ. (for example), so it never ends a sentence; μ.Χ. does.
- The Greek look-alike c → ς applies inside a word too, where a Latin c in Greek text is rare anyway.
- Only the tonos (U+0301) is dropped from Greek headwords: polytonic text (breathings, the grave and the circumflex)
  finds only headwords written the same way. Modern Greek dictionaries are monotonic.
- Greek text without tonos finds every headword that differs by it, as decided: ποτε shows ποτέ (never) and πότε
  (when). In wty el-en 7,921 tonos-free spellings stand for two or more headwords.

## Owner decisions in this review

Words hyphenated at a line end (owner: "try to join, since it may not be one word"): the lookup tries the joined
word and the word with its hyphen, and keeps the text as written when neither is found.

Furigana markers outside Japanese (the plan's decision row "Furigana markers"):

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
initial), LK37 (Ukrainian), LK38 (Belarusian), LK39 (Greek), LK42 (Hindi), LK43 (Bengali), LK44 (Georgian).

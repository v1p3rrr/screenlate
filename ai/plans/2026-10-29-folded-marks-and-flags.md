# Folded marks and language flags (plan of 2026-10-29)

Part of the languages work (`2026-10-10-languages-implementation.md`), the step between groups 5c and 5d. That plan's
decision rows "Marks folded in the lookup" (request 79) and "Language flags in more places" bind here; changes to them
are recorded there. This file holds the step's technical choices, data checks, questions and progress. Built on branch
`languages/marks`, started from `languages/uz` (Uzbek not merged yet) while the owner is away (autonomous mode).

## Goal

1. Every Latin- and Cyrillic-script language except Vietnamese finds a word with any marks over or under its letters:
   - typed or app text without marks finds the marked words (fuzfa → fűzfa, Lodz → Łódź);
   - OCR text with a wrong or extra mark finds them too (fúzfa → fűzfa).
   Results spelled as read come first.
2. Language flags next to the language names in the places the decision row lists, codes on e-ink.

## Technical choices (agent, provisional where marked)

| Topic | Choice |
|---|---|
| The fold | A word in NFD without every nonspacing mark that follows a Latin or Cyrillic letter, composed again (NFC). Letters with a stroke, bar, descender or hook that Unicode does not decompose map to their base letters by a table: Latin ø ł đ ħ ŧ ƀ ɨ ƶ ǥ and dotless ı; Cyrillic ғ ҕ ӷ қ ҝ ҟ ҡ ӄ ң ӈ ө ұ ү ҳ ҷ ҹ ӌ ҫ ҙ ҭ ԓ (with capitals). Letters of their own are left alone: æ œ ß þ ð ə ŋ, і ә һ ӏ, the Uzbek ʻ ʼ (modifier letters). Kana, Han and other scripts are untouched. Provisional: the table's letters beyond the ones the decision row names. |
| Languages | `LanguageSupport.foldsMarks`: true for the Latin- and Cyrillic-script languages (`ocrScript`), false for Vietnamese. One list in Kotlin; the bundle builder has its own list, and a test keeps them equal. |
| Index | At import, each such dictionary keys every headword and every form row also under its folded spelling, as a respelled key (sorted after the text as written), next to the keys the language had before (Greek-style `every_headword`, stress marks, replacements). Lemmas are stored as before. The marks JSON is given under every code the language's dictionaries name: Norwegian dictionaries name nb and nn, not no, so `Language.otherCodes` lists them and the engine keys the entry under each (before this, the Norwegian stress-mark keys never reached nb/nn dictionaries either). |
| Lookup | An OCR-only text processor, "Folded marks", in each such language's bundle: the scanned text plus its folded spelling when they differ. Typed and app text get no processor; their unmarked words reach the folded keys. |
| Earlier per-language variants | Removed: Hungarian ő ű for ö ü ú, Estonian õ for ö (the decision row). Kept: Welsh ŷ ŵ for ý û (Welsh keeps "twr finds only twr", `respelledOnlyWithoutEntry`, which would drop tŵr found through the fold), Azerbaijani ə ı for e i (ə is no marked e; question 17 of group 5c is open). The one-for-one `ocrWord` fixes stay. |
| Existing filters | Welsh and Scottish Gaelic keep `respelledOnlyWithoutEntry` (owner decisions of group 5b): there a word found as written shows no folded results. |
| Swamp check | Per language, on its main Wiktionary dictionary and frequency list: the share of running text found as written that also gets extra results, typed and after OCR, the average number of extras, and the gain (words found only through the fold). Provisional threshold for a question to the owner: more than 5% of running text with extras in typed text or more than 10% after OCR, or 3 or more extras on average. |
| Index growth | Counted per language (keys added against rows) and measured as file size for a few dictionaries on the emulator. |
| Flags | `LanguageChips` and the other listed places take the flag the profile chose (`flagCountry`), as the Languages screen does; on e-ink the code (`Flags.code`). TalkBack reads the name only: the flag is decoration. `ChipFlag` (the camera's chip flag) moves next to `LanguageFlag` and serves every place; 18 dp in chips and the OCR model list, 24 dp in titles, card headings and the first-run list. The first-run list shows each language's default flag (no profile exists yet); the cards of turned-off languages show one flag per language their files can turn on (two for Serbo-Croatian's leftovers). Disabled OCR model headings dim the flag as their text. |
| Known loss | Hungarian prefixes after a misread mark: the earlier variant turned OCR elöfordult into előfordult, which the prefix rule then cut to fordult. The folded text elofordult reaches előfordult only through its own folded key (a form row or headword), and the rules match elő- as written, so a prefix cut after a misread mark is lost. Accepted as the decision row's "rules match their endings as written". |

## Data check

Measured 2026-10-29 by a script in the agent's scratchpad (not in the repo): each language's main Wiktionary dictionary
(`wty-<code>-en`, every headword and form row) against the 30,000 most frequent words of a frequency list (wordfreq, or a
Leipzig corpus where wordfreq has none; Scottish Gaelic from the Gaelic Wikipedia text), weighted by frequency, so the
shares are of running text. The numbers are net of what the app already finds today (the language's own mark keys,
Yomitan's diacritics processor in it, la, ro, id, ms, tl, Russian ё → е, the Welsh and Gaelic filter). Inflection is not
simulated.

- **Noise**: share of running text whose word is found as written and also gets extra results through the fold, typed
  (or app text) and after OCR. **Extras per word**: how many other spellings such a word adds on average.
- **Gain**: share of running text whose word is not found as written but is found through the fold. Text typed without
  marks and marks that recognition misreads are not in frequency lists, so this measures the cost well and the gain
  poorly: the use the decision row is for (fuzfa typed, fúzfa read) is not in these numbers.
- **Extra keys / growth**: keys the import adds, and their share of the dictionary's rows (index size grows less: a key
  is one short row).
- **Most frequent extras**: the words that bring the most noise, with what they add (OCR: only after OCR, where a
  correctly read marked word also finds its unmarked or differently marked homographs).

| Code | Language | Noise typed / OCR % | Extras per word typed / OCR | Gain typed / OCR % | Extra keys | Growth % | Most frequent extras |
|---|---|---:|---:|---:|---:|---:|---|
| it | Italian | 42.1 / 44.1 | 1.40 / 1.38 | 0.09 / 0.09 | 582,423 | 47.6 | di → dì, dî; e → è, é; che → ché |
| pt | Portuguese | 35.6 / 41.2 | 1.99 / 1.93 | 0.37 / 0.40 | 170,213 | 29.7 | de → dê; a → á, ã, â, à; o → ó, ô, õ, ò |
| es | Spanish | 36.1 / 39.8 | 1.10 / 1.12 | 0.48 / 0.50 | 591,792 | 39.9 | de → dé; que → qué; el → él |
| ga | Irish | 33.0 / 38.7 | 1.00 / 1.01 | 0.23 / 0.38 | 168,152 | 63.2 | an → án; a → á; ar → ár |
| sq | Albanian | 13.7 / 35.3 | 1.04 / 1.18 | 0.71 / 1.11 | 136,860 | 62.2 | të → te (OCR); e → ë; në → ne, nê (OCR) |
| lv | Latvian | 18.7 / 34.7 | 1.08 / 1.15 | 0.15 / 0.41 | 130,344 | 66.0 | ir → īr; ar → ār; par → pār |
| fr | French | 27.3 / 33.1 | 1.18 / 1.23 | 0.23 / 0.24 | 184,344 | 37.3 | de → dé; la → là; le → lé |
| cs | Czech | 16.3 / 31.1 | 1.07 / 1.17 | 1.02 / 1.12 | 376,519 | 72.5 | a → á; je → jé; že → ze (OCR) |
| sv | Swedish | 13.8 / 28.9 | 1.23 / 1.33 | 0.07 / 0.08 | 139,785 | 35.7 | är → ar, år (OCR); att → ätt; på → pa (OCR) |
| hu | Hungarian | 22.1 / 28.0 | 1.55 / 1.48 | 1.11 / 1.27 | 1,082,489 | 79.1 | a → á, à; egy → égy; de → dé |
| pl | Polish | 16.1 / 26.2 | 1.40 / 1.30 | 0.51 / 0.56 | 1,054,704 | 62.3 | na → ną; się → sie (OCR); z → ƶ, ź, ż |
| no | Norwegian (Bokmål) | 15.3 / 20.0 | 1.12 / 1.21 | 0.08 / 0.10 | 13,063 | 11.7 | og → òg; å → a, à (OCR); som → søm |
| nn | Norwegian (Nynorsk, extra) | 14.4 / 19.6 | 1.54 / 1.43 | 0.96 / 1.15 | 13,652 | 12.5 | og → òg, óg; å → a (OCR); for → fòr, fór, før, fôr |
| da | Danish | 16.6 / 18.1 | 1.09 / 1.09 | 0.12 / 0.14 | 19,487 | 13.7 | og → øg; en → én; har → hår |
| sh | Serbo-Croatian | 12.9 / 15.4 | 1.81 / 1.69 | 0.08 / 0.10 | 170,104 | 17.2 | u → ȕ, ȗ; i → ȉ, ȋ; a → ȁ, ȃ |
| lt | Lithuanian | 0.1 / 11.5 | 2.00 / 1.83 | 0.00 / 0.22 | 2 | 0.0 | į → i̷, ɨ, i (OCR); yra → yrà, ỹra; tai → tái |
| tr | Turkish | 5.6 / 10.6 | 1.05 / 1.08 | 0.28 / 0.42 | 2,080,432 | 75.8 | çok → çök (OCR); o → ö; ama → âmâ |
| nl | Dutch | 9.2 / 9.3 | 1.00 / 1.00 | 0.02 / 0.06 | 3,297 | 1.3 | de → dé; een → één; of → óf |
| en | English | 8.1 / 8.1 | 3.87 / 3.87 | 0.01 / 0.01 | 10,152 | 0.6 | a → ã, à, â, ä, ā, ă, ȧ, ạ; i → ĩ, î, ī, ĭ, ị; is → ĩs |
| ru | Russian | 7.1 / 7.9 | 1.00 / 1.00 | 0.94 / 1.03 | 164,961 | 11.0 | и → й; я → я̈; все → всё |
| de | German | 6.1 / 7.6 | 1.02 / 1.02 | 0.03 / 0.04 | 127,624 | 21.2 | ein → éin; war → wär; oder → öder |
| sk | Slovak | 4.0 / 7.2 | 1.00 / 1.03 | 0.00 / 0.36 | 211 | 0.2 | a → ä, á, à; z → ž; s → š |
| fi | Finnish | 3.4 / 6.4 | 1.06 / 1.04 | 0.04 / 0.06 | 1,241,134 | 26.7 | tai → täi; vain → väin; tämä → tama (OCR) |
| be | Belarusian | 2.9 / 6.2 | 1.06 / 1.20 | 0.01 / 0.57 | 14,273 | 22.4 | ў → у (OCR); у → ў; гэта → гэ́та |
| az | Azerbaijani | 2.4 / 4.5 | 1.00 / 1.13 | 0.06 / 0.14 | 680,054 | 69.1 | üçün → ucun, uçun (OCR); o → ö; baş → bas (OCR) |
| et | Estonian | 2.8 / 4.5 | 1.01 / 1.01 | 0.11 / 0.15 | 71,630 | 28.8 | oli → õli; pole → põle; ole → õle |
| uk | Ukrainian | 2.4 / 2.7 | 1.00 / 1.00 | 0.03 / 0.03 | 56,563 | 12.6 | і → ї; й → и (OCR); и → й |
| eo | Esperanto | 0.0 / 2.5 | 0.00 / 1.01 | 0.00 / 0.06 | 1 | 0.0 | ankaŭ → ankau (OCR); aŭ → au (OCR); ĉi → ci (OCR) |
| sl | Slovenian | 0.7 / 1.8 | 3.18 / 2.43 | 0.00 / 0.05 | 142 | 0.2 | je → jȅ, jẹ́; se → sẹ̑, še; da → dá |
| la | Latin | 1.5 / 1.5 | 1.00 / 1.00 | 0.00 / 0.00 | 27 | 0.0 | a → aͣ; fuit → fu͛it; e → ę |
| ky | Kyrgyz | 0.9 / 1.3 | 1.00 / 1.01 | 0.07 / 0.38 | 12,907 | 22.9 | болуп → бөлүп; болгон → бөлгөн; кол → көл |
| kk | Kazakh | 0.1 / 1.2 | 1.46 / 1.13 | 0.02 / 0.11 | 22,620 | 25.8 | мен → мең; қазақ → казак (OCR); сөз → соз (OCR) |
| ms | Malay | 0.5 / 0.5 | 1.00 / 1.00 | 0.00 / 0.00 | 110 | 0.2 | dua → duă; tiga → tigă; a → ă |
| tl | Filipino | 0.4 / 0.4 | 1.00 / 1.00 | 0.00 / 0.00 | 103 | 0.1 | ano → año; e → ë; tanong → tañong |
| id | Indonesian | 0.1 / 0.1 | 1.00 / 1.00 | 0.00 / 0.00 | 44 | 0.1 | a → à; à → a (OCR) |
| ro | Romanian | 0.1 / 0.1 | 1.00 / 1.00 | 0.00 / 0.00 | 17 | 0.0 | în → in (OCR); și → si (OCR); a → â, ă |
| gd | Scottish Gaelic | 0.0 / 0.0 | 0.00 / 0.00 | 0.00 / 0.23 | 0 | 0.0 | a → à, á; na → nà; air → àir |
| sw | Swahili | 0.0 / 0.0 | 0.00 / 0.00 | 0.00 / 0.00 | 1 | 0.0 |  |
| uz | Uzbek | 0.0 / 0.0 | 0.00 / 0.00 | 0.00 / 0.00 | 305 | 0.2 |  |
| cy | Welsh | 0.0 / 0.0 | 0.00 / 0.00 | 0.00 / 0.03 | 3 | 0.0 | yn → ŷn; y → ỳ, ý, ÿ, ŷ, ẏ; a → â, à, á, ä |

Read: in Italian, Portuguese, Spanish, Irish and French a third or more of running text gets one or two extra results
(the short function words: de → dé, e → è, a → á), after the results spelled as read. English gets few extras, but its
commonest word gets eight (a → ã, à, â, ä, ā, ă, ȧ, ạ, letter entries). Welsh and Gaelic get none: their filter shows
folded results only for a word without an entry. Every language with real noise is past the provisional threshold, so
the question below goes to the owner. Until the answer, the fold works as the decision row says, extras after the words
as written.

## Questions

1. **Extra results through the fold (provisional: kept as decided).** The fold works: a word typed without marks finds
   the marked words, a mark recognition misreads finds the right word. The cost, measured above: in many languages the
   short common words get one or two extra results below their own, because a marked word of the same letters exists
   (Spanish de → dé, que → qué, el → él; Italian e → è, é; Portuguese a → á, ã, â, à; French la → là; Hungarian a → á;
   Swedish att → ätt; Russian и → й; English a → eight letters with marks). In Italian, Portuguese, Spanish, Irish and
   French a third or more of running text shows them; in Albanian, Latvian, Czech, Swedish, Hungarian, Polish,
   Norwegian, Danish and Serbo-Croatian 13-35%; in the rest under 11%. Options:
   - a) keep: the extras come after the words as written (now; cheap either way);
   - b) show folded results only when the word as written has no entry, as Welsh and Gaelic do now, in every language:
     no noise, fuzfa and fúzfa still find fűzfa, but a word that has an entry as written finds no marked word (typed ar
     finds no år, OCR öt misread for őt finds only öt);
   - c) b) only in the languages past a threshold (say more than 10% typed noise: it, pt, es, ga, fr, hu, lv, da, cs,
     pl, no, sq, sv, sh), a) in the rest;
   - d) leave the fold out where it finds nothing new and only adds extras: English, Dutch, Indonesian, Malay, Filipino,
     Latin, Swahili (gain 0.02% or less).
2. **Word forms without the dictionary's headwords (not built; the coordinating session's follow-up to Uzbek).** The
   "Word forms" item imports only the English Wiktionary's form rows, not its headwords. In Uzbek (and Azerbaijani),
   ending rules show their results only when the word has no entry of its own; with uz–ru and the word forms alone,
   Davom finds davo with the ending "my", because uz–ru has no davom and the forms import does not know davom is a
   word. Options:
   - a) the forms import keeps a list of the dictionary's headwords, and a rule result is left out when the word is one
     of them, though nothing is shown for it (Davom then shows nothing with uz–ru alone, instead of davo "my");
   - b) keep as is (davo "my" shows; with Wiktionary into English installed, davom shows its entry).
   a) changes what the popup shows and needs a headword list in the forms import and in the filter, so it waits for the
   owner.

## Progress

- 2026-10-29: branch `languages/marks` from `languages/uz` 81654777; plan written; data check started.
- 2026-10-29: the fold in the engine (`folded`, `MarkStripper::folded_keys`, `fold_marks` in the marks JSON, nb and nn
  keys) and in 39 bundles (`foldedMarks`, OCR only); the Hungarian and Estonian variants removed; glue, unit and
  instrumented tests; docs and the language rules note. Data check done, question 1 written.
- 2026-10-29: flags in LanguageChips, the OCR model list, the download screen's title, the cards of turned-off languages
  and the first-run list; `ChipFlag` shared.

## Changelog

- 2026-10-29: plan written (autonomous mode).
- 2026-10-29: data check filled in, question 1 (extra results) added, the nb/nn keying, the flag sizes and the Hungarian
  prefix loss recorded.

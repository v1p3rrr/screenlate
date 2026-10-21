# Group 6 research: Catalan, Galician, Icelandic, Afrikaans, Tatar, Bashkir, Sanskrit (plan of 2026-10-20)

Part of the languages work (`2026-10-10-languages-implementation.md`, request 70). That plan's decision rows bind here;
the ones this plan builds on are listed below by name, and changes to them are recorded there. This file holds the
research, the proposed blocks, the technical choices and the questions; question numbers are its own. Written by an
autonomous research session while the owner was away: no question was asked, so every question is under "Questions
for the owner (deferred)", and nothing is built until the owner decides the order (research only, no app code).

## Goal

Seven languages, each looked up, read by OCR, voiced and exported as the earlier groups are:

- Catalan (ca) and Galician (gl): Romance languages, mostly covered by Wiktionary's form tables; Catalan's elisions
  and l·l, Galician's pronouns joined to the verb and a Spanish (or Portuguese) fallback for its smaller dictionary.
- Icelandic (is): full noun and adjective tables in Wiktionary, verbs from the Icelandic morphology database (BÍN);
  þ ð æ, compounds.
- Afrikaans (af): a small Wiktionary dictionary helped by the user's Dutch dictionaries; the article 'n written with
  seven apostrophes.
- Tatar (tt) and Bashkir (ba): Kazakh's design of group 4e (own ending rules, letter keys, Paddle `cyrillic`,
  Leipzig frequency); Tatar has no wty dictionary yet.
- Sanskrit (sa): Devanagari as Hindi; words joined by sandhi need rules of their own; Latin transliteration.

## Scope

Request 70 (owner, 2026-10-20): Catalan, Galician, Tatar with Bashkir, Icelandic, Afrikaans and Sanskrit; for Sanskrit,
how to split words joined by sandhi and how to read Latin transliteration. Already excluded by the owner and not
reopened: Toki Pona, Ancient Greek, Old Irish, Manx, Cornish, Hawaiian, Breton and, for now, Lao.

### Deferred languages, with reasons

| Language(s) | Reason |
|---|---|
| Taiwanese Hokkien (nan) | Taiwan's Mandarin is already covered by Chinese (Traditional script and the Chinese profiles); Hokkien is rarely written (two romanizations, Pe̍h-ōe-jī and Tâi-lô, and Han characters without a settled set), so there is little text on screens to look up. |
| Russia's other languages (Chechen, Yakut, Ossetian, Chuvash and others) | Their Wiktionary dictionaries are under 5,000 senses each (kaikki, English / Russian editions): Yakut 4,912 / 4,606, Ossetian 1,105 / 4,556, Chechen 1,341 / 3,875, Crimean Tatar – / 3,636, Chuvash 2,001 / 2,361, Kalmyk 832 / 2,276, Moksha 2,830 / 2,210, Ingush – / 2,126, Udmurt 2,042 / 1,703, Eastern Mari 2,445 / 1,636, Kumyk – / 1,678, Erzya 1,426 / 1,418, Buryat 677 / 1,338, Komi-Zyrian 1,646 / 1,192, Avar 643 / 997. A sense is not a word, so each yields at most a few thousand words; only Yakut has wty pairs that pass row "Catalog threshold" (sah-en 3,076 words, sah-ru 3,991), still a small dictionary. |
| The rest of the long candidate list (Punjabi, Gujarati, Malayalam, Amharic, Hausa, Yoruba, Burmese, Javanese and so on) | Not in request 70; most need script work of their own (Gurmukhi, Gujarati, Malayalam, Ethiopic, Myanmar) or have small dictionaries. They wait for a later group. |

## Research (2026-10-20)

Measured by the research session's helpers on the PC (scripts and downloads in the session scratchpad, removed with it;
nothing written to `testdata/`), looked up on source pages, or estimated where marked. Common sources: Yomitan e7334a9
(`ext/js/language/`, GPL-3.0, read for facts only); the wty tree on Hugging Face (`daxida/wty-release`, `latest/dict`,
release 2026.10.11, listed and downloaded 2026-10-20, CC BY-SA 4.0; MB of 10^6 bytes; words counted by `countWords`
of `scripts/catalog/generate.mjs`, ✗ under 1,500, – no file); wordfreq 912caf6; the Leipzig Corpora Collection
(downloads CC BY); the tessdata pins of `tessdata_catalog.py`; PaddleOCR v5 and PP-OCRv6 small from
`testdata/ocr/compare/models` and `models-v6`; circle-flags 379588b; JDK 21 (CLDR). Nothing in this plan was measured
on a device: the emulator was not used.

### Recognition (Latin-script languages)

- ML Kit's Latin model supports Catalan, Icelandic and Afrikaans and lists Galician as experimental (official page).
- tessdata (fast / best, MB): cat 1.15 / 3.80, glg 2.55 / 12.71, isl 2.28 / 9.49, afr 2.65 / 12.80.
- Paddle v5 latin (772 entries) and PP-OCRv6 small (18,708) have every letter of the four alphabets in both cases,
  including U+00B7, ŀ Ŀ, þ ð Þ Ð, æ Æ and ŉ; v5 lacks the en dash, v6 lacks „ ‚ ‹ › (so it never reads Icelandic's
  opening „).
- Paddle on the PC, screens rebuilt after group 5a's layout (1280 × 2856, 300-340 characters of own text per
  language): clean Noto Sans and Noto Serif at 72 px; hard grey Arimo (metric-compatible with Arial) at 34 px on
  white and white Arimo at 64 px outlined on a gradient. v5 latin on ONNX Runtime (thresholds 0.3 / 0.6), v6 small on
  MNN 3.6.1 (the app's 0.2 / 0.45), with a copy of `paddle_ref.py` given `--thresh` and `--box-thresh` (as group
  5a's copy). Wrong characters after folding quotes and apostrophes (engines write ' for ’ and " for “ ”), clean /
  hard / both:

| lang | Paddle v5 latin | Paddle v6 | ML Kit, Tesseract, cloud |
|---|---|---|---|
| ca | 0.3 / 0.5 / 0.4 | 0.5 / 0.3 / 0.4 | measured at implementation (emulator) |
| gl | 0.3 / 0.0 / 0.2 | 0.5 / 0.0 / 0.2 | at implementation |
| is | 2.0 / 1.0 / 1.5 | 1.3 / 1.7 / 1.5 | at implementation |
| af | 0.1 / 0.1 / 0.1 | 0.1 / 0.0 / 0.1 | at implementation |

- Misreadings over 8 runs per language (4 screens × 2 models):
  - Catalan: l·l (two per screen) right in 12 of 16; v5 dropped the dot once (collegi) and read it as a hyphen
    once, v6 read both as hyphens on the outlined screen (col-legi, il-lusió); never as a period or ŀ. ï → i in 2
    runs; Ò → ò once.
  - Galician: no letter errors (the short word "zoo" read as ZOO or OO).
  - Icelandic: Ð → Đ (U+0110) in all 8 runs; Þ → P in all 4 v5 runs (v6 right); ð → ž (5) or š (2) in v5 on the clean
    screens (6 of 30); Æ → AE in all 4 v6 runs, → E once in v5; Ý → Y once; Í → í twice (v6, hard screens); „ dropped
    by v6 every time.
  - Afrikaans: ê ô û î ë é è á always right; ï → i twice; ’n always read as 'n with an ASCII apostrophe, the space
    before it lost once (wag'n).

### Audio and voices

Twenty common words per language (water, house, dog, cat, bread, mother, father, day, night, sun, book, hand, eye,
tree, fish, child, man, woman, good, red), searched with the app's title patterns (`AudioPages.kt`), 2 s between
requests, a User-Agent with a contact URL:

| lang | Lingua Libre item | Lingua Libre | Wiktionary | either | Lingua Libre files / speakers | Commons prefix files |
|---|---|---|---|---|---|---|
| ca | Q7026 (cat) | 20 | 0 | 20 | 22,918 / 19 | `Ca-` 167 |
| gl | Q9307 (glg) | 0 | 5 (`Gl-`) | 5 | 562 / 6 (510 by one speaker, mostly place names) | `Gl-` 221 |
| is | Q294 (isl) | 12 | 7 (`Is-`) | 19 | 375 / 6 | `Is-` 297 |
| af | Q14196 (afr) | 20 | 0 | 20 | 2,961 / 4 | `Af-` 58 (51 `Af-za-`) |
| tt | Q25285 (tat) | 7 | 1 | 8 | 417 / 3 | `Tt-` 17 |
| ba | Q13389 (bak) | 0 | 7 (`Ba-`) | 7 | 1 / 1 | `Ba-` 98 Bashkir among unrelated titles |
| sa | Q11059 (san) | 2 | 0 | 2 | 105 / about 5 | `Sa-` 90, mostly letters |

- Catalan `Ca-` files are mostly phrases (mp3) with tags or, occ, val, ct; the app reads only two-letter tags as
  regions, so `occ` would be missed (none of the twenty words).
- Text to speech: Google's Android engine (TalkBack's list) has Catalan and Icelandic only; Google Cloud has one
  Standard voice each for af-ZA, ca-ES, gl-ES and is-IS, so af and gl voices may be on the device engine too (checked
  against the emulator's voice list at implementation); none for tt, ba, sa. Alternatives the user may install:
  eSpeak NG (ca, is, af, tt, ba; not gl, sa), RHVoice (Tatar), Piper (ca, is).

### Fonts (Latin-script languages)

fontTools on the catalog's files: Lexend and Literata have every letter; Inter lacks ŉ; Atkinson Hyperlegible Next,
PT Sans and PT Serif lack ŀ Ŀ and ŉ. U+00B7, þ ð Þ Ð, æ and the other letters are in every font. ŀ is a compatibility
character (NFKD l + ·) that running text does not use, and ŉ is deprecated since Unicode 5.2 (Afrikaans writes ’n), so
neither belongs in a language's letter list (row "Font lists across languages"). Android's system fonts have them all.
Cyrillic and Devanagari fonts are under Tatar and Bashkir and under Sanskrit.

### Translation, popularity, flags

- `TranslationLanguageTable`: ca, gl, is, af, tt, ba with the same code at both services; sa at Google only (it goes
  to Google in the cascade).
- Popularity (speakers rounded; Duolingo courses; Wikipedia articles; share of Common Crawl CC-MAIN-2026-39):

| lang | speakers | Duolingo | Wikipedia | Common Crawl |
|---|---|---|---|---|
| ca | 4.1 M first language, 9-10 M can speak it | from Spanish only | 806,463 | 0.198% |
| gl | about 2.4 M | no | 242,626 | 0.032% |
| is | about 0.33-0.39 M | no | 60,574 | 0.035% |
| af | 7.2 M first, about 10 M second language | no | 136,997 | 0.008% |
| tt | 3.26 M (2021 census) | no | 709,222 (mostly bot stubs) | 0.008% |
| ba | 1.1-1.2 M | no | 64,307 | 0.003% |
| sa | 24,821 first language (2011); a school subject in much of India | no | 12,585 | 0.003% |

  Likely learner order: Catalan far ahead, then Afrikaans, Galician and Icelandic, then Sanskrit, Tatar, Bashkir.
- Flags (circle-flags 379588b, SVG → drawable bytes): Catalonia es-ct 443 → 785, Andorra ad 844 → 1,285, Valencia
  es-vc 366 → 742, Balearic Islands es-ib 671 → 1,084, Galicia es-ga 432 → 845, Iceland is 592 → 968, South Africa za
  542 → 1,023, Namibia na 632 → 1,076, Tatarstan ru-ta 330 → 709, Bashkortostan ru-ba 380 → 817, Nepal np 735 →
  1,148; India `in` is in the app. `language/ca`, `gl`, `is`, `af`, `tt`, `ba` are git symlinks (12-byte text files)
  to es-ct, es-ga, is, za, ru-ta, ru-ba; there is no `language/sa`. `flags/tt.svg` is Trinidad and Tobago and
  `flags/ba.svg` Bosnia and Herzegovina. `circle_flags.py` names files `flag_<country>.xml`, so a hyphenated code needs
  an underscore name (`flag_es_ct`).
### Catalan and Galician

Sources: Yomitan e7334a9 (transform counts from the repo's own bundles in a node `vm`, as `glue.test.mjs` loads them);
wty `latest` (release 2026.10.11, downloaded 2026-10-20); wordfreq 912caf6 (`large_ca`); Leipzig
`glg_newscrawl_2016_100K`, `glg_wikipedia_2021_300K`, `cat_news_2022_100K` (CC BY); WikDict `cat-rus.tei`
(2025-11-21); Softcatalà's GitHub repositories; FreeDict's database JSON; five en.wiktionary raw pages; JDK 21.

#### Yomitan

| lang | descriptor | text processors | transforms |
|---|---|---|---|
| ca, gl | none | – | – |
| es | `es`, `spa` | capitalization | 8 conditions, 15 transforms, 545 rules (no enclitic rules: its "pronoun substitution" maps -arme, -arte, -arnos to -arse) |
| pt | `pt`, `por` | capitalization | none |
| fr | `fr`, `fra` | capitalization, `apostropheVariants` (' ↔ ’) | 9 transforms, 2,672 rules |

- wty's ca and gl rows have an empty rules field (all 280,489 ca-en rows, all 276,020 gl-en rows, every other pair);
  the part of speech is only in the definition tags. wty seems to fill the field only for languages Yomitan
  describes (eo and sq had it). So Spanish's rules would find nothing here (`candidate_lookup.cpp` `fits` passes an
  entry without rules only for an untyped candidate), and `build.mjs` refuses to borrow a source with
  `languageTransforms` anyway.
- So both build from Portuguese's descriptor (capitalization only) under their own codes (`cat`, `glg`), Catalan with
  French's `apostropheVariants` through `addedPreprocessors`: Catalan news text has 55,296 ' and 39,248 ’ between
  letters, ca-en headwords use ' (2,431 against 13).
- Plain inflection is in the dictionaries already: ca-en has 245,220 form rows, gl-en 255,490.

#### Dictionaries (wty)

| from | en | ru | pl | zh | fr | it | tr | ko | ja | pt | es | vi | de | ipa |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ca | 4.64 31.6k | 0.41 8.4k | 0.26 7.4k | 1.09 6.9k | 0.89 17.3k | 544 ✗ | 861 ✗ | – | 0.25 5.5k | 0.13 2.7k | 0.17 2.8k | 272 ✗ | 0.41 5.1k | 1.29 52.0k |
| gl | 3.80 18.6k | 0.30 5.9k | 0.11 2.7k | 0.53 2.4k | 0.11 2.1k | 272 ✗ | 561 ✗ | 520 ✗ | 0.14 3.1k | 2.14 27.1k | 0.17 2.2k | 90 ✗ | – | 0.29 15.2k |

- No own-language edition. Galician's largest dictionary is gl-pt (27.1k words, from Portuguese Wiktionary). No pair
  is over `COUNTED_BELOW` or near the line.
- Rows / form rows / lemma headwords: ca-en 280,489 / 245,220 / 31,554; gl-en 276,020 / 255,490 / 18,641. kaikki's
  228k and 249k "senses" are these rows: both dictionaries are mostly form rows (plurals and verb tables; Galician's
  include the personal infinitive).
- Galician spelling norms in gl-en: 1,182 lemma headwords (6.3%) are reintegrationist only (braço, processo,
  instituição, memorizaçom); 14,740 form rows (5.8%) belong to them; 8,496 headwords have an alternative-form row,
  2,155 of them Portuguese-like (jantar → xantar, indústria → industria), the rest dialect and older spellings (antre
  → entre). gl-en is about 94% in the official ILG-RAG norm; its size comes from 255k form rows of 4,297 verb lemmas
  and noun plurals. gl-pt: 22.9% of its lemma headwords carry Portuguese-like letters.
- Catalan variants in ca-en: 1,117 form headwords tagged Valencia (huit → vuit, conéixer → conèixer, meua → meva,
  vosté → vostè), 84 Balearic. Of 52 typical Valencian words 13 are missing (hui, prompte, isc, vullc, vosatros; the
  first person in -e: parle, cante, pense; the imperfect subjunctive in -era: tinguera, poguera); of 25 Balearic words
  3 (cantam, cantau, deim).
- Entries wty drops (checked against en.wiktionary raw text; their senses are form-of templates with two targets):
  Catalan del, al, pel (de/a/per + el; dels and pels exist only as form rows of the missing del and pel), ben
  ("alternative form of bé"; wty has bé → ben pointing at a missing entry); Galician moi ("apocopic form of moito").
  del, al, dels, pel, ben are 2.59% of the tokens of `cat_news_2022_100K` (del 1.27%); the prefix scan then shows "de",
  "a" or "be" (sheep). Galician "empresa" has no Galician section in Wiktionary itself.

#### Other dictionaries

| dictionary | pair | size | license | notes |
|---|---|---|---|---|
| WikDict `cat-rus` (2025-11-21) | ca→ru | 10,390 entries | CC BY-SA 3.0 | from translation tables; FreeDict imports the same data; no Galician pair in WikDict or FreeDict |
| Softcatalà diccionari anglès-català | en→ca, reversible | 68,726 entries, 33,392 single Catalan words | data CC BY 4.0, server GPL-2.0 | adds to ca-en 4 of the top 1,000, 28 of the top 5,000, 397 of the top 20,000 |
| Softcatalà sinonims-cat | ca synonyms | 42,000 words | data CC BY 4.0 | monolingual synonyms |
| Softcatalà catalan-dict-tools | ca forms lexicon | | GPL-2.0+ / LGPL-2.1+ | GPL modules only |
| Apertium (cat, glg and their pairs) | | | GPL-2.0 / 3.0 | MT lexicons without definitions |
| Dicionario da RAG, Digalego | gl | | not open (the RAG's legal notice forbids reproduction without authorization; Digalego commercial) | not usable |

Russian coverage (lemma or form found): Catalan top 1,000 wty ca-ru 417, WikDict 889, both 895; top 5,000 1,842,
4,001, 4,144. Galician gl-ru: 330 and 1,315 (no other Russian source).

#### Frequency

wordfreq `large_ca` (185,353 words); it splits at hyphens and off proclitic elisions, keeps enclitic apostrophes
(1,098 words: se'n, veure'l), has 1,341 words with the middle dot, 95 with l.l, 4 with ŀl. No Galician list.
Leipzig Galician corpora: `glg_wikipedia_2012_100K` 23.3 MB, `_2016_100K` 23.5, `_2016_300K` 71.6, `_2021_100K`
22.1, `_2021_300K` 67.3 (284,371 types, 6.28M tokens), `glg_newscrawl_2016_100K` 26.6 (113,366 types, 2.52M tokens);
no other Galician corpus (HEAD checks for 2010-2024 names; Leipzig's corpus API lists none).

#### Lookup coverage

| lang | dictionaries | top 1,000 | top 5,000 | top 20,000 |
|---|---|---|---|---|
| ca | ca-en | 98.4% | 95.7% | 84.1% |
| ca | ca-ru | 29.3% | 21.6% | 13.7% |
| gl | gl-en | 90.4% | 77.1% | 61.0% |
| gl | gl-en + gl-ru | 91.3% | 78.9% | 63.2% |
| gl | gl-en + gl-pt | 91.2% | 78.4% | 62.7% |
| gl | gl-en + pt-en (11.34 MB) | 96.7% | 90.2% | 79.6% |
| gl | gl-en + es-en (21.77 MB) | 97.7% | 93.4% | 87.1% |
| gl | gl-en + gl-pt + pt-en + es-en | 98.4% | 96.0% | 90.8% |

(Galician top words from the two Leipzig corpora summed, lowercase only.)

- Catalan misses of the top 5,000 (216): the dropped contractions; enclitic apostrophe forms (se'n, se'ls, me'n);
  English words and names; abbreviations (núm, sr, km) and ordinals (1r, 2n); hui.
- Galician misses (1,143): words missing from gl-en (moi, varios, final, empresa, serie, campaña, éxito, xornada);
  contractions with demonstratives (desta, neste, nese, diso, tódolos); enclitic forms (converteuse, atópase, fíxose,
  darlle, facelo).
- Spanish finds more Galician than Portuguese does, because the official norm spells like Spanish (x, ñ, ll, -ción):
  the extra words are mostly cognates with the same meaning (campaña, comisión, reunión, éxito); false friends seen:
  desa (Galician de + esa, a Spanish verb form), facelo (archaic Spanish facer). For reintegrationist text it would be
  the reverse; not measured (no such corpus).

Catalan running text (`cat_news_2022_100K`, 2,114,909 tokens; a token is a run of letters with ' ’ - · between
letters):

| kind | share of tokens | result today or with the rule |
|---|---|---|
| proclitic elision (l' d' s' m' t' n' + word) | 4.37% | today the lookup starts at "l" and finds the headword "l'"; with the French elision rule the word after the apostrophe is found for 4.08% of tokens; 0.26% are names and new words; 877 tokens are headwords whole (d'acord, d'hora) |
| hyphen inside (donar-li, fer-se; nord-americà) | 0.48% | whole headword 0.06%; the host found by the prefix 0.38%; neither 0.04% (names) |
| apostrophe inside (se'ls, veure'l) | 0.08% | host found 1,481 of 1,652 |
| middle dot (col·lecció) | 0.24% | whole headword 4,485 of 5,160; the second half alone is a headword in 1,025 (lecció, legal, la), a wrong result when aimed there today |
| lowercase not found | 3.64% | del, al, dels, pel, ben 2.6%; covid, km, rare words |

- Enclitics after a hyphen or apostrophe need no rule: the word start keeps them with the verb and the prefix scan
  reaches it (ca-en even lists abstenir-se, acostuma't).

Galician running text (`glg_newscrawl_2016_100K`, 2,175,605 tokens): found whole in gl-en 83.71%; capitalized not
found 5.09%; lowercase not found 10.06%; contractions d/n + demonstrative 0.20% (18 types); reached by an enclitic
split 0.75-0.94% (below).

Galician enclitic split, an own list written from the grammar: strips me te che se lle lles nos vos o a os as and the
combined llo lla llos llas mo ma cho cha nolo volo llelo (and their plurals); undoes -lo -la -los -las after a host
that lost -r or -s (facelo → facer, fixémolo → fixemos); -no -na -nos -nas after -u or -i (deuno → deu); up to two
pronouns (dándolle); the host tried as written and without its acute (fíxose → fixo, atópase → atopa), and it must be
a verb (a `v` lemma row or a form row of one, read from the definition tags).

| variant | tokens reached | wrong |
|---|---|---|
| A: all endings | 0.94% | many: crise → cri + se (768 tokens), debate → deba + te (568), premio, lema, crime, dirixente cut as -n + te |
| B: no "te" after n; o a os as only after a vowel | 0.85% | crise, debate, premio still wrong (crise and debate alone 7% of the hit tokens) |
| C: B, and words that are a noun, adjective or name lemma in es-en or pt-en are not split | 0.75% (5,652 types) | about 6% of hits: in the 320 most frequent hit types 616 of 6,999 tokens (8.8%: premios → premi, estrea → estre, revela → rever + la, lemas → le + mas, compromete, comete); about 4% of a random 80 of the rest |

- Right with C: converteuse, atópase, fíxose, darlle, facelo, pódese, dálle, produciuse (about 0.7% of all tokens).
  Future and conditional forms with the pronoun inside reach the right verb but the wrong tense (celebrarase →
  celebrara). C needs Spanish and Portuguese headword lists as its guard (a few hundred KB, built from wty at catalog
  time).
- Hyphenated pronouns of the pre-2003 norm (elimina-lo) are 0.009% of tokens, the old d'a, n'a about 80 tokens: no
  rule.

#### Text

Catalan l·l:
- ca-en writes it with U+00B7 in 2,836 headwords (4 with l-l, 2 L·L, none with ŀ U+0140, U+2027, U+0387, U+2022 or
  l.l). In running text: wordfreq · 1,341 words, l.l 95, ŀl 4; the news corpus · 5,157 times, • 10, l.l 5, l-l 5,
  ŀl 1.
- Today `·` (category Po) ends a word: aimed at "lecció" in "col·lecció" the lookup finds "lecció" (lesson), for 20%
  of dot words; aimed at "col" the prefix scan reaches col·lecció.
- Abbreviations with the dot (Il·lm., Il·lma.): `wordBefore` reads letters and marks only, so "Il·lm." is read as "lm".
- ŀ Ŀ become l· under NFKC; U+0387 becomes U+00B7 under NFC.
- 65 ca-en dot headwords share their letters with a dotless one (novel·la/novella, cel·la/cella, col·la/colla,
  vil·la/villa, al·là/allà); in wordfreq's top 50,000 nine such pairs are both frequent (al·là 22,858, allà 630).
- OCR (PC run below): Paddle read l·l right 12 times of 16; it dropped the dot once (collegi) and read it as a hyphen
  three times (col-legi, il-lusió); never as a period or ŀ.

Apostrophes and hyphens:
- Catalan elisions l' d' s' m' t' n' before a vowel or h: the French elision rule with that list makes the word after
  the apostrophe start a new word. rock'n'roll stays whole (the letter between two apostrophes); in "dóna-m'ho" the m
  after a hyphen counts as an elision, so "ho" is found when aimed there; enclitics after a vowel ('l 'm 'n 's 't:
  porta'l, digue'm) are not elisions and stay with the verb.
- Galician: the official norm has no elisions; pronouns are written joined to the verb (dixéronllo, fíxoo); the
  article contractions do da ao á co coa polo pola no na nun nunha dun dunha cun cunha are all gl-en headwords
  ("ao" as form rows only); the demonstrative contractions (desta, neste, nese, diso, naquel) are not, and splitting
  d- or n- before este ese aquel isto iso aquilo outro reaches them; tódolos, ámbolos missing.

Quotes, ordinals, abbreviations:
- Quotes: Catalan «…», “…” inside, ‘…’ third (IEC); Galician «…» and “…”, ‘…’ inside: `quotePairs` + « », as
  Spanish has.
- Ordinals: Catalan 1r, 2n, 3r, 4t, 5è, 1a (no period); Galician 1.º, 2.ª, n.º (the period is followed by º or ª,
  letters, so `endsSentence` already keeps the sentence going). No `ordinalPeriod`.
- Abbreviations that keep a sentence going ("etc." left out; lists to finish at implementation):
  - ca: Sr., Sra., Srta., Srs., Dr., Dra., Mn., St., Sta., Il·lm., Il·lma., Excm., Excma.; p. ex., núm., pàg., pàgs.,
    vol., cap., art., fig., cf., ed., op. cit., ib., vs.; tel., av., pl.; aprox., s. ("s. XX"); a.C., d.C.; months
    gen., febr., abr., jul., ag., set., oct., nov., des.
  - gl: Sr., Sra., Srta., D., Dna., Dr., Dra., Excmo., Excma., Ilmo., Ilma., Sto., Sta., S.; p. ex., núm., n.º, páx.,
    páxs., vol., cap., art., fig., cf., ed., op. cit.; tel., av., pr.; aprox., s., a. C., d. C. (paired letters);
    months xan., feb., abr., xuñ., xul., ago., set., out., nov., dec.
- Capitals: default case pairs; capitals keep the dot (COL·LEGI); Ŀ is the capital of ŀ.

Galician norms: the official ILG-RAG norm (1982, revised 2003) is used in education, media and administration; the
reintegrationist AGAL norm (1983; lh, nh, ç, -çom) has no official status.

#### Names, codes

- CLDR: Catalan / каталанский / カタロニア語 / 加泰罗尼亚语 / 加泰蘭文 / Katalanisch / catalán / catalan / catalano /
  kataloński / catalão / Katalanca / Tiếng Catalan / 카탈로니아어; own name català. Galician / галисийский / ガリシア語 /
  加利西亚语 / 加利西亞文 / Galicisch / gallego / galicien / galiziano / galicyjski / galego / Galiçyaca / Tiếng
  Galician / 갈리시아어; own name galego.
- E-ink codes CA, GL. Speakers: Catalan 4.1 M first language and 5.1 M second (Ethnologue 2021/22), over 10 M who
  understand and speak it (Council of Europe); Duolingo teaches it from Spanish only. Galician about 2.4 M with some
  competence (2012), 58% of Galicia first-language (2007); no Duolingo course.
### Icelandic and Afrikaans

Sources: Yomitan e7334a9; wty `latest/dict` (release 2026.10.11, downloaded 2026-10-20); wordfreq 912caf6
(`small_is`, `large_nl`); Leipzig `afr_mixed_2019_1M` (CC BY); DMII/BÍN Comprehensive Format 21.10 (CLARIN-IS
20.500.12537/162); FreeDict `isl-eng` 0.1.1 and `afr-eng` 0.2.2 (TEI); Apertium repositories; CLARIN-IS records of
ISLEX and Íslensk nútímamálsorðabók; JDK 21 (CLDR).

#### Yomitan

Neither language has a descriptor. Both build under their own codes from a descriptor without transforms (`build.mjs`
`borrowed`, as Malay from Indonesian): Icelandic from Swedish (`isl`), Afrikaans from Dutch (`afr`); both give
capitalization only. wty rows of both leave the rules field empty (is-en 207,437 rows, af-en 14,040); parts of speech
are in the definition tags (is-en lemma rows: n 11,535, v 2,529, adj 2,120, name 1,966, adv 556; af-en: n 3,604,
name 725, adj 717, v 517), so any own rule list runs untyped or reads the tags (as Kazakh's verb chains).

#### Dictionaries (wty)

Cells: archive MB, words; ✗ under 1,500; – no file. No own-language edition.

| from | en | ru | pl | zh | fr | it | tr | ko | ja | pt | es | vi | de | ipa |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| is | 3.22 18.7k | 0.33 5.7k | 0.31 5.3k | 0.29 4.9k | 0.31 6.2k | 310 ✗ | 0.08 2.3k | 701 ✗ | 0.08 1.6k | 0.12 2.3k | 417 ✗ | 94 ✗ | 749 ✗ | 0.26 16.1k |
| af | 0.53 5.6k | 0.23 4.0k | 0.07 1.7k | 0.12 1.5k | 0.19 4.5k | 747 ✗ | 796 ✗ | 595 ✗ | 673 ✗ | 862 ✗ | 370 ✗ | 194 ✗ | 317 ✗ | 0.05 2.8k |

- Pairs that pass: is en ru pl zh fr tr ja pt; af en ru pl zh fr. Near the line (inside `needsCount`'s band): is→ja
  1,561, af→pl 1,690, af→zh 1,549. Outside the gloss languages: is-el 2,374, is-ku 2,879; af-nl 2,566.
- The tree now also holds per-pair transcriptions (`is/en/wty-is-en-ipa.zip`, 15,300 words; af-en-ipa 2,221 words)
  besides `all/<lang>`; the catalog script keeps reading `all/<lang>`.
- Form rows (rows / form rows / lemma headwords / distinct form headwords): is-en 207,437 / 187,922 / 18,690 /
  182,211; af-en 14,040 / 7,919 / 5,559 / 7,652; is-ru 9,143 / 3,416 / 5,694; af-ru 4,189 / 204 / 3,967.
- is-en has every noun case and number with the suffixed article (hesturinn, hestinum, hestsins → hestur) and
  adjectives strong and weak with comparison, but verbs mostly by their principal parts (3rd person past 2,015 rows,
  supine 2,019; 1st person present only 51): talar, sagði, fór, komið are rows; segir, gerðist, finnst, gæti are not.
- af-en: plural 3,506 rows, diminutive 1,110 (huisie, hondjie), alternative 754, participles 497 + 431 (gelees,
  gesê), attributive 524, comparison 432. Some form rows lead to Arabic-script Afrikaans (die → دي): dropped as the
  Jawi rows are.
- af headwords: the article is `'n` with an ASCII apostrophe; 142 headwords contain an apostrophe ('n, 't,
  'seblief, A's); 278 have a hyphen (270 affixes; Suid-Afrika); marks in lemma headwords ë 170, ê 27, ï 20, é 9,
  ô 8.

#### Other dictionaries

| dictionary | pair | size | license | notes |
|---|---|---|---|---|
| DMII / BÍN Comprehensive 21.10 (Árni Magnússon Institute) | is forms → lemma | 32.8 MB zip; 6.57M form rows, 303,820 lemmas | CC BY-SA 4.0 (CLARIN record; Miðeind's BinPackage README the same, with a required credit line "Beygingarlýsing íslensks nútímamáls …") | 233,179 form → lemma pairs reach an is-en or is-ru lemma; 55,319 of them (54,828 forms, mostly verbs) are not is-en headwords; as untagged form-of rows a 0.20 MB zip |
| FreeDict isl-eng 0.1.1 | is→en | 11,219 entries; 2,068 headwords not in wty | GPL-3.0+ | from Apertium; adds 0.4 / 1.6 points (top 1,000 / 5,000), mostly names; not worth it |
| FreeDict afr-eng 0.2.2 (Ergane) | af→en | 5,129 entries; 2,965 not in wty | GPL-2.0+ | word lists; adds 71.1 → 85.0% (top 1,000), 46.6 → 58.9% (top 5,000) |
| Apertium isl-eng, afr-nld | | 23,504 / 6,296 entries | GPL | MT lexicons; not used |
| ISLEX 2026-02 | is → da, sv, nb, nn, fo, fi | about 55k words | CC BY-SA 4.0 (older pages CC BY-NC-ND) | no gloss language of the app |
| Íslensk nútímamálsorðabók | is→is | 56k headwords | 2026-02 release CC BY-NC-ND 4.0 (the withdrawn 2020 release CC BY-SA 4.0) | the current license forbids derivatives; not used |
| WikDict | – | no isl or afr pairs | | |

Whether a form row in one dictionary (BÍN forms) reaches a lemma held only in another (is-en) is checked at
implementation (`form_of.cpp` follows form rows after the lookup).

#### Frequency

wordfreq `small_is` (42,304 words); no Afrikaans list. Leipzig (sizes by HEAD request, MB): `afr_mixed_2019_100K`
22.0, `_300K` 65.8, `_1M` 216.1; `afr-za_web_2015_300K` 63.4; `afr_wikipedia_2021_300K` 66.3; `isl_newscrawl_2011_300K`
61.5. The measurements below used `afr_mixed_2019_1M` (465,503 lowercase types, 17.4M tokens).

#### Lookup coverage

Share of the top words found (as written or capitalized):

| lang | dictionaries | top 1,000 | 5,000 | 20,000 |
|---|---|---|---|---|
| is | is-en | 94.3% | 84.3% | 69.9% |
| is | is-en + is-ru | 94.3% | 84.4% | 70.3% |
| is | is-en + BÍN forms | 97.4% | 89.5% | 75.0% |
| is | ceiling (is-en or any BÍN word) | 98.2% | 94.3% | 88.8% |
| af | af-en + af-ru | 71.1% | 46.6% | 25.0% |
| af | + FreeDict afr-eng | 85.0% | 58.9% | |
| af | af-en + af-ru + nl-en (Dutch fallback) | 88.4% | 70.3% | 47.7% |
| af | all three | 93.1% | 74.9% | |

- Icelandic misses (786 of the top 5,000): 248 verb forms of is-en lemmas (kemur → koma, segir → segja, finnst →
  finna, vil → vilja), about 170 nouns not in is-en (names, viðurkenning), 50 adjectives and adverbs (fyrri, helstu,
  hinsvegar), 285 not in BÍN at all (English words, abbreviations t.d, m.a, þ.e, bls, u.þ.b).
- An own untyped verb ending list (-ur -ir -ar -um -ið -i → -a or -ja; -st -ist -ast; -aði -uðu → -a; only lemmas
  tagged v) reaches 458 of 1,218 missed verb forms of the top 20,000 (38%), 14 false (3%: fundist → funda, hefir →
  hefja); strong verbs with a vowel change (kemur, fer, gefur) stay unfound. BÍN finds those too.
- Afrikaans: English Wiktionary has no Afrikaans entry for om, na, weer, begin, dus, terwyl and many other basic words
  (checked through its API). Misses of the top 5,000 by how they would be found: a naive ending list 3.6% (about 20%
  of it false: kommer → kom, sessie → ses, gewig → wig); nl-en as written 22.3% (1,114 words: werknemers, ernstig;
  1 clear false friend in a sample of 50: verken "explore" → varken "pig"); nl-en after respelling (y → ij, initial s
  → z, sk → sch, -tjie → -tje, final g → ch, +en) 4.7% more, about 12% false (liewe → Lieve, verwoerd → vervoerd);
  none 22.5% (Afrikaans spellings of derivations: ekonomiese, spesifieke, finansiële; emphasis acutes; -self
  reflexives; gereeld, heeltemal, uiteindelik). nl-en is 8.63 MB (67,713 words).
- The gain of a Dutch fallback (71.1 → 88.4%, 46.6 → 70.3%) is about that of group 5a's Slovak → Czech fallback
  (70.7 → 82.3%, 51.2 → 68.8%).
- Compounds (`Compounds.kt` on the top 50,000 misses, parts of three letters or more): Icelandic 16,936 missing, split
  6,304 without links (sampled about 87% sensible: fisk + veiðum, hæstaréttar + lögmaður; false líkn + eski) and 6,392
  with s, a, ar, u (genitive forms are form rows of their own, so links add 88); Afrikaans 40,134 missing, 6,517
  without links (about 80%: skool + vakansie; false vers + oek), 7,191 with s, e (about 70%: ver- verbs split as ver
  "far" + rest).
- Words over 16 letters: 0.18% of Icelandic text, 0.58% of Afrikaans tokens (Swedish 0.14%).

#### Text

Icelandic:
- þ ð æ ö and á é í ó ú ý are letters; 48% of the top 5,000 words have one. Typed without them is rare in wordfreq (ad
  rank 4,156 against að 4; med 4,927 against með 17; thad, thvi, thu absent); no chat corpus was available. Keys
  without marks would make 58 keys of the top 5,000 shared (í/i, á/a, að/ad, vel/vél, fór/for/för, satt/sátt).
- Ordinals with a period (1. janúar, 20. öld): Icelandic joins row "Ordinals with a period".
- Quotes „…“ with ‚…‘ inside.
- Abbreviations that keep a sentence going: t.d., þ.e., þ.e.a.s., m.a., skv., nr., kl., bls., u.þ.b., sbr., e.t.v.,
  þ.m.t., a.m.k., ath., klst., dr., hr., sr., f.Kr., e.Kr. (before a lowercase letter), o.þ.h.; o.s.frv. and o.fl.
  usually end a sentence and are left out.

Afrikaans:
- The article 'n (397.8k uses in the corpus): ’n 32%, 'n 29%, ‘n 28% (autocorrect's opening quote: 113,304 of the
  corpus's 122,952 ‘ are ‘n), ŉ (U+0149) 4.2%, plain n 3.9%, `n 2.1%, ʼn and ´n rare. At a sentence start it stays
  lowercase ("’n Man"). Today the word start skips the apostrophe and looks up "n", which has no entry: the most
  frequent word after die finds nothing.
- Apostrophes inside words (foto’s): Ukrainian's processor.
- ê ë î ô û ï: 2% of the top 5,000 words. Typed without them, as a share of the marked spelling's uses: wereld 6.6%,
  gese 4.6%, reel 18%, geeet 10%, oe 6%. Keys without marks share 25 keys of the top 5,000 (se/sê, voel/voël, lê/le,
  hoe/hoë, môre/more).
- Emphasis acutes (dít, nóg, én, ná, één, méér): 82 words of the top 20,000; 57 found with the acute dropped. dié and
  ná are words of their own, so the exact match comes first.
- Diminutives, plurals and the ge- past are mostly form rows; separable verbs put ge- inside (opgetree → optree).
  Hyphens stay inside words (Suid-Afrika, e-pos).
- Dates without a period ("5 Mei" 21,456 times, "5. Mei" none); ordinals 1ste, 2de: no ordinal rule.
- Quotes “…” and "…"; »…« and „…” rare.
- Abbreviations (counts before a capital / a lowercase letter): mnr. 880/24, mev., mej., dr. 549/9, ds. 460/15, prof.,
  adv., bv. 54/561, d.w.s. 5/122, m.a.w., t.o.v., i.p.v., o.a., nl., vgl., asb. 34/431, bl., e.a., nr., jr., sr., st.,
  hfst., ca., v.C.; "ens." left out; "n.C." only before a lowercase word or a number.

#### Names, codes

- CLDR: Icelandic / исландский / アイスランド語 / 冰岛语 / 冰島文 / Isländisch / islandés / islandais / islandese /
  islandzki / islandês / İzlandaca / Tiếng Iceland / 아이슬란드어; own name íslenska. Afrikaans / африкаанс /
  アフリカーンス語 / 南非荷兰语 / 南非荷蘭文 / Afrikaans / afrikáans / afrikaans / afrikaans / afrikaans / africâner /
  Afrikaanca / Tiếng Afrikaans / 아프리칸스어.
- E-ink codes IS, AF. Speakers: Icelandic about 0.33-0.39 M; Afrikaans 7.2 M first language (2016), 10.3 M second
  (2011). No Duolingo course for either.
### Tatar and Bashkir

Turkic languages of Russia written in Cyrillic, close to each other and to Kazakh and Kyrgyz; the block reuses group
4e's design (`2026-10-19-group-4e.md`): Mongolian's descriptor borrowed, own ending rules, letter keys for the letters
OCR and Russian keyboards lose, Paddle `cyrillic`, Leipzig frequency lists, Lingua Libre audio. Sources: Yomitan
e7334a9; wty release 2026.10.11 (yomidevs/wiktionary-to-yomitan 602ffb3); kaikki JSONL of the en, ru, fr, zh, ko, tr
and ja editions; PanLex mirror `cointegrated/panlex-meanings` rev 0422f0e (CC0, 2024-03-01 dump); Apertium; Tatoeba;
the Wikidata query service; 47 Tatar and 48 Bashkir Wikipedia articles; Leipzig downloads; Paddle v5 on ONNX Runtime;
tessdata at the pins of `tessdata_catalog.py`; the catalog's fonts and Noto Sans; Commons and Lingua Libre; eSpeak NG
and RHVoice; CLDR; circle-flags 379588b. Nothing was measured on a device.

#### Yomitan

No tt or ba descriptor. Mongolian's (`mon`, capitalization only) is borrowed under `tat` and `bak` with the app's own
processors and rules (`addedPreprocessors`, `addedTransforms`), as for Kazakh and Kyrgyz. Russian's yoToE and
removeRussianDiacritics are not needed (ё only in Russian loans).

#### Dictionaries

Tatar is not in wty at all: no `dict/tt`, and `languages.json` at 602ffb3 lists 157 languages with Bashkir and
Crimean Tatar but not Tatar; no issue mentions it. Issue #334 (Kyrgyz, 2026-04-04) was answered "Added" the next
day; issue #338 widened the list to about 150 of the English edition's largest languages, and Tatar's English
edition is small. Kaikki's Tatar, counted as `countWords` would:

| edition | senses | entries | words with own meaning | Cyrillic headwords | Latin | verbs |
|---|---|---|---|---|---|---|
| ru | 18,092 | 12,206 | 11,817 | 11,920 | 173 | 2,507 (2,350 as infinitive -рга/-ргә, 145 as -у/-ү) |
| en | 2,237 | 2,046 | 1,764 | 1,274 | 536 | 92 (-у/-ү) |
| tr | 6,331 | 6,009 | 5,839 | 133 | 5,867 (Zamanälif) | 1,306 |
| ko | 5,122 | 4,677 | 4,596 | 2,409 | 2,260 | 660 |
| fr | 1,792 | 1,643 | 1,229 ✗ | 1,144 | 93 | 136 |
| zh | 1,143 | 845 | 791 ✗ | 683 | 135 | 32 |
| ja | 995 | 891 | 876 ✗ | 122 | 769 | 210 |

- A tt-ru would be the real Tatar dictionary: noun declension tables in `forms` (about 9,000 nouns; the genitive
  mistagged "possessive"), 8,787 romanization rows, 36 Arabic-script rows, a few garbage cells (юлңнң), short Russian
  equivalents as glosses. tt-en passes by count (1,764) but only 1,274 words are Cyrillic. Transcriptions: 191 and 87
  words (fail).

Bashkir in wty (MB, words; ✗ under 1,500):

| from | en | ru | pl | fr | ko | zh | tr | vi | pt | de | ja | it, es | ipa |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ba | 0.87 3,041 | 0.71 4,180 | 0.20 2,045 | 1,314 ✗ | 797 ✗ | 781 ✗ | 439 ✗ | 371 ✗ | 283 ✗ | 192 ✗ | 116 ✗ | – | 0.06 3,177 |

- Distinct Cyrillic headwords: en + ru 5,018, with pl 5,647. Parts of speech in the definition tags (ba-en n 1,810,
  adj 385, v 343, vi 109, vt 108; ba-ru n 3,292, v 211); the rules field empty. Verbs end in -ыу -еү -оу -өү -ау -әү
  (four-way rounding harmony).
- Form rows: ba-en 17,595 (about 1,390 nouns × 11 forms, 5 Yañalif), ba-ru 22,872 (about 1,750 nouns), ba-pl 2,173
  (1,937 of them Latin transliterations pointing to Cyrillic); Latin → Cyrillic rows also in ba-tr, ba-de, ba-ja. The
  4e row "Form rows" covers them as is.

Russia's other languages (kaikki senses, English / Russian Wiktionary), for the deferral: Yakut 4,912 / 4,606 (wty
sah-en 3,076 and sah-ru 3,991 words, both pass), Ossetian 1,105 / 4,556, Chechen 1,341 / 3,875, Crimean Tatar – /
3,636, Chuvash 2,001 / 2,361, Kalmyk 832 / 2,276, Moksha 2,830 / 2,210, Ingush – / 2,126, Udmurt 2,042 / 1,703,
Eastern Mari 2,445 / 1,636, Kumyk – / 1,678, Erzya 1,426 / 1,418, Buryat 677 / 1,338, Komi-Zyrian 1,646 / 1,192, Avar
643 / 997.

#### Other sources

| source | Tatar | Bashkir | license |
|---|---|---|---|
| PanLex | 27,916 expressions (Cyrillic 15,156, Latin 11,857, Arabic 811); Cyrillic words with Russian 9,474, with English 4,676 | 5,505 Cyrillic; with Russian 3,976, English 3,186 | mirror CC0; live site CC BY-NC-SA (as group 4e) |
| Apertium | tat-rus 6,036, tat-eng 14,985, tat-bak 57,710, kaz-tat 9,956 | analyser only | GPL-3.0; lemma pairs without glosses |
| Tatoeba | 100,502 sentences (70,150 Cyrillic, 30,349 Latin) | 434 | CC BY 2.0 FR |
| Wikidata lexemes | 11 | 17 | CC0 |
| corpus.tatar, tugantel.tatar | over 500 M tokens; 194 M word forms | – | no license for the texts |

- Printed academic dictionaries are under copyright; tatsoft.info does not resolve; bashkort.org has no
  dictionaries.
- PanLex quality (agent's judgement): mostly right (су → вода, мәче → кошка, буенча → по, ҡояш → солнце), some noise
  (Украина → "Яма", ут → "палаты", hyphenated infinitives бул-ырга). It has words kaikki lacks (буенча, кадәр,
  турында, шулай, яки; Bashkir шулай, әммә, фәнни).
- Top 2,000 Leipzig word forms found (dictionaries / + PanLex / with rules / with rules + PanLex): Tatar news 1,421 /
  1,559 / 1,681 / 1,799; Bashkir en + ru 950 / 1,028 / 1,468 / 1,532; with pl 1,006 / 1,058 / 1,559 / 1,599.

#### Lookup coverage

Tatar dictionaries simulated from kaikki the way wty builds them (tt-ru 11,581 headwords and 280,606 forms; tt-en
1,216 and 14,754). Lowercase Cyrillic tokens; cumulative.

| text, dictionaries | headword | + form rows | + own rules |
|---|---|---|---|
| tt Wikipedia (19.4k tokens), en | 21.0% | 28.3% | 41.6% |
| tt Wikipedia, ru | 39.8% | 67.3% | 80.3% |
| tt Wikipedia, en + ru | 42.2% | 70.4% | 82.6% |
| tt news 2015 (117.8k), en + ru | 43.6% | 71.1% | 85.0% |
| tt web 2018 (104.0k), en + ru | 44.4% | 73.8% | 86.6% |
| ba Wikipedia (62.4k), en | 34.0% | 42.9% | 66.2% |
| ba Wikipedia, ru | 31.5% | 41.0% | 57.5% |
| ba Wikipedia, en + ru | 36.6% | 49.2% | 70.6% |
| ba Wikipedia, en + ru + pl | 40.5% | 53.0% | 76.1% |
| ba Leipzig wiki (111.9k), en + ru + pl | 40.4% | 51.1% | 76.5% |

- The research rules (written from grammar descriptions, no GPL code): the noun chain stem [plural] [possessive]
  [case] [predicative or particle]; the verb chain stem [voice] [negation], then past, present, negative present,
  future (-ачак, -асаҡ, -ар) or conditional (-са, -һа) + person, imperative, infinitive, converb (-ып, -гач, -ғас,
  -ганчы), or participle and verbal noun (-ган, -ған, -учы, -ыусы, -асы, -у, -ыу) + [plural] [possessive] [case]; then
  the question particle -мы/-ме (Bashkir also -мо/-мө). Tatar endings in two harmony variants (plural -лар -ләр -нар
  -нәр; genitive -ның -нең; dative -га -гә -ка -кә -на -нә -а -ә; accusative -ны -не -н; locative -да -дә -та -тә -нда
  -ндә; ablative to -ннән; -ча, -дагы, -гача), Bashkir with the л д т ҙ variants, rounded о ө variants, the third-person
  possessive -һы after a vowel (системаһы), the past -ны -ды -ты -ҙы. Consonant change undone (Tatar аягы → аяк,
  китабы → китап; Bashkir ғ → ҡ, г → к, б → п).
- Verb dictionary forms: Tatar stem + у/ү and also the infinitive -ырга -ергә -арга -әргә -рга -ргә, because tt-ru
  writes verbs as infinitives and tt-en as -у forms; the present -ый/-и restored to -а/-ә. Bashkir stem + ыу еү оу
  өү у ү. Verb chains count only when the headword is a verb; the longest stem wins.
- Precision, 40 samples each weighted by frequency (agent's judgement): Tatar 35/40 (wrong: үтүгә → үтүк "iron", ның
  → ны, органнарын → a form of орырга since орган is missing, дата → да, атамаларын → only related to атарга);
  Bashkir 34/40 (килешеү → килеү, килтерә → килеү, булдыралар → булыу related only, итә → ит "meat" since итеү is
  missing, ағаһы → ағыу "poison", йәһәтенән → йәһәт). Most errors come from missing headwords.
- Misses: Tatar particles та тә үк, Russian loans and their forms (система, системасы, федераль), compounds with итү
  and кылу written apart (кабул, тәшкил, идарә), abbreviations; Bashkir the particle ла лә да дә ҙа ҙә та тә, тип,
  итеп, береһе, билдәле, бәйле, әммә, loans.
- Tokens longer than 16 letters: Tatar 0.17-0.18%, Bashkir 0.21-0.23%: the group 4b row "Scan length" (16) covers
  them.

#### Letters

Leipzig 100K word lists: Cyrillic tokens with a foreign letter tat_news_2015 0.09%, tat_web_2018 0.05%,
bak_wikipedia_2021 0.04%.

- No typing habit like Kazakh's Latin i (39%) or h (60%): Latin ə 0.03% at most, Latin h for һ 0.08-0.15% (Tatar)
  and 0.03% (Bashkir), Latin ç Ç ş for ҫ 0.04%, fita ѳ for ө 17-24 tokens, Greek γ for ү 10, ӊ for ң 4-9. By the row
  "Look-alike fixes only after OCR" these go to `ocrWord` only.
- Legacy 8-bit Tatar: old fonts put the Tatar letters on the Serbian slots of Windows-1251 (ә → ђ, ө → љ, ү → њ, ң →
  ћ, җ → ќ, һ → џ: белђн, књп, шђџђр); tat_news 1,090 tokens (ђ 587 against 588k ә, about 0.1%), tat_web 216; none in
  Bashkir. These letters never occur in Tatar words, and the text itself is written so (old sites, documents), so the
  mapping is a spelling of the text, for any text.
- Russian twins (keys as Kazakh's): headwords and forms sharing a key with another: Tatar 1.74% (5,266 of 302k),
  Bashkir 2.18% (750 of 34.4k), against Kazakh's 0.9%; headwords only 2.61% (булу/бүлү, җай/җәй) and 3.51%
  (тороу/төрөү, хис/һис). By letter: Tatar ң 0.78%, ә 0.37%; Bashkir ң 0.41%, ә 0.40%, ҫ 0.15%. More than Kazakh
  because harmony pairs differ only in ә/а, ө/о, ү/у. Edited text shows no twin typing; the rate in informal typing is
  unknown.

#### Scripts

- Tatar: Arabic script until 1927, Latin Yañalif 1927-1939, Cyrillic since 1939. Tatarstan's law of 1999 adopted a
  Latin alphabet (Zamanälif); the federal amendment of 11 December 2002 requires Cyrillic for the state languages of
  the republics, upheld by the Constitutional Court in November 2004. (The "2012 law" of the research brief was not
  confirmed: only the 3 March 2012 revision of Tatarstan's language law was found, without an alphabet clause.)
- Latin Tatar online: 11-13% Latin sentences in Leipzig's Azatliq corpora, 0.0-0.1% in the tat_* corpora, 30% of
  Tatoeba, the Turkish Wiktionary's Tatar entries; tt.wikipedia has no script converter. Bashkir is Cyrillic only.

#### Recognition

- Paddle `cyrillic_PP-OCRv5_mobile_rec` (852 classes) has all ten letters (ә ө ү җ ң һ ҡ ғ ҙ ҫ) in both cases; eslav
  has none; PP-OCRv6 small has no Cyrillic at all.
- PC run (9 lines per language, one in capitals, 11 styles), wrong characters: Tatar 0.2-1.7% per style, 0.51% over
  all but Literata; Bashkir 0.9-1.9%, 1.06%. Literata 36 px: 1.5% and 6.7% (the font lacks ҡ ҙ ҫ). Errors: capitals
  read as small letters in the capitals line (Ң → ң in every style, Ҙ → ҙ, Ҫ → ҫ, Һ → һ); Ә → Э three times (Tatar),
  Ҙ → З twice; Ҫ → Latin Ç in 7 of 10 Bashkir styles; small special letters always right.
- Tesseract: `tat` (fast 1.07 MB, best 7.59 MB) is a Latin-script model (99 characters, Ä Ö Ş Ü, no Cyrillic); no
  `bak`; `kaz` (fast 4.73 MB) has ә ө ү ң һ ҡ ғ ҙ but not җ ҫ; `rus` none. ML Kit has no Cyrillic model.

#### Fonts

Inter, PT Sans, PT Serif and Noto Sans have all ten letters in both cases; Literata lacks ҡ ҙ ҫ Ҡ Ҙ Ҫ although the
catalog lists it with cyrillic-ext. `fontSubsets` latin, cyrillic, cyrillic-ext.

#### Frequency

- wordfreq and hermitdave have neither.
- Leipzig Tatar (100K archive MB): tat_news_2015 19.63 (300K 60.0, 1M 201.5), tat_web_2018 17.41, tat_mixed_2015
  19.52, tat_wikipedia_2016 and _2021 15.8 and 15.87. Bashkir: bak_wikipedia_2016 16.2, bak_wikipedia_2021 19.28.
- Every `bak_news_2015/2016/2017` and `bak_newscrawl_2011` corpus is Tatar text (azatliq.org; per 10k Cyrillic
  letters ҡ ҙ ҫ ғ 1-7 times against 549 in Bashkir Wikipedia, Tatar-only җ 37-43 against 0). Only bak_wikipedia is
  Bashkir.
- SHA-256 of the 100K archives (downloaded 2026-10-20):
  - tat_news_2015_100K 33fb5b22d12f9b9c90e5f4c88abab2907add52bc7a17208b62e0ce65dba03c57;
  - tat_web_2018_100K 41166d920eb94be5fed2179089da1d1e65ede8840c7dc1c96e1f2363f0d3ab04;
  - bak_wikipedia_2021_100K 801b859ed64f1909384aadf2900d741d7f1eadb18bf48ec26d6fdcb90dc90530.
- Word forms: tat_news 91,038 (44,108 seen twice or more), tat_web 107,192 (49,270), bak_wiki 132,121 (55,445). Top
  1,000 found as a headword or form row: Tatar news 766, web 819; Bashkir 536 (en + ru), 577 (with pl). The Bashkir
  list is encyclopedic (йыл, район, ауыл).

#### Audio, voices, translation

- Lingua Libre: Tatar Q25285 417 files, 3 speakers (412 by one); Bashkir Q13389 1 file. Commons: Tatar 17 `Tt-` audio
  files; "Bashkir pronunciation" 98 `Ba-<word>` files (the `Ba-` prefix also matches 1,268 unrelated titles).
- Twenty common words, Lingua Libre / Wiktionary / either: Tatar 7-9 / 1 / 8-10 (the two runs differ by word choice:
  ана, әти, көн, күз, агач, балык, бала, кеше, яхшы; Кояш on Commons); Bashkir 0 / 7 / 7 (бесәй, икмәк, көн, ҡояш,
  ағас, балыҡ, бала). By the row "Audio order": Lingua Libre first for Tatar, Wiktionary first for Bashkir.
- Voices: no Google voice for either; eSpeak NG has tt and ba (GPL); RHVoice has Tatar (Talgat, LGPL), no Bashkir.
- Translation: `TranslationLanguageTable` lists tt and ba for both services.

#### Text

- Quotes: Tatar news “” 2,036/2,020 against «» 360/394, Tatar Wikipedia «» 108 against “” 47; Bashkir «» dominate;
  CLDR has only the root default; « » “ ” „ " for both.
- Abbreviations: Tatar һ.б. (also spaced), б.э.к., б.э., млн., млрд., т., рус., баш., ингл., лат., с., мәкт.;
  Bashkir һ.б., б.э.т. (also spaced), б.э., т., бор., л., млн., баш., рус., б., й., гл., кв., долл., диал., кн. A period
  after a short verb ends a sentence, so the lists are explicit.
- Numbers with endings: Tatar news mostly writes them joined (5нче, 2015нче: 232 joined against 31 hyphenated),
  Bashkir with a hyphen (1990-сы, 2-се, 3-сө: 276 against 5).
- Hyphenated words: Tatar 1,180 in the news sample (бер-берсе, азык-төлек, хатын-кыз), Bashkir 1,157 (халыҡ-ара,
  үҙ-ара): the 4e row "Hyphenated words" applies.
- The question particle is attached in both (-мы/-ме; Bashkir also -мо/-мө). Apostrophes almost never.

#### Names, codes

- Speakers: Tatar 3.26 M in the 2021 census (4.28 M in 2010); Bashkir about 1.1 M (2021; another source 1.56 M).
- CLDR: Tatar / татарский / タタール語 / 鞑靼语 / 韃靼文 / Tatarisch / tártaro / tatar / tataro / tatarski / tártaro /
  Tatarca / Tiếng Tatar / 타타르어; Bashkir / башкирский / バシキール語 / 巴什基尔语 / 巴什喀爾文 / Baschkirisch /
  baskir / bachkir / baschiro / baszkirski / bashkir / Başkırtça / Tiếng Bashkir / 바슈키르어. Own names татар теле,
  башҡорт теле (CLDR "татар", "башҡорт").
- circle-flags: `ru-ta` (Tatarstan, 330 B → 709), `ru-ba` (Bashkortostan, 380 B → 817, without the kurai flower);
  `language/tt` and `language/ba` link to them. `flags/tt.svg` is Trinidad and Tobago and `flags/ba.svg` Bosnia and
  Herzegovina: never use them. E-ink codes TT, BA.
### Sanskrit

Sources: Yomitan e7334a9; wty (dataset sha c22cd95, release 2026.10.11, downloaded 2026-10-20); the Cologne Digital
Sanskrit Dictionaries (`sanskrit-lexicon/csl-orig` v02 text files); the Digital Corpus of Sanskrit (DCS,
`OliverHellwig/sanskrit`, CoNLL-U and `lookup/dictionary.csv`, CC BY 4.0); Vidyut 0.4.0 (wheel and
`data-0.4.0.zip`, MIT); 37 Sanskrit Wikipedia articles and the 18 Bhagavad Gita chapters with commentaries from
Sanskrit Wikisource (measurements only); Paddle `devanagari_PP-OCRv5_mobile_rec`; Noto Sans / Serif Devanagari and
Tiro Devanagari Sanskrit (OFL). Nothing was run on a device.

#### Yomitan and the app

- No `sa` descriptor. Hindi's (`hi`, no processors, no transforms) can be borrowed under `sa` (`san`) as Bengali's
  is; Sanskrit takes NFC only (Vedic देवाँ writes a nasalized vowel with candrabindu, so Hindi's candrabindu →
  anusvara is not taken; candrabindu is in 0.01-0.04% of tokens) plus the processors under "Text".
- Spaced languages use `WORD` resolution (`SpacedLanguageSupport`), which drops whole trailing words only, so a
  joined Sanskrit word is found only as written. `LETTER` resolution (ja, zh, ko, th, km) shortens character by
  character. `Compounds.kt`'s `word()` stops at the first non-letter (`isLetter`), so it would cut a Devanagari word
  at its first vowel sign.

#### Dictionaries (wty)

| from | en | ru | pl | zh | fr | ko | ja | pt | es | vi | ipa |
|---|---|---|---|---|---|---|---|---|---|---|---|
| sa | 3.80 9.5k | 0.62 13.1k | 1,263 ✗ | 545 ✗ | 378 ✗ | 349 ✗ | 208 ✗ | 259 ✗ | 182 ✗ | 317 ✗ | 0.22 11.2k |

(no it, tr, de files; no own edition.)

- Pairs that pass: sa-en, sa-ru and the transcription (11,198 words). sa-ru is larger than sa-en.
- sa-en: 277,870 rows = 12,245 lemma rows + 265,625 form rows (238,957 form headwords): declension (instrumental
  plural 14,974 rows …) and present-system verbs; sa-ru: 13,313 rows, 1 form row. The rules field is empty; parts of
  speech in the tags (sa-en n 6,320, adj 2,426, v 1,081; sa-ru n 7,805, v 2,727, adj 2,108).
- Lemma names of form rows: 8,642, of which 7,815 resolve with accents dropped (the rest are missing roots and
  suffixes).
- Lemma headwords are unaccented (accent and romanization only in the grammar line: `विश्व॑ • (víśva) stem`); 143,298
  of 238,957 form headwords carry svara marks (U+0951 in 123,559, U+0952 in 114,920). Dropped, the form keys fall to
  218,525.
- Scripts of headwords: Devanagari 9,418 lemmas and 236,094 forms; about 3k form rows in Sinhala (1,206), Brahmi,
  Gujarati, Tamil, Kaithi, Sharada and others, tagged "<Script> script form", pointing to the Devanagari lemma; no
  romanized headwords.
- Malformed form rows: 18,033 end in a footnote digit (पाय्ये॑भिः1), 149 carry a bracketed romanization, 89 a middle
  dot, 89 a vowel sign or virama detached by a space, 15 are Latin residue ("form", "a-").
- Anusvara: 33,059 keys write a class nasal + virama (सङ्ग), 8,559 an anusvara before a consonant (संग); 39,424 keys
  end in म्, 12 in anusvara, while 13.7-18.1% of text tokens end in anusvara (रामं for रामम्).

#### Other dictionaries

| dictionary | content | size | license |
|---|---|---|---|
| Monier-Williams 1899 (MW) | sa→en | 286,525 records, 194,083 distinct headwords; 50.2 MB text | conflicting: `mwheader.xml` says "Copyright © 2014 The Sanskrit Library and Thomas Malten", Creative Commons BY-NC-SA 3.0; the GitHub repositories `csl-orig` and `MWS` say CC BY-SA 4.0. The 1899 book is in the public domain. |
| Apte 1890, Macdonell 1893, Cappeller 1891 | sa→en | 34,882 / 20,749 / 40,069 | the same NC header |
| Böhtlingk PWG, PW | sa→de | 123,366 (PWG) | the same; marked deprecated on the Cologne page |
| Stchoupak 1932, Burnouf 1866 | sa→fr | 24,574 / – | the same |
| Friš (FRI) | sa→cs/ru/en reader's vocabulary | 8,155 entries, 7,671 Russian glosses | no header; repository CC BY-SA 4.0; edition Moscow 2015 |
| Knauer 1908 | sa→ru | 3,271 entries | repository CC BY-SA 4.0; not in `csl-orig` yet |
| Kochergina (1978, 2005) | sa→ru, ~30k words | – | in copyright; not usable |
| DCS `dictionary.csv` | sa→en lemma list (IAST) | 180,457 lemmas, 164,492 with short English meanings (10.7 MB) | CC BY 4.0; the meanings read like condensed MW glosses, their provenance needs checking |

- No Yomitan conversion of MW or any Sanskrit dictionary was found (GitHub, 2026-10-20). Cologne headwords are in
  SLP1; conversion to Devanagari is one to one (vidyut-lipi, sanscript; MIT).
- Lemma coverage of the DCS gold (Hitopadeśa, 6 Rāmāyaṇa, 4 Kathāsaritsāgara, 3 Ṛgveda chapters; 33,598 words, 4,813
  lemmas): wty en + ru 88.4% of words / 65.8% of lemmas; wty ru 82.8 / 57.0; MW 96.4 / 93.0; Macdonell 82.7 / 58.3;
  Friš 82.1 / 50.4; Friš + wty ru 89.9 / 67.2; wty + MW 97.2 / 93.6. MW has 178,833 headwords wty lacks.

#### Sandhi

Sanskrit changes the sounds where two words meet, and the writing follows the sound, so in Devanagari the joined
words become one written word:

- vowels merge: तथा + इति → तथेति; च + एव → चैव; इति + अस्य → इत्यस्य;
- the final visarga changes: रामः + अपि → रामोऽपि (the avagraha ऽ marks the dropped a), पाण्डवाः + च → पाण्डवाश्च,
  सः + गच्छति → स गच्छति;
- final consonants change or join the next vowel: किम् + अकुर्वत → किमकुर्वत; तत् + मात्र → तन्मात्र;
- final m before a consonant is written as anusvara: रामं गच्छति;
- compounds are written as one word (धर्मक्षेत्रे), with sandhi inside them (सुख + उपविष्ट → सुखोपविष्ट).

In the DCS gold of the Hitopadeśa (19,698 written words) 21.4% of written words hold more than one word (2,187
compounds only, 1,747 free words joined by sandhi, 278 both); 396 joins are written with an avagraha; 21.3% of the
words are not first in their written word, so a lookup from the written word's start cannot reach them.

#### Lookup coverage

Samples (Devanagari tokens): Wikipedia 52,099; Gita verses 6,719; Gita commentaries 30,000 of 526,607; DCS texts
converted from IAST with words after an avagraha joined as Devanagari prints them: Hitopadeśa 19,698,
Kathāsaritsāgara 3,430, Rāmāyaṇa 2,337. Keys: sa-en lemmas and forms (accents dropped, the malformed rows cleaned) and
sa-ru lemmas; the anusvara rule reads a class nasal + virama before its own stop as anusvara on both sides and a final
anusvara as म्. Steps are cumulative; "found" means something was found, not that it is right:

- word end: the word-end sandhi undone (ो → ः, ा → ाः, a final consonant + ः, a voiced final made plain, र् श् ष् स्
  → ः, a final nasal → त् or म्, ं → म्), and the part before an avagraha tried so;
- prefix3: the longest start of at least 3 code points that is a key and does not end before a mark;
- first word: the longest first word with the join undone (ए ओ ा ै ौ ी ू split into their two vowels, y/v after a
  virama read as i/u, a consonant + vowel sign read as consonant + virama + an independent vowel, श् ष् स् before a stop
  read as ः, ऽ read as अ), then the word-end rules;
- split: the fewest such parts that cover the token.

| sample | exact, no anusvara rule | exact | + word end | + prefix3 | + first word | split instead |
|---|---|---|---|---|---|---|
| Wikipedia en + ru | 45.9% | 49.3% | 50.7% | 83.7% | 97.0% | 87.2% |
| Gita verses | 43.9% | 46.6% | 49.0% | 85.5% | 98.0% | 89.0% |
| Gita commentaries | 36.5% | 37.6% | 39.6% | 81.2% | 98.0% | 83.9% |
| Hitopadeśa | 51.0% | 52.9% | 57.0% | 86.5% | 98.1% | 88.0% |
| Kathāsaritsāgara | 43.8% | 45.1% | 48.4% | 81.8% | 98.3% | 86.7% |
| Rāmāyaṇa | 47.4% | 48.3% | 53.4% | 84.7% | 97.6% | 89.7% |
| Wikipedia, ru only | | 20.5% | 20.8% | 58.2% | 86.9% | 41.1% |

Precision on the Hitopadeśa (right: the found key is the gold first word, its lemma, or a form of the same wty lemma;
DCS lemmatizes participles to roots, so this undercounts):

| cascade | found | right of all | right of found |
|---|---|---|---|
| exact + word end | 57.0% | 53.4% | 93.8% |
| + noun endings | 64.5% | 59.4% | 92.0% |
| + endings + prefix3 | 89.3% | 73.3% | 82.1% |
| + endings + first word | 98.5% | 77.7% | 78.9% |
| exact + word end + split | 88.0% | 68.0% | 77.3% |
| vidyut-cheda, first part looked up in wty | 55.3% | 45.9% | 83.1% |

- Ceiling: 90.8% of gold words have their form or lemma among the keys; about 17% of one-word tokens are inflections
  wty neither lists nor reaches.
- Noun endings: an own list from declension tables (a-stems -ः -म् -ेन -ाय -ात् -स्य -े -ौ -ाः -ान् -ैः -ेभ्यः -ानाम्
  -ेषु -ाभ्याम् -योः -ानि -ो -ैर् -ाद्; ā-stems -ा -ाम् -या -ायै -ायाः -ायाम् -ाभिः -ासु; i-, ī- and u-stems), the stem a
  lemma headword of 2+ code points: +4.6 to +7.6 points; of 25 hits judged by hand the "wrong" ones were mostly
  participles (पूजिताः → पूजित) and compounds (कनकसूत्रं → कनकसूत्र), nearly all usable.
- The prefix and first-word fallbacks find the first members of compounds (गोपगृहे → गोप) but also cut words wty lacks
  into wrong pieces (कपिलो → कपि "monkey", विश्वासः → विश्वा). Shown as "part of the word" they help; shown as the word
  they mislead.
- vidyut-cheda (0.4.0, its own lexicon): every part known for 72.9% of tokens; its first part equals the gold first
  word for 55.0% of 5,000 tokens; 0.2 ms a token on the PC; lemmas in its own scheme.

Splitting tools:

| tool | license | size | on Android |
|---|---|---|---|
| Vidyut (cheda, sandhi, kosha, prakriya, lipi; Rust) | MIT | data zip 31.75 MB (`padas.fst` 46.3 MB, `registry.msgpack` 32.3 MB unpacked, cheda model 2.3 MB, sandhi rules 14 KB) | Rust through the NDK and JNI is possible; about 80 MB of data as a download item; not tried |
| sanskrit_parser | MIT | Python, large data | no |
| Sanskrit Heritage Engine (Huet, OCaml) | LGPL for the 2012 release; the current repository's license not confirmed | server | network only |
| Hellwig and Nehrdich neural splitter (2018) | AGPL-3.0 | TensorFlow | excluded |

Effort (estimated): (i) the longest start only (`LETTER`-like resolution for Sanskrit with the "ends before a mark"
filter): 1-2 days, 73.3% right; (ii) the rule list (word-end sandhi, avagraha, the first word with the join undone,
noun endings through `own-transforms.js` and `untypedRules`, import keys for accents and anusvara): 3-5 days, 77.7%
right, and an ending match comes before a cut into a prefix; (iii) vidyut-cheda through JNI: 2-3 weeks, four ABIs,
about 80 MB of data, not better on the first word here. Reaching the second word of a joined token (21% of words):
inner word starts with the restored vowel (एति → इति), as Thai and Khmer have word starts inside a line: 2-3 days on
top of (ii).

#### Transliteration

- Schemes: IAST (ā ī ū ṛ ṝ ḷ ṃ ḥ ṅ ñ ṭ ḍ ṇ ś ṣ), ISO 15919 (ṁ, r̥ l̥, ē ō), Harvard-Kyoto (ASCII, case-sensitive:
  aa A I U R lR M H G J T D N z S), ITRANS (several spellings per letter, looks like English), SLP1 (Cologne,
  Vidyut), Velthuis.
- Latin Sanskrit appears in scholarly editions and DCS/GRETIL (IAST), in brackets on English Wikipedia, and without
  diacritics in yoga and religion texts (asana).
- Folding to ASCII is unsafe: 15.1% of wty's 9,170 lemma romanizations share an ASCII-folded key with another word.
  IAST → Devanagari (vidyut-lipi) reproduces the headword for 86.7% of the romanizations; 62.1% of them contain an
  IAST letter, so only those are recognizable as Sanskrit in Latin text (an English page with IAST words is the
  detection problem).
- Libraries: sanscript / indic_transliteration (MIT), vidyut-lipi (MIT); Aksharamukha is AGPL-3.0 and excluded.

#### Marks and text

Share of tokens (Wikipedia / Gita verses / commentaries): svara marks 0.01% / 0 / 0; avagraha 1.58 / 3.21 / 2.64%;
candrabindu 0.01 / 0.04 / 0.01%; ending in virama 9.57 / 5.77 / 7.86%; in anusvara 13.71 / 18.05 / 14.99%; in
visarga 16.39 / 13.05 / 11.39%; ॠ ॡ ॢ ॣ ॄ 0.03 / 0.06 / 0.00%; longer than 16 / 24 / 32 code units 5.0 / 0.8 / 0.2%,
8.3 / 1.7 / 0.5%, 15.8 / 5.5 / 2.2%.

- Accents appear in Vedic editions only; Wikisource's Ṛgveda 1.1 is unaccented but writes ळ (अग्निमीळे).
- Sentence ends: । and ॥ (Gita: 700 ।, 1,436 ॥, verse numbers inside ॥१-१॥); Wikipedia 6,388 । against 471 ". ".
- Digits: 3,099 Devanagari against 1,001 Western. Quotes: ' 691, ‘ 262, " 212, ’ 144, “ 45, ” 44.
- Sanskrit is also printed in Bengali, Telugu, Kannada, Malayalam, Grantha, Gujarati, Oriya, Sinhala, Sharada,
  Thai and Tibetan scripts; lookup of those would need a conversion to Devanagari and is not proposed.

#### Recognition

- ML Kit: its Devanagari recognizer lists "संस्कृतम् / Sanskrit / sa" as experimental (Hindi, Marathi, Nepali
  supported).
- tessdata `san`: fast 12.40 MB, best 15.14 MB (`script/Devanagari` 17.9 / 28.6; `hin` fast 1.12 for scale).
- Paddle's Devanagari dict (568 entries) covers all of U+0900-097F (avagraha, ॐ, ॠ ॡ ॢ ॣ ॄ ऌ, ॑ ॒, ळ), no Vedic
  Extensions, no Devanagari Extended, no ZWJ/ZWNJ.
- Paddle on the PC: 14 Sanskrit lines shaped with HarfBuzz (Pillow had no raqm), v5 detector and the devanagari
  recognizer on ONNX Runtime. Share of wrong characters (spaces left out): Noto Sans 56 px 11.7%, 30 px 15.0%, Noto
  Serif 48 px 11.7%, Tiro 56 px 11.7%, Tiro 34 px grey 13.1%, white over a photo 12.6%; 12.6% over all. The accented
  line costs 12-18 errors a style; without it about 10.8%. Hindi's clean screens gave Paddle 0.9%. Recurring: avagraha
  read as 5, S, s or ड; conjuncts simplified (र्म → रम, त्र → ्र, ज्ञ → ज, ङ्ग → ड़, द्ध → द्व, श्च → श्व); ॐ read as
  ऊॐ or dropped; ॄ ॢ as ृ. ML Kit and Tesseract were not run; the pages are kept for the emulator.

#### Fonts

Noto Sans Devanagari and Noto Serif Devanagari: all 128 Devanagari, 41 of 48 Vedic Extensions, 32 of 32 Devanagari
Extended; Tiro Devanagari Sanskrit (OFL): 108 / 43 / 30, 1,835 glyphs with stacked conjuncts. The font catalog has no
Devanagari font; the system draws Noto. Whether the phone's copies have the Vedic signs is checked on the emulator.

#### Audio, frequency, names

- Lingua Libre `LL-Q11059 (san)` 105 files, 4-5 speakers (verbs and roots); Commons `Sa-` 90 audio files, mostly
  letters; twenty common words: Lingua Libre 2 (जल, रक्त), Wiktionary 0. No Google voice, no eSpeak NG voice.
- No wordfreq list. Leipzig `san_wikipedia_2021` 10K (1.34 MB), 30K (3.89 MB), 100K (12.6 MB). DCS has 5.46M
  lemmatized words (CC BY 4.0) in a 1,133 MB dump of 23,279 files; a lemma list would have to be built and hosted.
- Census 2011: 24,821 first-language speakers (14,135 in 2001); a school subject in much of India; no Duolingo course.
- CLDR: Sanskrit / санскрит / サンスクリット語 / 梵语 / 梵文 / Sanskrit / sánscrito / sanskrit / sanscrito / sanskryt /
  sânscrito / Sanskrit / Tiếng Phạn / 산스크리트어; own name in CLDR संस्कृत भाषा (usual संस्कृतम्).
- Translation: Google only (`sa`). circle-flags has no `language/sa`; `in` is in the app. E-ink code SA. Bubble letter
  candidates (हि and म taken): सं, ॐ, अ.
### Plain fixes found (no owner decision)

- Flags: circle-flags' `language/*` files are symlink text; read `es-ct`, `es-ga`, `ru-ta`, `ru-ba` directly; resource
  names with an underscore; never `tt.svg` (Trinidad and Tobago) or `ba.svg` (Bosnia and Herzegovina).
- Catalan: `wordBefore` must accept the middle dot (Il·lm.); check that the Catalan path normalizes to NFC (U+0387 →
  U+00B7).
- Afrikaans: the 'n word start (without it the most frequent word after die finds nothing); form rows to
  Arabic-script spellings dropped, as the Jawi rows.
- Icelandic: Đ đ ž š → Ð ð ð ð in `ocrWord` (letters it never writes); `ordinalPeriod`.
- Bashkir: Ç ç → Ҫ ҫ and the other look-alikes in `ocrWord`; Literata not offered; never the `bak_news*` Leipzig
  corpora.
- Sanskrit: the import cleanup of sa-en, `Compounds.word()` counting marks as part of the word if reused.
- The catalog's letter lists leave out ŀ Ŀ ŉ.
- Catalan Wiktionary audio tags of three letters (`occ`) are not read as regions; no change now (none of the common
  words is affected).

## Blocks and order (proposed)

Grouped by what the app needs, as group 5 was; the order is the owner's decision (question 1).

| block | languages | what it needs | depends on |
|---|---|---|---|
| 6a Romance | Catalan, Galician | Portuguese's descriptor borrowed; Catalan elisions and l·l as a word connector; Galician enclitic split (question 4) and demonstrative contractions; a fallback language for Galician (question 3); WikDict cat-rus; a Leipzig frequency list for Galician | group 5a's fallback piece (Slovak → Czech), if 5a is built first; otherwise 6a builds it |
| 6b Germanic | Icelandic, Afrikaans | descriptors borrowed (sv, nl); BÍN verb forms (question 8); the Afrikaans 'n word start; the Dutch fallback for Afrikaans (question 12); compounds with links; Icelandic ordinals with a period | the same fallback piece; hosting for BÍN and FreeDict afr-eng |
| 6c Turkic | Tatar, Bashkir | group 4e's ending engine, letter keys and Leipzig source with new tables; Tatar's dictionary (question 14) | group 4e built; a Tatar dictionary in wty or our converter |
| 6d Sanskrit | Sanskrit | Hindi's descriptor borrowed; sandhi rules and import cleanup (question 23); Devanagari fonts; scan length 24 | group 4b (Hindi) built (it is) |

Recommended order: 6a (Catalan has the most learners and needs the least new code), 6b, 6c once group 4e is built
and Tatar has a dictionary, 6d last (the largest new piece, the fewest learners). Within a block one branch per
language in the order of the table.

## Which text each rule reads

Row "Look-alike fixes only after OCR" (request 69): fixes and matching made for OCR misreadings apply to OCR results
only (`ocrWord`); spellings people type themselves stay matched in any text.

| lang | rule | reads |
|---|---|---|
| ca | elisions l' d' s' m' t' n' start a new word; apostrophe variants (' ↔ ’) | any text |
| ca | the middle dot between letters joins them into one word; `wordBefore` accepts it (Il·lm.) | any text |
| ca | l.l, l-l, l•l, l‧l, l⋅l, l∙l, ŀl tried as l·l when the word as written is not found (people type them) | any text |
| ca | "ll" tried as l·l when the word is not found (an engine dropped the dot); "l-l" from OCR is covered by the any-text row | OCR only |
| gl | enclitic split, demonstrative contractions, the fallback language | any text (grammar, not misreadings) |
| is | Đ đ → Ð ð, ž š → ð (letters Icelandic never writes, row "OCR letter fixes" of group 5a) | OCR only |
| is | Þ for P and Æ for AE, tried when the word as read is not found (question 10) | OCR only |
| is | compounds with links s, a, ar, u; ordinals with a period; abbreviations | any text |
| af | an apostrophe (' ’ ‘ ` ´ ʼ) before a lone n, s or t belongs to the word and is read as '; ŉ read as 'n | any text (seven spellings people type) |
| af | apostrophe variants inside words; emphasis acutes also looked up without them; keys without ê ë î ô û ï (question 11) | any text |
| af | the Dutch fallback | any text |
| tt, ba | ending rules; hyphen and digit rules; particle variants | any text |
| tt, ba | Kazakh-style letter keys (the Russian twins) | any text (a keyboard habit, as Kazakh's row "Letters OCR loses") |
| tt | legacy 8-bit letters ђ љ њ ћ ќ џ read as ә ө ү ң җ һ (the text itself is written so; the letters never occur in Tatar words) | any text |
| tt, ba | Latin ə h ç Ç ş, ѳ, γ, ӊ and the usual Latin look-alikes as Cyrillic letters (0.03-0.15% typed: not a habit) | OCR only |
| sa | NFC; Vedic accents dropped; anusvara and class nasals keyed alike; sandhi rules and noun endings | any text |
| sa | Latin S, s or 5 between Devanagari letters → ऽ | OCR only |

## Decisions this plan builds on

Rows of the languages plan: "Depth", "Gloss languages", "Catalog threshold", "Transcription threshold"
(Galician, Catalan, Icelandic, Bashkir, Sanskrit pass; Afrikaans passes with 2,800; Tatar has none), "Own-language
dictionaries" (none exists), "Recommended dictionaries", "Frequency dictionaries" (built on the phone; Catalan and
Icelandic from wordfreq `large_ca` and `small_is`), "Dictionaries we convert" and "FreeDict into Russian" (converted
dictionaries listed once hosted), "Second form-of step", "Inflection names and tags", "Rule chains", "German
compounds" (later for other languages with compounds), "Compound part order" (German only), "On-device OCR engines",
"OCR engine choice", "Diacritics of Latin-script languages", "ML Kit at setup", "Look-alike fixes only after OCR",
"Latin look-alikes in Cyrillic text", "Tesseract models", "Paddle model hosting", "OCR model list", "Flags", "Flag
country" (a default flag only), "Bubble letters", "Audio order, groups 2 and 3" (by measured coverage), "Text to
speech" (last source), "Font lists across languages", "Ordinals with a period", "Greek accents" (spellings matched at
import), "Group 5 release" (the model for when these ship). Group 4e's rows "Endings", "Letters OCR loses", "Written
Kazakh letters", "Hyphenated words", "Frequency", "PanLex languages"; group 4b's "Scan length"; group 5a's "Czech
dictionaries for Slovak" (the fallback model), "OCR letter fixes", "Words without their marks", "ESPDIC and WikDict".

Technical choices of the agent (the owner may object; recorded in the changelog when changed):

| Topic | Choice |
|---|---|
| Bundles | ca and gl borrowed from Portuguese's descriptor (capitalization only; Spanish's 545 rules cannot be borrowed and would find nothing, since wty's ca and gl rows carry no part of speech), codes `cat`, `glg`; Catalan adds French's `apostropheVariants`. is from Swedish's (`isl`), af from Dutch's (`afr`) with Ukrainian's apostrophe processor; tt and ba from Mongolian's (`tat`, `bak`) as Kazakh; sa from Hindi's (`san`) with NFC only (not Hindi's candrabindu → anusvara). Own rules through `addedTransforms` with `untypedRules` or the definition tags. |
| Catalan elisions | French's elision rule with l d s m t n. |
| Catalan l·l | `·` in Catalan's `wordConnectors`; the typed look-alikes as a lookup variant after the exact word (any text); "ll" → l·l for OCR words only, after the exact word (the 65 headword pairs such as col·la/colla keep the exact spelling first). |
| Galician contractions | desta, neste, nese, diso, naquel and the rest: d- and n- before the demonstratives split into de and en + the word, shown as a rule result. |
| Icelandic letters | No keys without þ ð æ ö and acutes (58 frequent keys would be shared, little evidence of such typing; question 9). OCR letter fixes Đ đ ž š → Ð ð ð ð. |
| Afrikaans 'n | The word start keeps an apostrophe before a lone n, s or t ('n, 's, 't) and reads it as '; ŉ read as 'n. |
| Afrikaans endings | No own list: form rows cover plurals, diminutives and participles; a naive list reached 3.6% of the top 5,000 at about 20% false. |
| Compounds | `compoundLinks`: is s, a, ar, u; af s, e. |
| Ordinals | Icelandic joins `ordinalPeriod`; Afrikaans, Catalan and Galician need nothing. |
| Turkic rules | Group 4e's engine with Tatar and Bashkir tables (two- and four-way harmony, Bashkir's ҙ variants, -һы after a vowel); Tatar verbs produce both the -у/-ү form and the -рга/-ргә infinitive; particle variants (Tatar та тә, Bashkir ла лә ҙа ҙә та тә) looked up as the dictionary's да/дә; the lookup starts after the digits of joined Tatar forms (5нче) as after a hyphen. |
| Turkic letter keys | As Kazakh's row "Letters OCR loses": every match, the exact spelling first; a Tatar or Bashkir letter written in the word is exact (as "Written Kazakh letters"). Collisions are 1.7% and 2.2% (Kazakh 0.9%; question 15). |
| Tatar Latin | Cyrillic only (Latin Zamanälif is not one to one with Cyrillic; 0.0-0.1% of the Leipzig Tatar corpora). |
| Tesseract | None for Tatar and Bashkir (`tat` is a Latin-script model; no `bak`); Paddle `cyrillic` for both. |
| Leipzig | Never the `bak_news*` or `bak_newscrawl` corpora for Bashkir (they are Tatar text). |
| Fonts | Literata not offered for Bashkir (it lacks ҡ ҙ ҫ), by the row "Font lists across languages"; ŀ Ŀ ŉ not in any letter list. Sanskrit offers Tiro Devanagari Sanskrit (OFL) as a downloadable font if the catalog gets a Devanagari font; the system's Noto otherwise. |
| Sanskrit import | sa-en cleaned at import: trailing footnote digits (18,033 rows), bracketed romanizations (149), middle dots (89), detached vowel signs (89) and Latin residue rows (15); Vedic accents dropped from headwords (`HeadwordMarks`); class nasal + virama keyed as anusvara (`replacements`, about 20 pairs); other-script form rows kept (only that script's text reaches them). |
| Sanskrit text | Sentence ends at । and ॥ (verse numbers inside ॥…॥ stay with the verse); scan length 24 by default (5.0-15.8% of tokens are longer than 16 code units). |
| Flags | Circle-flags' `es-ct`, `es-ga`, `ru-ta`, `ru-ba` read directly (never the symlink files or `tt.svg`, `ba.svg`), resources named with an underscore. |
| Audio order | Lingua Libre first for ca, is, af, tt, sa; Wiktionary first for gl and ba; text to speech last. |
| Voice | Languages without a voice keep the popup's "No text-to-speech voice for this language is installed". |
| OCR | ML Kit main for ca, gl, is, af and sa (row "ML Kit at setup"); Paddle `cyrillic` for tt and ba (ML Kit has no Cyrillic); the "more accurate" marks after the emulator run (question 21). |
| Upstream reports | The dropped entries (Catalan del, al, pel, ben; Galician moi) and the missing Tatar language are reported to wty by the owner or with the owner's consent (an outward-facing step; question 14). |

## Questions for the owner (deferred)

Each is marked deferred (it blocks a part; that part is skipped) or decided provisionally (the plan assumes the
recommended option; what changing it takes is said).

1. Answered (owner, 2026-10-21): (a), each block in its own session. Each session in turn (the emulator is shared)
   measures on the emulator every engine and model offered for its languages, Paddle included, and checks each rule's
   hypothesis on data before building it (group 5 measured ML Kit, Tesseract and cloud in one shared emulator run and
   Paddle on the PC only). Blocks and order. The seven languages differ in what they need (table "Blocks and order").
   (a, recommended) Four blocks in the order 6a Catalan, Galician; 6b Icelandic, Afrikaans; 6c Tatar, Bashkir (after
   group 4e); 6d Sanskrit. (b) The same blocks, Sanskrit before the Turkic block. (c) One language at a time by
   learners: Catalan, Afrikaans, Galician, Icelandic, Sanskrit, Tatar, Bashkir. (d) Only some of them now. Changing it
   reorders the work list only.
2. Answered (owner, 2026-10-21): (a). Catalan l·l. Today the middle dot ends a word: aimed at the second half of
   col·lecció the popup shows "lecció" (lesson), for 20% of the words with the dot. People also type l.l, l-l, l•l or
   ŀl; Paddle dropped the dot once and read it as a hyphen three times in 16 (the other engines are measured later). (a,
   recommended) The dot joins letters into one word in any text; the typed look-alikes are tried as l·l when the word as
   written is not found, in any text; a recognized "ll" is also tried as l·l when not found, for OCR words only (col·la
   and colla are both words, so the exact spelling comes first). (b) The dot and ŀ only. (c) The dot only. Changing it
   drops lookup variants and their tests.
3. Answered (owner, 2026-10-21): (a). A fallback language for Galician (as Slovak looks up Czech when a word has no
   Slovak entry). gl-en finds 77.1% of the 5,000 most frequent Galician words; with the user's Spanish dictionary 93.4%,
   with the Portuguese one 90.2%, both and gl-pt 96.0%. The official Galician spelling (x, ñ, ll, -ción) is close to
   Spanish; the extra words are mostly cognates with the same meaning (campaña, comisión, éxito), a few false friends
   (desa). (a, recommended) Spanish: when a Galician word has no entry, the Spanish dictionaries the user has are
   searched and their entries marked Spanish; the Spanish dictionaries offered unticked on Galician's download screen.
   (b) Portuguese (better for the rare Portuguese-like reintegrationist spelling). (c) Both, Spanish first: the fallback
   becomes a list instead of one language. (d) None. Blocks: Galician's fallback and its catalog entries.
4. Answered (owner, 2026-10-21): (a) only if a check at implementation shows that the false hits are few and do more
   good than finding nothing: each wrong hit is counted as harmless when the right word is still shown first, harmful
   when the rule's guess is the only or the first result; the numbers go to the owner before the rule is kept. Galician
   pronouns written joined to the verb (fíxose, darlle, facelo, atópase). No dictionary lists most of them; 0.75% of
   news words are such forms that an own split rule reaches (it strips the pronoun endings, undoes -lo after a dropped
   -r or -s, drops the stress accent and requires the host to be a verb). About 6% of its hits are wrong (premios →
   premi, estrea → estre, revela → rever + la), and that only with a guard that leaves words alone when Spanish or
   Portuguese Wiktionary has them as nouns (crise, debate, lema); Albanian's list was kept at 1.3% wrong. (a) The rule
   with the guard, shown as a rule result like the other own lists, the measured examples as tests. (b) The rule without
   the guard (more wrong hits, about 8-10%, estimated). (c) No rule. The owner's threshold decides (for Albanian: keep
   only if false hits are removed or cut to a few percent). Blocks: the Galician enclitic rule.
5. Answered (owner, 2026-10-21): (a); the agent files the issue from the owner's account after showing the owner its
   text. Words Wiktionary has but wty drops: Catalan del, al, dels, pel, ben (2.6% of Catalan news words; the popup
   shows "de", "a" or "be", sheep, instead) and Galician moi. Their pages are form-of templates with two targets. (a,
   recommended) Report them to wty with the page names and wait (an outward-facing step: the owner files it or allows
   it). (b) Also ship a small supplement of our own for these few words (our own glosses, a license note). (c) Leave it.
   Blocks: the supplement.
6. Answered (owner, 2026-10-21): (a). WikDict's Catalan → Russian dictionary (CC BY-SA 3.0). wty ca-ru finds 1,842 of
   the 5,000 most frequent Catalan words, WikDict cat-rus 4,001, both 4,144. (a, recommended) Convert it and list it
   unticked once hosted, as WikDict sv, da, no → ru (decision "ESPDIC and WikDict"). (b) Not offered. Galician has no
   WikDict pair. Changing it drops one catalog source.
7. Answered (owner, 2026-10-21): (a). Frequency for Galician, Afrikaans, Tatar and Bashkir (wordfreq has none of them;
   Catalan and Icelandic take wordfreq's lists). (a, recommended) Built on the phone from Leipzig, as Kazakh's: Galician
   `glg_newscrawl_2016_100K` (26.6 MB), Afrikaans `afr_mixed_2019_100K` (22.0 MB), Tatar `tat_news_2015_100K` (19.6 MB;
   `tat_web_2018_100K` finds 819 of the top 1,000 against 766 and is the alternative), Bashkir `bak_wikipedia_2021_100K`
   (19.3 MB; encyclopedic: йыл, район, ауыл). (b) As (a) without Bashkir. (c) None. Changing it removes catalog entries.
8. Answered (owner, 2026-10-21): (a); no own verb ending list, Icelandic verb forms are found once the BÍN dictionary is
   hosted. Icelandic verb forms. Wiktionary gives Icelandic verbs mostly by their principal parts: kemur, segir, finnst,
   gæti are not found (248 of the 786 misses among the 5,000 most frequent words). The Icelandic morphology database BÍN
   (CC BY-SA 4.0 with a required credit line) has them. (a, recommended) A forms-only dictionary built from BÍN (55k
   forms, about 0.2-0.5 MB, hosted): 84.3% → 89.5% of the top 5,000. (b) An own verb ending list: 38% of the missed verb
   forms, 3% wrong, no download, strong verbs (kemur, fer) still missed. (c) Both, the list until BÍN is hosted. (d)
   Neither. The plan assumes (c); (a) alone drops the list.
9. Answered (owner, 2026-10-21): (a). Icelandic typed without þ ð æ ö and the acutes. The written sources show little of
   it (ad for að is rank 4,156, að rank 4); keys without marks would make 58 keys of the 5,000 most frequent words
   shared (á/a, að/ad, vel/vél, fór/for/för). (a, recommended) No keys without marks. (b) Keys without marks, after the
   exact match (thad → það needs th → þ besides). Changing it adds an import key and its tests, as Slovak's.
10. Deferred (owner, 2026-10-21): asked again with the emulator measurement of ML Kit, Tesseract and cloud. Icelandic
    letters misread by OCR that are real letters elsewhere: Paddle v5 read Þ as P on every screen (v6 right), v6 read Æ
    as AE on every screen. P and "ae" also occur in Icelandic words, so no one-for-one fix. (a, recommended) Wait for
    the emulator run of ML Kit, Tesseract and cloud; if an engine that is offered keeps doing it, try Þ for P and Æ for
    AE for OCR words only when the word as read is not found. (b) No fallback; mark the engine that does it as less
    accurate. Blocks: the Icelandic OCR fallback. (Đ → Ð and ž š → ð follow group 5a's decision "OCR letter fixes" and
    are planned.)
11. Answered (owner, 2026-10-21): (a). Afrikaans typed without ê ë î ô û ï. 4.6-18% of the uses of common marked words
    are typed without them (wereld for wêreld 6.6%, reel for reël 18%); keys without marks share 25 keys of the 5,000
    most frequent words (se/sê, voel/voël, hoe/hoë). Emphasis acutes (dít, nóg, ná) are also looked up without them (dié
    and ná are words of their own, so the exact match comes first). (a, recommended) Keys without marks, the exact match
    first, as Slovak (decision "Words without their marks"). (b) As written only. Changing it removes an import key.
12. Answered (owner, 2026-10-21): (a). Dutch dictionaries for Afrikaans. English Wiktionary lacks many basic Afrikaans
    words (om, na, weer, dus, terwyl): af-en + af-ru find 71.1% of the 1,000 most frequent words and 46.6% of the top
    5,000. With the user's Dutch dictionary 88.4% and 70.3%, about 4% of the Dutch hits false friends (verken "explore"
    → varken "pig"); the gain is about that of Slovak → Czech. (a, recommended) When an Afrikaans word has no entry, the
    Dutch dictionaries the user has are searched as written and their entries marked Dutch; Dutch's dictionaries offered
    unticked on Afrikaans's download screen. (b) As (a) plus respelling Afrikaans as Dutch (y → ij, sk → sch, -tjie →
    -tje): about 5 points more, about 12% of those hits wrong. (c) No fallback. Changing (a) to (c) removes the fallback
    entry for Afrikaans.
13. Answered (owner, 2026-10-21): (a). FreeDict afr-eng (GPL-2.0+, Ergane word lists, 5,129 entries): with af-en and
    af-ru 71.1% → 85.0% of the top 1,000, with Dutch as well 93.1%. (a, recommended) Convert it and list it unticked
    once hosted, with the GPL source offer as for the FreeDict pairs. (b) Not offered. Blocks: one catalog entry.
14. Answered (owner, 2026-10-21): (a); the agent files the issue from the owner's account after showing the owner its
    text. A Tatar dictionary. wty has no Tatar at all (it has Bashkir); Russian Wiktionary has 11.8k Tatar words, the
    English one 1.3k Cyrillic words. wty added Kyrgyz a day after a user asked (issue #334). (a, recommended) The owner
    asks wty to add Tatar (an issue at yomidevs/wiktionary-to-yomitan); the catalog then lists tt-ru and tt-en without
    code of ours; our own converter only if the request is refused. (b) Our own kaikki → Yomitan converter now, hosted,
    with CC BY-SA attribution. (c) Defer Tatar. Blocks: Tatar's dictionaries (the rest of the Tatar work can be
    prepared).
15. Answered (owner, 2026-10-21): (a). Tatar and Bashkir letters taken as their Russian twins (Kazakh's rule "он" shows
    он, оң, өң). Here more dictionary words share a key: Tatar 1.7%, Bashkir 2.2% (Kazakh 0.9%), because harmony pairs
    differ only in ә/а, ө/о, ү/у (булу/бүлү, тороу/төрөү). (a, recommended) As Kazakh: every match, the exact spelling
    first. (b) Only when the word as written finds nothing. (c) Exact letters only. Changing it is a flag in the support
    class.
16. Answered (owner, 2026-10-21): (a). Ending rules for Tatar and Bashkir. Form rows alone find 70-74% of Tatar and
    49-53% of Bashkir running text; group 4e's design with their tables 83-87% and 71-77%, right on 35 and 34 of 40
    samples. (a, recommended) Our own rules as for Kazakh. (b) Form rows only. Changing it drops the tables.
17. Answered (owner, 2026-10-21): (a). PanLex for Tatar and Bashkir (the CC0 mirror Kazakh's converter uses): 9.5k Tatar
    and 4.0k Bashkir words with a Russian translation, some wrong; +4-8 points of the top 2,000 words. (a, recommended)
    Both, into English and Russian, unticked, listed once hosted. (b) Ticked for Bashkir (its dictionaries are small),
    as Kyrgyz. (c) Neither. Changing it is a catalog entry each.
18. Answered (owner, 2026-10-21): (a). Flags (row "Flag country": one default flag per language). (a, recommended)
    Catalonia for Catalan, Galicia for Galician, Iceland, South Africa for Afrikaans, Tatarstan for Tatar, Bashkortostan
    for Bashkir, India for Sanskrit. (b) Andorra for Catalan (the only state where Catalan alone is official). (c)
    Russia for Tatar and Bashkir. (d) Namibia for Afrikaans. (e) No flag for Sanskrit (the code in a circle). Changing
    one is a drawable and a line.
19. Answered (owner, 2026-10-21): (a); for Tatar the owner asked for something more interesting, and Җ was kept after
    the other Tatar letters were compared (Ө and Ң taken, Ә looks like Azerbaijani Ə, Ү and Һ shared with the
    neighbours). Bubble letters. Taken: Ñ Ç ß È Ã Я Ł Ğ Ư Ā Ĳ Ř ID MS Ng Đ Ћ Ї Ў Ω Қ Ң, group 5's Ș Å Æ Ø Ë Ĉ Ô Ė Ŵ Á Ò
    Ä Õ Ő Ə Oʻ Ъ Ѓ Ө and the script letters. (a, recommended) Catalan Ŀ, Galician GL, Icelandic Þ, Afrikaans Ê, Tatar Җ,
    Bashkir Ҡ, Sanskrit सं. (b) Catalan CA, Galician X, Bashkir Ҙ, Sanskrit ॐ. Changing one is a string.
20. Answered (owner, 2026-10-21): (a). Sanskrit in Latin letters. Scholarly editions write IAST (dharmakṣetre), yoga
    texts write plain ASCII (asana); 15.1% of Sanskrit romanizations share a key with another word when the marks are
    dropped, and a Latin word without an IAST letter cannot be told from English. Converting IAST to Devanagari
    reproduces 86.7% of the headwords. (a, recommended) None at first: Sanskrit is looked up in Devanagari. (b) Latin
    words with an IAST letter converted to Devanagari before lookup. (c) Also Harvard-Kyoto and ITRANS, as a setting.
    Changing it adds a text processor (vidyut-lipi's or sanscript's table, MIT).
21. Deferred. Recognition models and their "more accurate" marks. Measured on the PC only (Paddle: Catalan and
    Galician 0.2-0.4%, Afrikaans 0.1%, Icelandic 1.5%; Tatar 0.5%, Bashkir 1.1%; Sanskrit 12.6% on Devanagari). ML
    Kit, Tesseract (cat glg isl afr san) and cloud are measured on the emulator on the same screens at implementation,
    then the recommendation rule picks. Blocks: the marks only (ML Kit stays main for the Latin languages and Sanskrit,
    Paddle `cyrillic` for Tatar and Bashkir).
22. Answered (owner, 2026-10-21): converted and hosted under the stricter license of the file header (CC BY-NC-SA 3.0),
    with the non-commercial mark of decision "Risky dictionaries"; the maintainers are not asked. For a Google Play
    build it moves to self-hosting or is removed (noted in `ai/notes/store-publishing.md`). Monier-Williams and the
    other Cologne Sanskrit dictionaries. MW (1899, 194k headwords) covers 93% of the lemmas of a lemmatized Sanskrit
    corpus, wty 66%. Its license is contradictory: the dictionary files' header says CC BY-NC-SA 3.0 (© 2014 The
    Sanskrit Library and Thomas Malten), the GitHub repositories say CC BY-SA 4.0; the book itself is in the public
    domain. (a, recommended) Ask the Cologne maintainers which applies; wty only meanwhile. (b) Convert and host it
    under the repositories' CC BY-SA 4.0 (about 10-20 MB, estimated). (c) The DCS lemma list instead (CC BY 4.0, 164k
    short English glosses of unclear provenance). (d) None. Blocks: any Sanskrit dictionary beyond wty.
23. Answered (owner, 2026-10-21): (b), the longest dictionary start, as it is 5 points below (a) for less than half the
    work. Second part, the agent's choice (owner): yes, after the first-word lookup is built and measured, since a tap
    on the second word is expected to find that word. Sanskrit sandhi. Sanskrit joins words in writing: तथा + इति is
    written तथेति, रामः + अपि रामोऽपि; compounds are one word. 21% of the words of a classical text sit inside a longer
    written word; today a joined word is found only as written, and only 37-53% of running-text words are found so. (a,
    recommended) Our own rules (word-end sandhi undone, the avagraha split, noun endings, then the first word with the
    join undone, shown as "part of the word"): right answers 53% → 78%, about 3-5 days. (b) The longest dictionary start
    only: 73% right, with wrong short pieces (कपिलो → कपि "monkey"), 1-2 days. (c) The Vidyut segmenter (MIT, Rust)
    through JNI: about 80 MB of data, 2-3 weeks, not better on the first word here. (d) None. A second part: should a
    tap inside a joined word start at the restored second word (एति → इति), 2-3 days more? Blocks: Sanskrit's lookup
    beyond the exact word.
24. Answered (owner, 2026-10-21): (a). Vedic accents (marks above and below the letters in Vedic editions; 0.01% of
    Wikipedia tokens, 60% of wty's form headwords). (a, recommended) Dropped in any text and at import, so accented and
    plain words match. (b) Kept significant. Changing it removes a `HeadwordMarks` entry and a processor.
25. Answered (owner, 2026-10-21): (a). Sanskrit into Russian. sa-ru (13.1k words) is wty's largest Sanskrit dictionary;
    Friš's reader vocabulary (7,671 Russian glosses, repository CC BY-SA 4.0, edition Moscow 2015) adds a little (Friš +
    wty ru 89.9% of the corpus words against 82.8%); Kochergina's dictionary is in copyright. (a, recommended) sa-ru
    only. (b) Friš converted once its license is confirmed. Changing it adds a converter.
26. Answered (owner, 2026-10-21): (a). Smaller items, one line each: (a, recommended) no Softcatalà English-Catalan
    conversion (CC BY 4.0, +28 of the top 5,000); no Valencian ending list (parle, cante, tinguera unfound; no Valencian
    corpus to measure); no Afrikaans ending list (20% wrong); no Tesseract for Bashkir (`kaz` lacks җ ҫ but has the
    Bashkir letters; it could be offered unticked); Latin Tatar not read (Cyrillic only); Sanskrit silent without a
    voice (a Hindi voice would read it only approximately). (b) Any of them done; the owner names which.

## Work

Per block, as group 5a's work list; detailed when the block starts.

- Every block runs in its own session, one after another on the shared emulator (owner, 2026-10-21): before building,
  the session measures on the emulator every engine and model offered for its languages (ML Kit, Tesseract fast and
  best, Paddle v5 and v6, cloud) and checks each rule's hypothesis on data; the numbers go into the block's plan.

- Shared: the fallback language (from group 5a's Slovak → Czech, generalized to a list if question 3 chooses (c));
  flags with hyphenated codes; the font letter lists without ŀ Ŀ ŉ and the Literata exclusion for Bashkir; the
  `paddle_ref.py` threshold options (testdata script, outside git).
- 6a: bundles (pt borrowed, `apostropheVariants`), elisions, l·l connector and its variants, `wordBefore` with the
  dot, the Galician contraction split, the enclitic rule (question 4), the fallback (question 3), catalog (`SOURCES`:
  the passing pairs, ipa; WikDict cat-rus converted), frequency (`large_ca`; Galician Leipzig), tessdata cat glg,
  Paddle v5 latin and v6, flags es-ct es-ga, quotes and abbreviations, strings in all 14 locales.
- 6b: bundles (sv and nl borrowed, Ukrainian's apostrophe processor for af), the 'n word start, Icelandic OCR letter
  fixes, `compoundLinks`, `ordinalPeriod` for is, BÍN converter (question 8; no own verb list), keys without marks for
  Afrikaans, the Dutch fallback, FreeDict afr-eng (question 13), Arabic-script form rows dropped (die → دي),
  frequency (`small_is`; Afrikaans Leipzig), flags is za.
- 6c: Tatar and Bashkir tables in group 4e's engine (both verb dictionary forms for Tatar), letter keys, legacy Tatar
  letters, OCR-only look-alikes (Bashkir Ç ç → Ҫ ҫ), particle variants, digit-joined forms, abbreviations and quotes,
  `paddle-cyrillic` gains tt and ba, Leipzig sources (Tatar and Bashkir Wikipedia only), PanLex (question 17), the
  Tatar dictionary (question 14), flags ru-ta ru-ba.
- 6d: the Sanskrit bundle, import cleanup and keys, the longest-start sandhi lookup, then the restored second word
  (question 23), Monier-Williams converted with its non-commercial mark (question 22), sentence ends, scan length 24,
  tessdata san, `ocrWord` S s 5 → ऽ, the Devanagari font, flag in, strings.
- Tests per language as group 5a's (the measured examples as positive and negative tests, rows of
  `notes/language-rules.md` with their reading source), catalog and frequency script tests, docs (`docs/usage.md`,
  `docs/architecture.md`), NOTICE (wty, Leipzig, BÍN's credit line, WikDict, FreeDict, PanLex, flags).
- Test model: new cases after group 5's; emulator checks under the lock: ML Kit, Tesseract and cloud on the PC
  screens of this research (regenerated: the screens and scripts were in the research scratchpad), the voices list
  for af and gl, the Vedic signs in the system Devanagari font, Gboard's Tatar and Bashkir layouts, the lookup time of
  the Sanskrit rules on long lines.

Effort (estimated): 6a about two sessions (Galician's rules and the fallback the most); 6b about two (BÍN converter,
the fallback reused); 6c about a session once group 4e's engine exists, plus the Tatar dictionary; 6d two to three
(sandhi rules, import cleanup, fonts).

## Progress

- 2026-10-20: research written by an autonomous session (five research helpers in parallel; nothing measured on a
  device); 26 questions recorded, 10 deferred and 16 decided provisionally. Not started: the build order is the
  owner's decision.

## Changelog

- 2026-10-20: plan written (request 70 of the languages plan).
- 2026-10-21: the owner answered the questions: 3 (a); 4 (a) only after a check of its false hits; 5 and 14 (a), the
  agent files both issues at wty after the owner has seen their text; 8 (a) without the own verb list; 10 deferred to
  the emulator measurement; 13 (a); 22 hosted under the file header's CC BY-NC-SA 3.0 with the non-commercial mark; 23
  (b), then the restored second word; the provisional ones as assumed (Җ kept for Tatar after a comparison). Each block
  runs in its own session, one after another, and measures every engine and model on the emulator before building
  ("Work"). Questions 10 and 21 wait for that measurement.

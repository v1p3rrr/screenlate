# Drafts

Work kept for a later session. Nothing here is part of the build: `build.mjs` and the bundles in
`dictionary/engine-hoshidicts/src/main/assets/yomitan-language/` do not read this folder.

## group-6-turkic.patch (2026-10-29)

Draft Tatar and Bashkir ending tables for the Turkic engine (`turkicTransforms` in `own-transforms.js`), with what the
group 6 recheck needed to build bundles for Catalan, Galician, Icelandic, Afrikaans, Tatar and Bashkir. Written by the
cloud session of 2026-10-29 for a coverage measurement, from the grammar notes under "Tatar and Bashkir" in
`ai/plans/2026-10-20-group-6-research.md`. Not reviewed. Kept here as a patch at the owner's word (2026-10-29), for the
session that builds block 6c; that session reviews it, finishes it and puts it into the real files.

### What it changes (against `languages/main` 23448dd3)

- `own-transforms.js`: the exports `tatarTransforms` and `bashkirTransforms`; and two changes to the shared engine:
  the hidden "dictionary form" transform takes up to six forms instead of four (a Tatar consonant stem needs -ырга,
  -арга, -у and the voiced -барга, -гарга), and the class after which the negative aorist's -с follows gains ә
  (`[мбп][аеоөә]`, for -мәс). The engine changes alter the Kazakh, Kyrgyz, Azerbaijani and Uzbek bundles too (kk.js
  65,572 → 65,902 bytes): rebuild them and run the glue tests.
- `build.mjs`: borrowed descriptors (ca and gl from pt, is from sv, af from nl, tt and ba from mn), `addedPreprocessors`
  (ca `apostropheVariants`, af `ukrainianApostropheVariants`), the six languages in `foldedMarkLanguages`, tt and ba in
  `addedTransforms`. The KDoc of those tables is not updated, and `LanguageSupport.foldsMarks` in Kotlin must list the
  same languages (a test keeps the two lists equal).
- `glue.js`: tt and ba in `markedLanguages`, `taggedRules` (verbs match definitions tagged v), `untypedRules` (as
  Kazakh) and `skippedByNominalRules` (Tatar ул, бу, шул; Bashkir ул, был, шул).

### Use

```bash
git apply scripts/yomitan-language/drafts/group-6-turkic.patch
node scripts/yomitan-language/build.mjs <yomitan checkout at yomitan-commit.txt> <out dir> tt ba
node scripts/yomitan-language/tools/coverage.mjs <out dir>/tt.js tt tat_news.tsv cyrillic wty-tt-ru.zip wty-tt-en.zip
```

(`tools/freq_list.py` writes the word list; the dictionaries are wty's release files.)

### Measured with it

In the plan's section "Recheck against languages/main": Tatar news 43.4 / 58.9 / 83.1% of lowercase Cyrillic tokens
(headword / + form rows / + these rules) with tt-ru and tt-en; Bashkir Wikipedia 40.2 / 51.0 / 75.0% with ba-en, ba-ru
and ba-pl; of 40 rule hits drawn by frequency 37 (Tatar) and 38 (Bashkir) right; 17 and 10 candidates a word on average
over the 5,000 commonest words, up to 246.

### Not done (block 6c's list)

- No plural after a finite verb (алдылар, баралар, барҙылар): the engine's `plural` slot follows only nominal and
  participle texts. 0.18% of Tatar news tokens in the past tense alone.
- Question 27 of the plan (owner, 2026-10-29): beside a word's own entry, verb-ending results are shown, noun-ending
  results only where that noun can take that ending (vowel harmony, the stem's last letter, the ending written with the
  language's own letters rather than a Russian twin such as н for ң). The filter is not written.
- The tables accept every harmony variant after any stem (-ләр after a back-vowel stem) and were not checked against
  a grammar or by a speaker. Bashkir's infinitive in -ырға (йоҡларға) is reached only as aorist + dative.
- The new rule names (until, agent, future, verbal noun, when) need entries in `res/values*/inflections_turkic.xml` in
  the 17 locales (`generate-inflections.mjs`).
- Not in the patch: `TatarSupport` and `BashkirSupport`, the Russian-twin keys (`headwordMarks.replacements`,
  `keyedSpelling`, as Kazakh), Җ җ in the fold tables (`own-processors.js` and `archive_split.cpp`), the form tag
  names of question 30 (deferred for later), tests.

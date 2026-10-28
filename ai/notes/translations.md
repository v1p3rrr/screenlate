# Translations

UI strings live in `values*/strings.xml` of `app`, `overlay`, `core/anki` and `dictionary/api`, and the Japanese
inflection names and descriptions shown in the popup in `values*/inflections_ja.xml` of `dictionary/engine-hoshidicts`
(GPL, from Yomitan through hoshidicts; `tools/generate_inflections.py` writes only the English file, translations are
edited by hand). 14 locales: English (`values`), ru, de, es, fr, it, ja, ko, pl, pt, tr, vi, `b+zh+Hans`, `b+zh+Hant`.
`TranslationsTest` finds every string file under `values/` of every module and checks that every key exists in every
locale with the same format arguments, that plurals have the CLDR categories of their locale, and that apostrophes,
quotes and line breaks are escaped.

Inflection terms per language follow the school grammar of Japanese taught in that language: 連用形 continuative
(ru соединительная форма, ko 연용형, vi dạng liên dụng), 未然形 irrealis, 仮定形 hypothetical, 終止形 dictionary form,
命令形 imperative, 連体形 attributive; the kanji term stays in parentheses where the local term differs from it
(not in ja and zh).

## Format pitfalls

- A literal line break inside `<string>` is collapsed to a single space by aapt2 (checked with
  `aapt2 dump resources`): paragraphs need `\n`, so "\n\n" between paragraphs of ⓘ texts. The test fails on a raw
  line break.
- `'` and `"` must be escaped (`\'`, `\"`); typographic quotes and apostrophes need no escape.
- Plural categories: ru/pl one, few, many, other; de/tr one, other; es/fr/it/pt one, many, other; ja/ko/vi/zh other.

## Conventions per language

- Address: de "du", es "tú", fr "vous", it "tu", pl "ty", pt "você", tr "siz", ru "вы".
- pt is Brazilian: "app" (masculine), "celular", "Excluir", "Baixar", a switch is a "chave".
- fr: "appli", "paramètres", typographic apostrophe ’, a space before ":" as in the rest of the file.
- Quotes: it “”, es «», de „“, pl „”, zh-Hans “”, zh-Hant 「」, ja 「」, ko ‘’ or “”.
- Popup: es "ventana emergente", fr "fenêtre contextuelle", pl "okienko", tr "açılır pencere", vi "cửa sổ bật lên",
  zh-Hans 弹窗, zh-Hant 彈出視窗. vi accessibility: "hỗ trợ tiếp cận". zh-Hant uses 詞典 for dictionary, never 字典.
- ja: half-width spaces around Latin words and digits ("Screenlate を開く", "2 回"), full-width "：" after labels.
- ko: the phone is "휴대폰" (not 휴대전화).
- Same thing, same word across screens: the bubble's dock setting is the "side" or "edge" (not a "dock"); text
  weight (font weights) and letter thickness (an outline) are different words; scan length has one name everywhere;
  the lookup feature "Kanji of the word" is named the same in summaries; a dictionary from a backup "replaces its
  installed version".

## Tone (2026-10-14 audit)

UI texts, hints and docs use the dry tone of software documentation (request 40 of the languages plan). Wording
settled in that audit, to reuse rather than reinvent:

- Defaults: "are reset to their defaults" / «будут сброшены к значениям по умолчанию», never "go back to how they
  were after installation".
- Services are not people: "requests to it are paused for a few minutes" / «запросы к нему приостановлены на
  несколько минут» (not "it rests"); "the next one is used" (not "takes over"); "was not found in AnkiDroid" (not "is
  gone").
- Progress texts are nouns, not first person: «Распознавание…», «Остановка импорта и удаление словарей…».
- Purpose of an option: "Intended for …" / «Предназначено для …»; a recommendation: "Recommended: Both" / «Рекомендуется
  «Оба»».
- Errors: "Unknown error." / «Неизвестная ошибка.», "The request timed out." / «Время ожидания ответа истекло.»,
  "Cloud recognition server error (%1$d)." / «Ошибка сервера облачного распознавания (%1$d).».
- The keep-alive card is "Persistent notification" / «Постоянное уведомление»; its switch "Show a notification while
  the service runs".
- Dictionary languages: "Language of the words" / "Language of the definitions" («Язык слов» / «Язык толкований»).
- A tone-only change to English may leave the other locales as they are when their wording is already neutral; a
  change of meaning goes to all 14.

## Tools used for the 2026-10-01 audit

Side-by-side dumps of English and the locales per key and a script that replaces whole string values were kept in
the session scratchpad only; the steps are simple enough to redo: parse each `strings.xml`, compare by key, and write
values back with `xml.sax.saxutils.escape` plus the Android escapes above. The 2026-10-14 tone audit did the same,
with new texts kept in Python dicts (`{key: {locale: text}}`) and `¶` standing for the literal `\n\n` paragraph break,
so no backslashes pass through tool inputs.

## Candidate interface languages (research, 2026-10-29)

Which of the languages the app reads (`core.common.Language`, 52 now, and the planned groups 5c-5f and 6: az, uz, bg,
mk, mn, yue, ta, te, kn, mr, ur, ca, gl, is, af, tt, ba, sa) are worth an interface translation after the 14 locales.
Written by the cloud session of 2026-10-29 from web sources (below); the owner decides the order.

What one more locale costs: about 1,000 strings of `strings.xml` and `installation.xml` (about 6,800 English words,
nearly all in `app`), 313 form-of tag names (400 words) and 721 inflection names and descriptions (4,900 words,
a third of them the Japanese ones), checked by `TranslationsTest` once the locale is in its plural table; the CLDR
plural categories (Arabic six, the Slavic and Baltic languages three or four, most others one or two); for the
right-to-left languages also the mirrored screens. The agent's translation is fluent for the large languages and
weaker for the small ones; nobody on the project reads any of the candidates, so quality depends on users' reports.

Who would use it: the app is a pop-up dictionary for reading foreign text on an Android screen, so the users are
learners of Japanese, English, Korean, Chinese and the other languages it reads, whose own language is the interface.
A locale matters where such learners are many, use Android, and keep their phones in their own language rather than
in English (or in Russian, Turkish or Spanish, which many speakers of a candidate read as well).

Ranked, with the data behind each (speakers: Ethnologue, total L1 + L2; Android: StatCounter mobile OS share,
September 2026; Japanese learners: Japan Foundation survey 2024):

1. **Indonesian (id).** 255 M speakers; Android 79%; the second-largest number of Japanese learners abroad (732,914,
   after China) and a large Korean and English learner base; phones are normally used in Indonesian. No plurals
   (`other` only), Latin script: the cheapest translation of the list, and Malay readers (Malaysia, Brunei) mostly
   understand it.
2. **Thai (th).** 71 M; Android 74%; fifth in Japanese learners (194,366); English is little used in phone
   interfaces. No plurals; Thai has no spaces between words, so long strings need a look on the device (Android's
   line breaking handles Thai). Moderate effort.
3. **Ukrainian (uk).** About 30-40 M; Android 62%; many learners of English, German, Polish and Japanese, and since
   2022 a strong wish for a Ukrainian interface instead of the Russian one. The Russian strings are a close start (the
   same plural categories, similar grammar), so the effort is low.
4. **Arabic (ar).** 335 M (Modern Standard Arabic is everyone's second language); Android 83% in Egypt, 48% in Saudi
   Arabia; a large learner base for English, Japanese and Korean. Six plural categories, right to left: the strings
   are done on `cloud/rtl-strings-2026-10-29`, the mirrored screens are left.
5. **Persian (fa).** 82 M; Android 88% in Iran, where Google Play is not offered, so an APK from GitHub is the usual
   way to get apps; English learning is common. Plurals one/other, right to left: strings done on the same branch.
6. **Hindi (hi).** 611 M; Android 94% in India; growing Japanese and Korean interest. Many smartphone users who would
   learn languages keep their phones in English, so the gain is smaller than the speaker count suggests. Devanagari,
   plurals one/other; moderate effort.
7. **Bengali (bn).** 274 M; Android 93% in Bangladesh, where phones are used in Bengali more often than Indian ones
   in Hindi; Japanese is learned for work programs. Moderate effort.
8. **Uzbek (uz, planned in group 5c).** About 35-45 M; Android 82%; many learn Korean (the EPS-TOPIK work program) and
   Japanese for work abroad. Many read Russian, which covers part of them. Latin script, plurals one/other.
9. **Urdu (ur, planned as group 5f).** 246 M; Android 90% in Pakistan; English interfaces are common among the users
   who would learn languages. Right to left, reusing the Arabic, Persian and Hebrew work, with Nastaliq text in the
   interface to check.
10. **Hebrew (he).** About 9-10 M; Android 71% in Israel; high English proficiency. Small, but its strings are done
    with Arabic and Persian (plurals one, two, other).
11. **Romanian (ro), Hungarian (hu), Greek (el), Czech (cs).** 24 M, 13 M, 13 M, 10 M (Slovak readers understand
    Czech); Android 78%, 73%, 72%, 69%; moderate English proficiency and steady Japanese and Korean interest. Czech
    has four plural categories, the others two or three.
12. **Malay (ms), Filipino (tl).** Malaysia: Android 55%, interfaces mostly in English or Malay (Indonesian is close);
    the Philippines: Android 87%, but phones are mostly in English. Low gain.
13. **Tamil, Telugu, Marathi, Kannada (planned in group 5e).** 86 M, 96 M, 99 M, 59 M; Android 94% in India; as with
    Hindi, the users most likely to learn languages mostly use English interfaces.
14. **Dutch (nl), Swedish (sv), Norwegian (no), Danish (da), Finnish (fi).** Very high English proficiency, and the
    Nordic markets are iPhone-heavy (Sweden: Android 44%; the Netherlands 68%). Low gain.
15. **Covered by a neighbor's locale:** Belarusian, Kazakh and Kyrgyz speakers mostly read Russian (Kazakhstan:
    Android 69%), Azerbaijani speakers Turkish (Android 84%), Slovak Czech, Malay Indonesian, Catalan and Galician
    Spanish, Cantonese the Traditional Chinese locale. Catalan stands out for loyalty to its own interface but has
    about 10 M speakers.
16. **The rest** (bg, mk, sr, hr, sl, lt, lv, et, ka, hy, km, mn, sq, sw, is, af, tt, ba, cy, ga, gd, eo, la, sa):
    small audiences, English or another locale commonly used (Swahili users in Kenya, Android 95%, mostly keep
    English), or few learners who would read with a pop-up dictionary. Khmer: Cambodia is iPhone-heavy for the
    region (Android 64%).

Outside the read languages, Burmese would be the strongest case (Myanmar is ninth in Japanese learners, 100,315),
but the app does not read Burmese.

Sources: Ethnologue 2026 figures as tabulated in Wikipedia's "List of languages by total number of speakers";
StatCounter Global Stats, mobile OS market share per country (gs.statcounter.com/os-market-share/mobile/<country>,
September 2026); Japan Foundation, Survey on Japanese-Language Education Abroad 2024 (4,000,750 learners; country
figures as reported by esuj.gr.jp); Duolingo Language Report 2025 (Japanese the fourth most studied language) and
Duolingo's course expansion of 2025-04-30 (courses from Hindi, Bengali, Telugu, Tamil, Thai, Indonesian, Tagalog).
Speaker numbers for languages under 50 M are common estimates, not Ethnologue's table.

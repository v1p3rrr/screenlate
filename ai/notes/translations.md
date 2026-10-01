# Translations

UI strings live in `values*/strings.xml` of `app`, `overlay`, `core/anki` and `dictionary/api`, in 14 locales:
English (`values`), ru, de, es, fr, it, ja, ko, pl, pt, tr, vi, `b+zh+Hans`, `b+zh+Hant`. `TranslationsTest` checks
that every key exists in every locale with the same format arguments, that plurals have the CLDR categories of their
locale, and that apostrophes, quotes and line breaks are escaped.

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

## Tools used for the 2026-10-01 audit

Side-by-side dumps of English and the locales per key and a script that replaces whole string values were kept in
the session scratchpad only; the steps are simple enough to redo: parse each `strings.xml`, compare by key, and write
values back with `xml.sax.saxutils.escape` plus the Android escapes above.

# Changes since v0.2.3

This inventory compares the `v0.2.3` tag with the multilingual `main` at `42f9760e`. The condensed publication text is in [`.github/release-notes/v1.0.0.md`](../../.github/release-notes/v1.0.0.md).

## Languages and setup

- Added 61 lookup languages alongside Japanese. The [README](../../README.md#supported-languages) lists all 62.
- First launch offers a learning language and a choice of dictionaries and local recognition models. An upgrade from v0.2.3 keeps Japanese and its installed dictionaries.
- The Languages screen can enable, disable and star languages and choose their flags. Disabled languages can retain their downloaded files and settings for later use.
- Home and the floating or docked bubble's hold menu switch between the active language and up to two starred languages. Flags shared by several languages carry language codes. E-ink mode uses codes in place of flags.
- Lookup, popup, Anki and audio settings are saved per language. The active language is used for screen scans and Search. Camera remembers its own language and uses the language of the saved recognition for photo lookups.
- Home shows whether the active language is ready, including missing dictionaries or recognition models, installations in progress and failed downloads.
- Croatian and Serbian have separate profiles that share the same dictionaries. Serbian lookup accepts both Latin and Cyrillic text.

## Camera and saved images

- The Camera tab takes photos, imports images from the system picker and accepts an image shared from another app.
- The bubble looks up words directly on a photo. Pan and zoom reuse the saved recognition, and the popup follows the word as it moves.
- Camera shows recognition progress and offers a retry for missed regions. High-accuracy recognition reads large images in overlapping tiles to improve recognition of small text.
- The private photo history retains images and their recognized text. Save manually or automatically, limit the history by count or size, and reopen images for lookup.
- Save to gallery exports a separate copy. Anki cards can attach a crop or the whole photo using the original image.

## Screen and photo recognition

- The built-in ML Kit recognizers now cover Latin, Devanagari and Bengali text alongside Japanese, Chinese and Korean.
- Downloadable PaddleOCR and Tesseract models can be selected per language. Tesseract offers fast and accurate variants where supported. PaddleOCR includes models for Thai, Devanagari, Arabic and Persian, Tamil and Telugu alongside the other available scripts.
- Text recognition has its own settings page with cloud, on-device and combined modes, and a model picker for each enabled language.
- Downloadable model updates appear with dictionary updates. The current model remains usable during an update. Damaged models can be downloaded again from Home.
- Recognition and selection account for right-to-left text, scripts without spaces, and language-specific punctuation, character variants and line breaks.

## Dictionaries and lookup

- The catalog covers every supported lookup language. It groups dictionaries by the language of the entries, the language of the definitions and the kind of dictionary.
- Fresh Japanese setups download dictionaries such as JMdict and KANJIDIC from the catalog. Korean hanja data ships with the app.
- The catalog includes main dictionaries and, where available, separate word forms, frequencies, phonetic transcriptions, short translations and character dictionaries. Some frequency lists are converted into dictionaries on the device after download.
- Downloads wait for connectivity, resume after interruptions where the server supports it, and retry stalled transfers. Two tasks can run in parallel, with imports processed one at a time. The queue shows waiting, running and failed tasks with cancellation or resume controls.
- Language setup shows archive download size, expected installed size and whether there is enough free space. It asks before large downloads over mobile data.
- The Dictionaries page uses per-language tabs and individual removal controls. The old reset that removed all dictionaries has been removed.
- Lookup handles inflections, word forms and spelling variants with rules suited to the language. Examples include German compounds, English phrasal verbs, Celtic initial mutations and inflections in Turkic and Indic languages.
- Many Latin- and Cyrillic-script languages can match entries when diacritics are missing or misread, with exact spellings ranked ahead of alternatives. Japanese lookup now follows dictionary entries that point from a form to its headword.
- Search accepts pinyin for Chinese and Jyutping for Cantonese. Both support Simplified and Traditional characters, character entries and optional tone colors over headwords. Cantonese supports Hong Kong character forms.
- Korean lookup includes hanja entries and hangul readings. Japanese pitch-accent patterns can be shown in different colors.
- Phonetic transcriptions can show regional variants. Search and popup entries can use script-specific fonts, including additional downloadable fonts.

## Anki, pronunciation and translation

- AnkiDroid's connection check shows app access, deck, note type and field-template status.
- Anki fields support Chinese and Cantonese character variants and readings, Russian stress marks, and phonetic transcriptions where available. Suggested templates handle right-to-left text.
- Audio sources and voices follow the language profile. Wiktionary and Lingua Libre recordings are available for supported languages, with region priority for recordings and transcriptions where applicable.
- Sentence translation uses the source language of the lookup. The popup translation button is off by default on fresh installations. Existing settings carry over on upgrade.

## Navigation, interface and backups

- The bottom bar opens Home, Search, Camera and Settings. Each tab keeps its page or search text while another tab is open.
- Text recognition, audio and the list of apps where the bubble is hidden have dedicated settings pages.
- Appearance can set the interface language. Arabic, Persian and Hebrew bring the interface to 17 languages and mirror the layout.
- Backup creation can select settings sections, fonts and dictionaries individually. Language selection carries the chosen profiles, settings and recognition models into backup and restore. Earlier Japanese-only backups are supported.
- Yomitan settings and collection import are together on the backup page.

## Stability and performance

- Reduced popup layout work before results appear and moved search result preparation away from the input thread.
- Improved recognition of tilted text, vertical columns and wrapped or fragmented lines.
- Fixed bubble gesture and service rebind issues, camera viewer and photo storage edge cases, and dictionary and audio recovery issues. The publication notes group these fixes in one sentence.

## Release scope

- The `ocr-models-1` assets are already published and used by the catalog. The five additional recognizers were checked through the app's download and installation flow.
- The catalog audit found a main dictionary for all 62 profiles. Converted dictionaries awaiting hosting and the languages still in research are outside this inventory.

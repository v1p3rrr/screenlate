# Private dictionary archive

A copy of every file the app downloads, of the sources we convert and of the OCR models, kept on the owner's computer
so they survive their hosts removing them and can be shared privately while we have no hosting (plan Backlog, "Own
copies of the dictionaries"; open item "Hosting"). Built on 2026-10-19 from the catalog of `languages/main` (`3dfb14c`).

- Location: a git-ignored folder (`/dictionary-archive/` in `.gitignore`); the owner moves it where they like. The
  exact place is not recorded in the repository (public); the agent's memory has it, otherwise ask the owner. Nothing
  from it goes into git, `testdata/` or any upload.
- The refresh script lives inside the archive (`mirror/mirror.mjs`, Node 22+; git; Python 3 for the conversions), not
  in the repository (owner, 2026-10-19). It reads the catalog with `git show <ref>:…/dictionaries-v2.json` and the
  converters and `paddle_models.py` the same way, so it works from any checkout:
  `node <archive>/mirror/mirror.mjs --repo <Screenlate checkout> [--ref languages/main] [--sets …] [--list] [--verify]`.
  Its word-count rule copies `scripts/catalog/generate.mjs` (MIN_WORDS 1,500, COUNTED_BELOW 8 MiB, the gloss list);
  change both together.

## Layout

Each set has a `manifest.tsv` (path, size, SHA-256, URL, version, date, license, languages, what was checked, fetch
date, status) and a README. In all 1,083 files, 2.4 GiB on 2026-10-19; a repeated run downloads nothing new.

| Set | Contents | 2026-10-19 |
|---|---|---|
| `catalog/` | Every catalog file except OCR models, by source language (`<lang>/<file>`, index JSONs in `<lang>/index/<catalog id>.json`): wty main, glossary and transcription archives, Jitendex, JMdict and KANJIDIC editions, Kolobok, the Japanese frequency and kanji dictionaries, the Japanese Wikipedia, CC-CEDICT, KRDICT, STDICT, the wordfreq lists the phone builds frequency dictionaries from. `future/<lang>/`: the wty pairs of hi, ka, hy, bn, th, ar, kk, ky that pass the catalog threshold (`future/word-counts.tsv` keeps every candidate's count) and wordfreq's hi, bn, ar lists | 958 files, 1,893 MiB: 432 dictionary archives, 408 index files, 21 wordfreq lists; `future/`: 47 of 120 candidate archives passed (97 files with their index files and 3 wordfreq lists, 56 MiB) |
| `sources/` | FreeDict `*.src.tar.xz` (deu, eng, fra, ita, pol, spa, zho → rus) and our conversions (`freedict/converted/`), the OpenRussian CSV export of 2020-09-09 and our ru→en, ru→de conversions, Mueller's English–Russian dictionary (Debian source package `mueller` 2002.02.27-16, from snapshot.debian.org by SHA-1), Unihan 17.0.0 (hanja dictionary, Chinese script table) | 26 files, 80 MiB (9 of them our conversions) |
| `models/` | tessdata_fast and tessdata_best files at the catalog's commits, our `ocr-models-1` Paddle archives, the PaddlePaddle ONNX repositories they are converted from at the commits `paddle_models.py` pins; `future/`: tessdata of hin, kat, hye, ben, tha, ara, kaz, kir and Paddle's devanagari, arabic and th recognizers | 99 files, 500 MiB |

## Checks

A file counts as archived only after it matched what its host publishes: Hugging Face's LFS SHA-256 (or git blob
SHA-1 for small files), a GitHub release asset's SHA-256 digest, the git blob SHA-1 of a raw GitHub file, the
catalog's pinned SHA-256 (wordfreq, tessdata, Paddle), FreeDict's SHA-512, Debian's SHA-1; every zip is also read
through with its CRCs compared, every JSON parsed. Files without any published hash rely on the size and the zip
and JSON checks: GitHub release assets uploaded before GitHub kept digests (KRDICT, STDICT, Kanjium, JPDB, BCCWJ, the
Japanese Wikipedia), Jiten's frequency list (no size either), the jitendex.org and Kolobok index files.

## Refresh

wty is rebuilt about weekly, Jitendex monthly, JMdict and CC-CEDICT daily; the catalog changes with each language. A
run downloads only what changed (the four files with neither a published size nor a hash, Jiten's list and the index
files of Jitendex, Kolobok and Jiten, are fetched every time; about 8 MB), replaces a file only after the new copy
passed its checks, and keeps files their source no longer lists (status "kept; no longer listed since …"), as the
owner chose (2026-10-19). The future languages' candidates are recounted only when their archive changed. A converted
archive is rebuilt when its source or its converter's blob changes.

## Sharing and licenses

- CC BY-SA (Wiktionary, JMdict family, Jitendex, Kolobok, CC-CEDICT, wordfreq, Wikipedia, KANJIDIC, Kanjium, JLPT,
  FreeDict/WikDict, OpenRussian), BSD-3-Clause (mozc kanji variants), Unicode License v3, Apache-2.0 (tessdata,
  PaddleOCR): shareable with attribution and the license; ShareAlike for derivatives (our conversions).
- Mueller: GPL-2.0-or-later; share with the source, which the Debian source package is.
- No license for redistribution: JPDB v2.2, Aozora Bunko and jitai ("Published by the authors"), the NINJAL lists
  (BCCWJ, NWJC, CSJ, CEJC: NINJAL's terms, not an open license). Keep them for our own use; do not pass them on.
- KRDICT and STDICT: the NIKL data is CC BY-SA (third parties say 2.0 KR), but the Yomitan packaging (Lyroxide) states
  no license of its own (`languages.md`, "Catalog candidates and licenses").

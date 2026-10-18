# OCR engines: outside research (2026-10-17)

What public sources say about ML Kit text recognition v2, Tesseract 5 (LSTM, `tessdata_fast` against `tessdata_best`) and
PaddleOCR PP-OCRv5 mobile. Written for the on-device engine work (`2026-10-17-ocr-engines.md`); our own numbers are in
`ocr-engines.md` ("Engine comparison"). Read-only research: nothing in the code was changed. Pages were read on
2026-10-16/17; "main" links point at the repositories' default branches and can move.

Source types: **D** official documentation or model card, **C** source code, **I** issue or maintainer statement,
**P** paper or preprint (arXiv papers are not all peer reviewed), **B** blog or vendor page (weak). A number from a
source of a different version or a different task is marked as such.

## Key findings

1. No public benchmark runs all three engines on screenshots, game text, subtitles or comics. The comparisons that exist
   (section 1) put Google Cloud Vision first on most sets where it is included, PaddleOCR ahead of Tesseract on signs
   and on most document sets, and ML Kit ahead of Tesseract on mobile word crops. Those sets are photos and documents; our clean
   rendered screens are the case that favours Tesseract, so the order may change on the hard screens.
2. PaddleOCR's Latin recognizer cannot output 45 of the 61 lowercase Vietnamese letters that carry a tone mark or a
   stroke (checked in the shipped model's character list). PP-OCRv6 has the same hole and the maintainers closed the
   report as work for a future model. This explains the 13.5% CER on Vietnamese in our table.
3. PaddleOCR's Cyrillic and East Slavic character lists each contain 115 Latin letters on purpose; a maintainer says
   removing them needs retraining. This explains words written in Latin look-alikes.
4. PaddleOCR (the classic scripts and PaddleX 3.x alike) turns every line crop taller than 1.5× its width by 90°
   before recognition. Vertical text is therefore read as rotated horizontal text. The publisher's own vertical score
   for the mobile recognizer is 0.81 (server 0.93).
5. Tesseract's LSTM character boxes are estimated from the network's time steps (an issue open since 2021 reports them
   as inaccurate), and Thai and CJK text is split into one "word" per character in hOCR, TSV and ALTO output (open since
   2019). Both fit our unusable tap positions on CJK (26–62%) next to a low CER.
6. `tessdata_fast` against `tessdata_best`: the author of the LSTM engine put fast at under 5% worse; an independent
   run on a 2560×1440 screenshot found best 1.5× slower with about the same word count. No public per-language table exists.
   Our Bengali, Greek and Armenian gaps are not documented anywhere; the only hint is that the fast set was trained with
   fewer fonts for non-Latin scripts.
7. Tesseract 5 retries a text line on the inverted image when the line's mean confidence is below 0.7, so light text on
   dark is not a hard failure; the docs still advise dark text on light. The documented size limits are an x-height of
   about 20 px or more (below 10 px there is "very little chance") and about 30 px at most for the LSTM engine.
8. PP-OCRv6 (June 2026) covers Chinese, English, Japanese and 46 Latin-script languages in one model. It has no
   Korean, Cyrillic, Greek, Arabic, Thai or Devanagari recognizer, so v5 stays the source for those scripts.
9. ML Kit's text models were last changed in May 2023 (Android release notes); since then only repackaging. Google
   publishes no accuracy or latency figures, only "real-time for Latin, slower for the others".
10. Licenses are permissive for everything except ML Kit: Tesseract, tessdata (all three sets), PaddleOCR, PP-OCRv5
    weights and MNN are Apache-2.0, ONNX Runtime is MIT. Converted model files may be redistributed under
    Apache-2.0 §4 (license text, changes marked, notices kept).

## 1. Published comparisons

| Source | Engines | Data | Result | Caveat |
|---|---|---|---|---|
| [SIBGRAPI 2025 workshop paper](https://sol.sbc.org.br/index.php/sibgrapi_estendido/article/view/38292) (P) | ML Kit, Tesseract (iOS: Apple Vision) | 848 real word crops from urban scenes; Pixel 7 emulator, Android 16 | CER: ML Kit about 10.5% best (grayscale), 12.2% unprocessed; Tesseract about 22.7% best (grayscale), 27% unprocessed. Word accuracy 80% against 64%. Adaptive binarisation and morphology made both worse (ML Kit to 30% CER, Tesseract to 49%) | Table text garbled in the PDF; Tesseract model set not named; emulator; Portuguese |
| [arXiv 2602.02223](https://arxiv.org/abs/2602.02223) (P, Feb 2026) | Google Vision, PaddleOCR 3.x, EasyOCR, Tesseract | Printed signs on one board, 1–7 m, 0–30°, three cameras | Levenshtein-ratio accuracy, main camera: Google Vision 0.967, PaddleOCR 0.917, EasyOCR 0.828, Tesseract 0.380; ultra-wide 0.887 / 0.788 / 0.655 / 0.190 | One board; no CER, no latency; Tesseract settings and PP-OCR version not given |
| [DocOCR-Eval, arXiv 2607.16203](https://arxiv.org/abs/2607.16203) (P, 2026) | PaddleOCR, EasyOCR, Tesseract, Cloud Vision (+ VLMs) | 50 images per set: zh exam papers, en forms and receipts, fr prescriptions, XFUND | ANLS (Paddle / Tesseract / Cloud Vision): zh 0.20 / 0.02 / 0.44; ja 0.46 / 0.45 / 0.62; en forms 0.42 / 0.23 / 0.29; en receipts 0.51 / 0.38 / 0.56; fr prescriptions 0.66 / 0.52 / 0.29. The authors conclude that no engine wins on every set | 50 images per set; two XFUND rows identical (copy error?); versions not given |
| [KITAB-Bench, arXiv 2502.14949](https://arxiv.org/abs/2502.14949) (P, Feb 2025) | Tesseract, EasyOCR, PaddleOCR | Arabic, 3,760 samples (and the 500-sample PATS set) | CER 0.54 / 0.58 / 0.79; on PATS Tesseract 0.14, PaddleOCR 0.77 | Predates the PP-OCRv5 Arabic recognizer (July 2025); PaddleOCR version not stated, so it does not contradict our Arabic result |
| [Sinhala and Tamil, arXiv 2507.18264](https://arxiv.org/abs/2507.18264) (P, 2025) | Tesseract 5.5.0, Cloud Vision, Document AI, Surya, EasyOCR | Synthetic rendered lines, 7,000 each | CER Sinhala: Surya 0.8%, Cloud Vision 6.2%, Tesseract 7.0%. Tamil: Cloud Vision 0.8%, Tesseract 7.8% (WER 61% against 12%), EasyOCR 11.7% | Synthetic; PaddleOCR not run |
| [Thai registration certificates, IJECE 2021](https://ijece.iaescore.com/index.php/IJECE/article/view/25537) (P) | Tesseract, Cloud Vision | 84 colour images of one document type | Accuracy 47.0% against 84.4% | One document type; old Tesseract |
| [HalalBench, arXiv 2604.22754](https://arxiv.org/abs/2604.22754) (P, Apr 2026) | ML Kit v2, docTR, EasyOCR, RapidOCR (PP-OCR models on ONNX Runtime) | Food packaging photos, 36 test samples, 10 languages | F1: docTR 0.193, ML Kit 0.180 (difference not significant), EasyOCR 0.167, RapidOCR 0.044. Japanese: 0.000 for all four | Tiny sample; RapidOCR configuration unknown, so its last place may be a language or model mismatch |
| [PP-OCRv6 small manga card](https://huggingface.co/fumetodev/PP-OCRv6_small_rec_manga) (B, community upload) | Stock PP-OCRv6 small, and a fine-tune | 422 hand-transcribed manga bubbles | Stock: CER 0.0615, 63.3% of bubbles exact. Fine-tune: 0.0402, 75.1%. The author lists vertical text, small kana, long-vowel marks and stylised fonts as the targets | Self-reported; v6, not v5; furigana must be removed before recognition |
| [Multilingual GPU benchmark](https://voiceping.net/en/blog/research-multilingual-ocr-gpu-benchmark-2026/) (B, 2026) | PP-OCRv5 among 8 models | 900 pages, 5 languages (en, ja, ko, zh, hi), six image types including screenshots | PP-OCRv5 mean CER 0.251; 0.152 on controlled rendered pages, 0.587 on real public pages | Documents, GPU; per-type numbers only in charts; Tesseract not run |

Maintainer and user reports:

- A Japanese dictionary app's maintainer ([js-dict #9](https://github.com/petlyh/js-dict/issues/9), April 2024, I) tried
  ML Kit and Tesseract for Japanese: ML Kit often misread kanji (sometimes kana) and did worst on vertical text;
  Tesseract was unusable for Japanese in their tests. They also ruled ML Kit out because it is proprietary
  (incompatible with their GPL-3.0 license and F-Droid). One person's tests, no numbers.
- An open-source photo app ([Ente, November 2025](https://ente.com/blog/ocr/), B) called ML Kit the only good option
  but a non-starter because of Play services and its closed nature, found the Tesseract ports on Android weak, and
  shipped PaddleOCR models on ONNX Runtime Mobile. No timings.
- Cloud Vision is first on signs, on three DocOCR-Eval sets (Chinese, Japanese, English receipts), on Tamil and on Thai.
  It is second on English forms (Paddle 0.42, Cloud Vision 0.29) and last of the three on French prescriptions (0.29
  against 0.66 and 0.52), and second to Surya on Sinhala. A strong reference, not a guaranteed winner.

Gaps: nothing public compares the three on screenshots, subtitles, games or comics; no ML Kit accuracy against
PaddleOCR; no published PP-OCRv5 phone timings with MNN; no per-language `tessdata_fast` against `tessdata_best` table.

## 2. Strengths and weaknesses

Cells give what a source says; "none found" means no source. Our own measurements are in `ocr-engines.md`.

| Topic | ML Kit v2 | Tesseract 5 (LSTM) | PP-OCRv5 mobile |
|---|---|---|---|
| Stylised and decorative fonts | None found | Trained on rendered fonts (Latin about 4,500, other scripts fewer: [fast README](https://github.com/tesseract-ocr/tessdata_fast)); no figures | "Artistic text" recognition accuracy 0.540 (server 0.640) against 0.861 for printed Chinese ([PP-OCRv5](https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5.html), D). Stock v6 reads 63% of manga bubbles exactly (table above) |
| Text over photos, gradients, outlines | None found; packaging photos F1 0.18 (HalalBench) | Global Otsu binarisation by default, which the docs say works poorly on uneven backgrounds; Sauvola and adaptive Otsu since 5.0 ([ImproveQuality](https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html), D). Signs: 0.38 against 0.92 for PaddleOCR (arXiv 2602.02223). A PNG's alpha channel is blended onto white, which erases transparent subtitles ([ImproveQuality](https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html)) | Signs 0.917 (arXiv 2602.02223); "general scenario" recognition 0.770 (server 0.838), and below the v4 mobile model's 0.811 |
| Low resolution, small text | Each character ideally at least 16×16 px; no gain above 24×24 ([guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), D) | Accuracy falls below an x-height of about 20 px, "very little chance" below 10 px, most text dropped as noise below 8 px; the LSTM engine also degrades above about 30 px ([old FAQ](https://tesseract-ocr.github.io/tessdoc/tess3/FAQ-Old.html), D, Tesseract 3 era plus an LSTM note). The FAQ section is titled "It won't read screen text!" | The 3.x pipeline scales an image up so its short side is at least 64 px (`text_det_limit_side_len` 64, type `min`, [pipeline page](https://www.paddleocr.ai/latest/en/version3.x/pipeline_usage/OCR.html), D); no minimum text size documented |
| Low contrast | None found | Same binarisation notes; in the SIBGRAPI test grayscale helped (CER 27% to 23%) and heavier steps hurt | None found |
| Vertical CJK | Docs silent; one maintainer reports it does worst on vertical text (js-dict #9) | Needs the separate `_vert` models (`jpn_vert` is trained on vertical text rendered with the long edge horizontal: [fast README](https://github.com/tesseract-ocr/tessdata_fast)) and PSM 5 | Recognition accuracy 0.809 (server 0.931, v4 mobile 0.592); lines taller than 1.5× their width are rotated 90° before recognition ([`utility.py`](https://github.com/PaddlePaddle/PaddleOCR/blob/main/tools/infer/utility.py), [PaddleX](https://github.com/PaddlePaddle/PaddleX/blob/develop/paddlex/inference/pipelines/components/common/crop_image_regions.py), C) |
| Rotated, curved | Reports a rotation per line; Latin got "all-orientation support" in 2022/2023 ([release notes](https://developers.google.com/ml-kit/release-notes), D); camera input needs the rotation passed in; curved: none found | Skew hurts line segmentation; OSD modes 0 and 1; [#4426](https://github.com/tesseract-ocr/tesseract/issues/4426) "poor rotation and layout detection" open since 2025; tilted Arabic text ChrF 5.9 in KITAB-Bench | Detection "rotation" 0.647 (server 0.800), "distortion" 0.827; the text-line orientation module is on by default |
| Handwriting | Not documented for this API | Documented as poor: "won't work very well", built for printed text ([FAQ](https://tesseract-ocr.github.io/tessdoc/FAQ.html), D) | Chinese 0.417, English 0.494 (server 0.581 and 0.581) |
| Mixed scripts in one line | Each script recognizer reads its script plus Latin-based languages (reference pages of [Japanese](https://developers.google.com/ml-kit/reference/swift/mlkittextrecognitionjapanese/api/reference/Classes/JapaneseTextRecognizerOptions), Chinese, Korean, Devanagari: "…and Latin-based languages"). The Korean model misses hanja (our note) | Languages joined with `+` (`eng+deu`); slower | Each non-Latin recognizer lists English next to its script; main reads zh, zh-Hant, ja, en and pinyin; no recognizer mixes two non-Latin scripts |
| Right to left (Arabic, Persian, Hebrew) | No Arabic or Hebrew model | Models exist; Arabic box ordering [#648](https://github.com/tesseract-ocr/tesseract/issues/648) open since 2017; diacritics and tilt are the weak points (KITAB-Bench) | Arabic recognizer for ar, fa, ur, ug, ps, ku, sd; the classic decoder reverses the output when the dictionary path contains "arabic" ([`rec_postprocess.py`](https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/rec_postprocess.py), C), which implies visual-order output; no Hebrew |
| Scripts without spaces (Thai, Lao, Khmer) | None | `tha`, `lao`, `khm` exist; hOCR, TSV and ALTO output one word per character ([#2702](https://github.com/tesseract-ocr/tesseract/issues/2702), open since 2019), plain text is fine with `preserve_interword_spaces=1`; Khmer scene text called "sub-optimal" ([KhmerST](https://arxiv.org/abs/2410.18277)); Thai: 47% against 84% for Cloud Vision | Thai recognizer, 82.68% line accuracy on 4,261 images; none for Lao or Khmer |
| Vietnamese | Latin model lists Vietnamese as supported ([languages](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages), D) | Separate `vie` model (in the fast set the Latin script model excludes Vietnamese) | Listed as supported, but the character list lacks most tone-marked letters (section 4) |
| Character and word boxes | Block, line, element and symbol boxes, rotation and confidence ([overview](https://developers.google.com/ml-kit/vision/text-recognition/v2), D) | Character boxes are estimated from the network's time steps, coarse and "never accurate" ([#3477](https://github.com/tesseract-ocr/tesseract/issues/3477), open since 2021); the legacy engine gives better ones | Line boxes; per-character positions come from the CTC alignment ([#10377](https://github.com/PaddlePaddle/PaddleOCR/issues/10377), [PR #10515](https://github.com/PaddlePaddle/PaddleOCR/pull/10515)), exposed as `return_word_box`, default off |

Coverage by script (documentation; our measurements decide which model is recommended):

| Script | ML Kit v2 | Tesseract | PP-OCRv5 |
|---|---|---|---|
| Latin (diacritics) | yes (Latin model, 31 supported and 15 experimental languages) | yes | latin recognizer, 47 languages |
| Vietnamese | yes | yes | listed, broken (section 4) |
| Cyrillic | no | yes | eslav (ru, be, uk), cyrillic (33 languages) |
| Greek | no | yes | el |
| Arabic, Persian, Urdu | no | yes | arabic |
| Hebrew, Georgian, Armenian | no | yes | no |
| Devanagari | Hindi, Marathi, Nepali (Sanskrit experimental) | yes | devanagari, 13 languages |
| Bengali | no | yes | no |
| Thai | no | yes | th |
| Lao, Khmer | no | yes | no |
| Japanese, Chinese | yes | yes, plus vertical models | main (also Traditional Chinese, pinyin) |
| Korean | yes | yes | korean |
| Latin text inside a non-Latin script | read by every script model | needs a joined language setup (`eng+rus`) | each non-Latin recognizer lists English |

## 3. Tesseract: data sets, page segmentation, preprocessing

The three official sets for 4.x and 5.x ([data files](https://tesseract-ocr.github.io/tessdoc/Data-Files.html), D):

| Set | Content | Weights | Retrainable | Notes |
|---|---|---|---|---|
| [`tessdata`](https://github.com/tesseract-ocr/tessdata) | Legacy engine and LSTM; its LSTM is an integer copy of `best` | int8 | no | Legacy data removed for Indic and Arabic-script files; only this set works with `--oem 0` |
| [`tessdata_best`](https://github.com/tesseract-ocr/tessdata_best) | LSTM only | float | yes (the only base for fine-tuning) | Slowest; best on Google's evaluation data |
| [`tessdata_fast`](https://github.com/tesseract-ocr/tessdata_fast) | LSTM only, smaller network, "value for money" | int8 | no | What Debian and Ubuntu ship; the README says it is the best choice for "some languages" but for "most not" |

All three are Apache-2.0 (each repository's LICENSE). The sets work only with the LSTM engine (`--oem 1`) except `tessdata`.

- Speed and accuracy, maintainer: in 2017 the LSTM author wrote that fast is "<5% worse" than best and faster than the
  legacy engine; later comments say fast and best were trained separately and cannot be derived from each other
  ([#1404](https://github.com/tesseract-ocr/tesseract/issues/1404), I). No benchmark table exists in the thread.
- Independent run ([nixarchy-voice #32](https://github.com/olafkfreund/nixarchy-voice/issues/32), I): Tesseract 5.5.3,
  English, one 2560×1440 screenshot, `--psm 3 --dpi 300`: fast 1,787 ms and 4.1 MB; `tessdata` 2,116 ms and 23.5 MB;
  best 2,747 ms and 15.4 MB. Words found 372, 380 and 377; unique-token overlap with a reference output 84% for fast
  and 95% for best (the author notes the overlap moved between runs of different screens, 67% to 84%).
- Download size of all languages ([Dangerzone #545](https://github.com/freedomofpress/dangerzone/issues/545), I): fast
  336 MiB compressed, best and `tessdata` 638 MiB.
- Where best is clearly better: no public table. The fast README says Latin was trained on about 400,000 lines in about
  4,500 fonts and other scripts with fewer fonts (Devanagari about 50, Kannada about 15); that fits, but does not
  prove, our Bengali, Greek and Armenian gaps. In [#2702](https://github.com/tesseract-ocr/tesseract/issues/2702) a
  user got correct spacing for Japanese from `tessdata_best` where the older integer data put a space after every
  character.

Page segmentation and preprocessing that matter on screenshots:

| Topic | Fact |
|---|---|
| Default mode | The command line starts in PSM 3 (automatic, no OSD, [usage](https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html)); the library's own default is PSM 6, a single block (`tessedit_pageseg_mode` in [`tesseractclass.cpp`](https://github.com/tesseract-ocr/tesseract/blob/main/src/ccmain/tesseractclass.cpp), C) |
| Modes for regions | ImproveQuality: for small regions try 6 (uniform block), 7 (line), 8 (word), 10 (character), 11 (sparse text), 13 (raw line); add a white border of about 10 px to tight crops; a very large border can give "empty page". 5 is a block of vertical text |
| Light text on dark | Docs: for 4.x use dark text on a light background. Code: a line whose mean confidence is below `invert_threshold` (0.7) is recognised again on the inverted image and the better result kept ([`lstmrecognizer.cpp`](https://github.com/tesseract-ocr/tesseract/blob/main/src/lstm/lstmrecognizer.cpp), C). That is per line, after layout analysis. `tessedit_do_invert=0` skips the second pass for speed ([FAQ](https://tesseract-ocr.github.io/tessdoc/FAQ.html)) |
| Binarisation | Otsu by default; since 5.0 `thresholding_method` also offers Leptonica Otsu (adaptive) and Sauvola. In the SIBGRAPI test external adaptive thresholding and morphology lowered accuracy |
| Scale | 300 dpi is the documented target; the FAQ's x-height limits are in section 2. Resizing is the documented remedy for small text; the x-height range of the FAQ (about 20–30 px) is the only size target given |
| Transparency | Tesseract 4+ blends alpha onto white ([ImproveQuality](https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html)); remove or invert it yourself for subtitles |
| Dictionaries | Word lists help sentences and hurt codes and names; `load_system_dawg` and `load_freq_dawg` set to false turn them off |
| Threads | Up to four OpenMP threads per page; `OMP_THREAD_LIMIT` limits them; four threads on two cores can be much slower ([FAQ](https://tesseract-ocr.github.io/tessdoc/FAQ.html)). [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) ships a single-thread and an OpenMP artifact; `TessBaseAPI` is not thread-safe; release 4.9.0 (June 2025) bundles Tesseract 5.5.1 ([release notes](https://github.com/adaptech-cz/Tesseract4Android/releases/tag/4.9.0)) |

## 4. PP-OCRv5 (and v6)

Recognizers, from the [multilingual page](https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.html)
and the [recognition module page](https://www.paddleocr.ai/latest/en/version3.x/module_usage/text_recognition.html) (D).
Accuracy is the share of lines read with no wrong character, punctuation included
([model card](https://huggingface.co/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec)), on the publisher's own set for each
row, so rows are not comparable with each other or with a CER:

| Recognizer | Languages | Line accuracy (images) | Size |
|---|---|---|---|
| main mobile | zh, zh-Hant, ja, en, pinyin | 81.29 (general set) | 16 MB |
| main server | same | 86.38 | 81 MB |
| korean | ko, en | 88.0 (5,007) | 14 MB |
| latin | 47 Latin-script languages incl. vi, tr, pl, lv, cs | 84.7 (3,111) | 14 MB |
| eslav | ru, be, uk, en | 81.6 (7,031) | 14 MB |
| cyrillic | ru, be, uk, sr (Cyrillic), bg, mn, kk, ky, tg, mk, tt and 22 more, en | 80.27 (7,600) | 7.7 MB |
| el | el, en | 89.28 (2,799) | 7.5 MB |
| th | th, en | 82.68 (4,261) | 7.5 MB |
| arabic | ar, fa, ur, ug, ps, ku, sd, bal, en | 81.27 (2,676) | 7.6 MB |
| devanagari | hi, mr, ne and 10 more, en | 84.96 (3,611) | 7.5 MB |

The detector: mobile 4.7 MB, Hmean 79.0; server 84.3 MB, Hmean 83.8
([detection page](https://www.paddleocr.ai/latest/en/version3.x/module_usage/text_detection.html), D).

Mobile against server, recognition accuracy by category
([PP-OCRv5 page](https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5.html), D; the publisher's
mixed internal set):

| Category | v5 mobile | v5 server | v4 mobile |
|---|---|---|---|
| Printed Chinese | 0.861 | 0.901 | 0.840 |
| Printed English | 0.875 | 0.868 | 0.660 |
| Japanese | 0.758 | 0.737 | 0.472 |
| Traditional Chinese | 0.720 | 0.747 | 0.322 |
| Vertical text | 0.809 | 0.931 | 0.592 |
| Handwritten Chinese / English | 0.417 / 0.494 | 0.581 / 0.581 | 0.298 / 0.255 |
| Artistic text | 0.540 | 0.640 | 0.556 |
| General scenario | 0.770 | 0.838 | 0.811 |
| Weighted average | 0.802 | 0.840 | 0.530 |

The mobile model is as good as or better than server on printed English and Japanese; the server model leads on
vertical text, handwriting, artistic text and general scenes. The page says v5 is slower than v4 mobile because of the
larger dictionary (end-to-end per image on 200 test images: mobile CPU 1.75 s, GPU 0.62 s; v4 mobile 1.37 s and 0.29 s).
The 3.0 [technical report](https://arxiv.org/abs/2507.05595) (P) gives no per-language numbers.

Known issues (most with a primary source):

- **Vietnamese.** `ppocrv5_latin_dict.txt` ([repository](https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/utils/dict/ppocrv5_latin_dict.txt))
  has 836 entries and lacks 45 of the 61 lowercase Vietnamese letters with a tone mark or stroke: every letter with a
  hook above or a dot below (ả ạ ẻ ẹ ỏ ọ ủ ụ …), every tone-marked ă â ê ô ơ ư (ắ ấ ế ố ộ ớ ữ ứ …), and ẽ ỳ ỹ; the 16
  that exist are the grave, acute and tilde forms of a, i, o, u, grave and acute of e, acute of y, and đ. The same
  list is in the shipped model's `inference.yml` ([Hugging Face](https://huggingface.co/PaddlePaddle/latin_PP-OCRv5_mobile_rec/blob/main/inference.yml), checked
  2026-10-17). A pull request adding 83 missing characters to the older `vi_dict.txt`
  ([#15204](https://github.com/PaddlePaddle/PaddleOCR/pull/15204)) was closed unmerged. The PP-OCRv6 dictionaries have
  the same gap ([#18254](https://github.com/PaddlePaddle/PaddleOCR/issues/18254), July 2026): a maintainer calls it "a
  model-level work item" because new letters change the class indices; closed as a follow-up for a future model.
- **Cyrillic with Latin.** The cyrillic list has 409 Cyrillic and 115 Latin letters, the eslav list 76 Cyrillic and 115
  Latin (counted from the repository files). A maintainer says Latin is kept because Latin text occurs in scenes and
  that filtering the output is "probably not effective"
  ([#13468](https://github.com/PaddlePaddle/PaddleOCR/issues/13468), closed as stale, 2024).
- **Arabic and Persian.** The arabic list holds both the Arabic and the Persian yeh (ي ی) and kaf (ك ک), letters that
  look the same in initial and middle forms, and has no zero-width non-joiner (U+200C). The recognizer is shared by
  ar, fa, ur, ug, ps, ku and sd. The classic decoder reverses the output for Arabic lists (section 2), i.e. the raw output is in visual order.
- **Vertical lines** are rotated 90° before recognition (section 2); the publisher lists vertical text as a trained
  scenario, but its mobile score for it (0.81) is below printed Chinese and English (0.86, 0.88).
- **Word boxes** are not native (section 2).
- **Detection thresholds** of the 3.x pipeline: pixel 0.3, box 0.6, unclip ratio 2.0, side limit 64 with type `min`;
  no source tells what they do on outlined text.

PP-OCRv6 (June 2026; [announcement](https://huggingface.co/blog/PaddlePaddle/pp-ocrv6),
[docs](https://www.paddleocr.ai/latest/en/version3.x/algorithm/PP-OCRv6/PP-OCRv6.html),
[report](https://arxiv.org/abs/2606.13108), [small recognizer card](https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec),
Apache-2.0):

| Tier | Parameters | Detection Hmean | Recognition accuracy | Languages |
|---|---|---|---|---|
| tiny | 1.5 M | 80.6 | 73.5 | 49 (no Japanese) |
| small | 7.7 M | 84.1 | 81.3 | 50 |
| medium | 34.5 M | 86.2 | 83.2 | 50 |
| PP-OCRv5 server / mobile (same set) | | 81.6 / 75.2 | 78.1 / 73.7 | |

The 50 languages are Chinese, Traditional Chinese, English, Japanese and 46 Latin-script languages (listed in the
[report](https://arxiv.org/html/2606.13108); Vietnamese is named but see the dictionary gap). There is no Korean,
Cyrillic, Greek, Arabic, Thai or Devanagari model. On the report's English and Latin sets small scores 86.3 and 84.0
against 86.0 (`en` v5) and 81.4 (`latin` v5). CPU speed: tiny about 3.9× faster than v5 mobile on a Xeon; per image
(200 images, Xeon 8350C, Paddle): medium 2.05 s, small 0.79 s, tiny 0.32 s. Models come as safetensors, Paddle and ONNX
files; no MNN, no Android timings. The small recognizer scores 88.2 on Japanese, 79.7 on screens and 61.1 and 57.6
on English and Chinese handwriting (the card's own categories). For this app v6 could replace the main and Latin
recognizers; it adds nothing for the other scripts, and we have not measured it.

## 5. ML Kit text recognition v2

- Scripts and languages ([languages](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages), D):
  Latin (31 supported and 15 experimental languages, Vietnamese and Turkish included), Chinese, Devanagari (Hindi,
  Marathi, Nepali), Japanese, Korean. Chinese, Devanagari, Japanese and Korean exist only in v2. The page lists
  Ossetic and Turkmen as "mapped" to a "Cyrillic script model"; the SDK offers none, matching our result.
- What a script model also reads: the reference pages describe the Japanese, Chinese, Korean and Devanagari options as
  recognizers "for [script] and Latin-based languages". The docs do not say how well.
- Packaging ([guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), D): bundled about 4 MB
  per script per architecture, unbundled (Play services) about 260 KB with a first-use download during which requests
  return nothing. The guide calls the Latin recognizer real-time on most devices and the others slower.
- Limits: characters of at least 16×16 px, no accuracy gain above 24×24 px, input kept small for speed, rotation must
  be passed in. No orientation or vertical-text statement. No accuracy or latency figures from Google.
- History ([release notes](https://developers.google.com/ml-kit/release-notes), D): all Android models upgraded April
  2022 ("all-orientation Latin support"); symbol and rotation results September 2022; Latin upgrade March 2023 and
  bundled v2 generally available May 2023; since then only 16 KB page-size repackaging (August 2024) and unchanged
  artifacts up to July 2026.
- Speed reports: none from Google. Blogs quote 33 ms per frame on a Pixel 3 with no primary source (v1 era); not used.
  Our emulator timings are in `ocr-engines.md`.
- Terms ([ML Kit terms](https://developers.google.com/ml-kit/terms), D): the APIs fall under the Google APIs terms; no
  reverse engineering or extraction of the software, models included. Images and text are processed on the device and
  the SDK does not send them or the results to Google; it contacts Google for model and bug-fix updates and sends
  usage metrics, and the app must tell its users about that metrics processing as the law requires. The page states
  no price and says nothing about distribution outside Google Play.

## 6. Android runtimes and speed

| Runtime | Facts |
|---|---|
| ONNX Runtime | Official PaddleOCR Android sample ([docs](https://www.paddleocr.ai/latest/en/version3.x/inference_deployment/cross_platform/android_deployment.html), D): ONNX Runtime 1.21.1, minSdk 26, models PP-OCRv6 small and tiny and PP-OCRv5 mobile as separate detector and recognizer files. Sample benchmark: device "GM1900", Android 9, 5 text lines: 420 ms in all (detection 349 ms, of it 311 ms inference; recognition 66 ms), cold load 158 ms; the page does not say which model produced it. Our measurement: same text as MNN, 5–20% slower, 19.4 MB per arm64 APK |
| MNN | Apache-2.0; the [README](https://github.com/alibaba/MNN) lists ONNX, TensorFlow, Caffe and TorchScript converters (Paddle format not listed); core library about 800 KB on armv7a; claims about 2× from ARMv8.2 FP16 and 2.5× from sdot. No public PP-OCRv5 phone timings found. Ours: 2.1 MB per arm64 APK |
| ncnn | [Sample app](https://github.com/nihui/ncnn-android-ppocrv5) with PP-OCRv5 mobile and server, dynamic shapes; no timings; notes that small models are often slower on the GPU than on the CPU |
| Paddle Lite | Not found: the PP-OCRv5 Android docs use ONNX Runtime |
| Desktop CPU, v5 mobile | Model-only: detector 57.8 ms, recognizer 21.2 ms ([module pages](https://www.paddleocr.ai/latest/en/version3.x/module_usage/text_detection.html), hardware not named); whole pipeline 1.75 s per image including image reading |

Tesseract on a phone: no public timing found; the desktop figures are in section 3. ML Kit: none (section 5).

## 7. Licenses and redistribution

| Component | License | Notes |
|---|---|---|
| Tesseract | [Apache-2.0](https://github.com/tesseract-ocr/tesseract/blob/main/LICENSE) | |
| `tessdata`, `tessdata_best`, `tessdata_fast` | Apache-2.0 ([LICENSE of fast](https://github.com/tesseract-ocr/tessdata_fast/blob/main/LICENSE), [best](https://github.com/tesseract-ocr/tessdata_best/blob/main/LICENSE)); the READMEs say "all data" | Built from `langdata`; its own license was not checked |
| Tesseract4Android | [Apache-2.0](https://github.com/adaptech-cz/Tesseract4Android) | Bundles Leptonica (own [BSD-style license](https://github.com/DanBloomberg/leptonica/blob/master/leptonica-license.txt)), libjpeg, libpng under their own licenses |
| PaddleOCR | [Apache-2.0](https://github.com/PaddlePaddle/PaddleOCR/blob/main/LICENSE), copyright PaddlePaddle Authors | |
| PP-OCRv5 weights | Apache-2.0 on the model cards ([cyrillic](https://huggingface.co/PaddlePaddle/cyrillic_PP-OCRv5_mobile_rec), v6 small) | Only two cards read; the other recognizers were not each checked |
| MNN | [Apache-2.0](https://github.com/alibaba/MNN/blob/master/LICENSE.txt) | |
| ONNX Runtime | [MIT](https://github.com/microsoft/onnxruntime/blob/main/LICENSE) | |
| ML Kit | Proprietary; [terms](https://developers.google.com/ml-kit/terms) | Used as a dependency, not redistributable as files; no reverse engineering; usage-metrics notice to users |

Redistributing converted PP-OCRv5 files (a plain reading of [Apache-2.0 §4](https://www.apache.org/licenses/LICENSE-2.0),
not legal advice): allowed, with a copy of the license, a prominent notice that the files were modified (converted to
MNN), and the original copyright, patent and attribution notices kept; a NOTICE file of the upstream project must be
carried along if there is one (none was checked in the PaddleOCR repository or on the model cards). A third-party app
already distributes MNN conversions of the models (`languages.md`). The Tesseract data is Apache-2.0 too; downloaded
unchanged from the tesseract-ocr repositories at a pinned commit, it is not redistributed by Screenlate.

## 8. What explains or contradicts our measurements

| Our result | Explanation or contradiction |
|---|---|
| Paddle Latin: Vietnamese 13.5% CER, 44% words | Explained: the model cannot output the letters (section 4). Not fixable by settings; use ML Kit Latin or Tesseract `vie` |
| Paddle Cyrillic: whole words in Latin look-alikes | Explained: 115 Latin letters in both lists, kept on purpose. Remedy outside the model: map look-alikes to Cyrillic in a word that has Cyrillic letters (own code) |
| Paddle Arabic: Persian ye and kaf come out as the Arabic letters | Explained: both letters are in the list and look the same in most positions; no source offers a fix |
| Paddle vertical Japanese drops ー、。 | Consistent with the 90° crop rotation and a vertical score of 0.81 (server 0.93); the missing marks are our inference, no source states them. The server model costs 81 MB |
| Tesseract CJK boxes off by up to a character, taps right 26–62% | Explained: LSTM boxes are coarse ([#3477](https://github.com/tesseract-ocr/tesseract/issues/3477)) and Thai and CJK words are split per character in box output ([#2702](https://github.com/tesseract-ocr/tesseract/issues/2702)). The legacy engine gives better boxes (reported in #3477); not tested |
| Tesseract loses lines of Korean, Bengali and Persian, reads Lao marks as a line | Partly explained: PSM 3 expects a page of text, ImproveQuality recommends modes 6, 7 or 11 for regions. No source covers Korean at 58–61% CER. Cheap untested check: PSM 6 and 11 and a 10 px border |
| fast as accurate as best on Latin, Cyrillic, Hebrew, Georgian, Thai, Lao, Khmer | Consistent with "<5% worse" (#1404) and the screenshot run (same word count, best 1.5× slower; ours 2–3×) |
| best clearly better on Bengali, Greek, Armenian | No source; the fast README's remark that other scripts had fewer training fonts fits |
| ML Kit Latin model as accurate as the Japanese one on Latin text, in less than half the time | Consistent: each script recognizer also reads Latin, and the docs call the Latin one the real-time one |
| ML Kit CER 0.1–3.8% (6.3% vertical) against 10–12% in the SIBGRAPI paper | Different data: clean rendered screens against photographed word crops. Real pages cost PP-OCRv5 about four times the CER of controlled ones (0.587 against 0.152, GPU benchmark) |
| Tesseract matches or beats Paddle on Latin CER, but is last in the literature | The literature uses photos, signs and receipts, where Tesseract does worst (section 1). The FAQ's remark that it will not read screen text is Tesseract-3 era and does not describe the LSTM engine. Expect the hard-screen results to move toward the literature |
| Paddle: Arabic CER 2.4% against Tesseract 5.8% | Does not contradict KITAB-Bench (Tesseract 0.54, Paddle 0.79): its PaddleOCR version predates the v5 Arabic recognizer |

## 9. Not found

- A benchmark with ML Kit, Tesseract and PP-OCRv5 on the same screenshots, subtitles, game text or comics.
- ML Kit accuracy or latency numbers from Google; any statement about vertical text.
- PP-OCRv5 timings on a phone with MNN; any Snapdragon table for v5.
- Per-language `tessdata_fast` against `tessdata_best` numbers; Tesseract timings on a phone.
- Independent PP-OCRv5 numbers for Thai, Greek or Devanagari.
- A fix or workaround from the maintainers for the Paddle Latin and Cyrillic dictionaries.
- The Korean-language low-resolution Tesseract/Paddle study the search found (PDF unreadable here); the
  [Arch bug](https://bugs.archlinux.org/task/60951) on `jpn_vert` was only seen in a search summary.

## 10. Descriptions for the app

One sentence per model, in plain words, from our measurements and the sources above. The two sentences that mention
photos or busy backgrounds rest on the literature, not on our clean screens: check them against the hard-screen results
of the second comparison before they go into the app.

| Model | Description |
|---|---|
| ML Kit (built in) | Fast and already in the app; reads Japanese, Chinese, Korean, Hindi and Latin-script text with precise word positions, but not Cyrillic, Greek, Arabic, Hebrew or Thai. |
| Tesseract, fast | A small download that reads clean text in most alphabets, including Cyrillic, Greek, Hebrew, Thai and Georgian; weaker on decorative fonts and photos, and its word positions are unreliable for Chinese, Japanese and Korean. |
| Tesseract, accurate | The same reader as "fast", several times larger and 2–3 times slower; clearly better only for Bengali, Greek and Armenian. |
| PaddleOCR | Reads Japanese, Chinese, Arabic, Persian and Greek well and copes better than Tesseract with photos and busy backgrounds; fails on Vietnamese tone marks and sometimes writes Cyrillic words with Latin look-alikes. |

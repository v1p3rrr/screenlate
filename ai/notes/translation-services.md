# Translation services (for a sentence translation button)

Probe of 2026-10-08 from a desktop connection, ja → ru, six sentences: three X posts (`testdata/ocr/x-thread.png`)
and three manga lines (`testdata/ocr/manga-page.webp`), each sent whole. Scripts were in the session scratchpad
(`mt_compare.py`, `lens_six.py`); the request shapes below are enough to redo them. All of these are unofficial
(no key, undocumented, may change or throttle).

| Service | Request | Time | Result |
|---|---|---|---|
| Bing Translator (web) | GET `https://www.bing.com/translator?setlang=en&cc=us` (keep cookies) → `IG:"…"`, `data-iid="translator.NNNN"`, `params_AbusePreventionHelper = [key,"token",…]`; POST form `fromLang, to, text, token, key` to `/ttranslatev3?isVertical=1&IG=…&IID=…` → `[{"translations":[{"text":…}]}]` | 1.5–2 s, first call 4 s | Best of the free ones: 5 of 6 right and natural, e.g. 安くはないが…必ず手に入れたい代物が多い → "Не дешево, но качество товаров высокое, и среди них много вещей, которые обязательно хочется заполучить" |
| Lens, the sentence drawn as an image with the translate filter (see `lens-protocol.md`) | as in `lens-protocol.md` | 0.9–1.1 s | 4 of 6; turned the meaning of the manga sentence around ("Несмотря на невысокую цену … предметами первой необходимости") |
| Yandex | POST `https://translate.yandex.net/api/v1/tr.json/translate?id=<32 hex>-0-0&srv=android&lang=ja-ru&text=…` → `{"text":[…]}` | 0.1–0.3 s | Fast but wrong in places: 2万円 → "2 миллионов иен", お願いしマス → "массируйтесь" |
| Google, `translate.googleapis.com/translate_a/single?client=gtx` and the site's `batchexecute` (rpc `MkEWBc`) | — | 0.6–2 s | Word for word the same output from both: the old models `ja_en_2023q1` + `en_ru_2023q1`, through English ("я думаю, ты милый", "следы его лица") |

- `edge.microsoft.com/translate/auth` (the Edge browser's keyless token) answered 404.
- The key of Chrome's page translator (`translate-pa.googleapis.com`) is not in `element.js`; not pursued.
- Google's Gemini-based "Advanced" translation is in its app in some countries only, not behind these endpoints.
- Blocking on Russian mobile networks was not checked; Yandex is the one likely to pass whitelists.

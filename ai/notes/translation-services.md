# Translation services (for a sentence translation button)

Probe of 2026-10-08 from a desktop connection, ja → ru, six sentences: three X posts (`testdata/ocr/x-thread.png`)
and three manga lines (`testdata/ocr/manga-page.webp`), each sent whole. Scripts were in the session scratchpad
(`mt_compare.py`, `lens_six.py`); the request shapes below are enough to redo them. All of these are unofficial
(no key, undocumented, may change or throttle).

| Service | Request | Time | Result |
|---|---|---|---|
| Bing Translator (web) | GET `https://www.bing.com/translator?setlang=en&cc=us` (keep cookies) → `IG:"…"`, `data-iid="translator.NNNN"`, `params_AbusePreventionHelper = [key,"token",…]`; POST form `fromLang, to, text, token, key` to `/ttranslatev3?isVertical=1&IG=…&IID=…` → `[{"translations":[{"text":…}]}]` | 1.5–2 s, first call 4 s | Best of the free ones; the answer carries `"usedLLM": true`: 5 of 6 right and natural, e.g. 安くはないが…必ず手に入れたい代物が多い → "Не дешево, но качество товаров высокое, и среди них много вещей, которые обязательно хочется заполучить" |
| Edge browser translator (keyless) | POST JSON `["text", …]` to `https://edge.microsoft.com/translate/translatetext?from=ja&to=ru&isEnterpriseClient=false` (omit `from` to detect) → `[{"detectedLanguage":{…},"translations":[{"text":…,"to":"ru"}]}]`, the Azure Translator v3 shape; no token, no cookies, any User-Agent (even okhttp's), several texts per request | 0.65–0.8 s | 3 of 6, a different model from Bing's: 2万円 → "20 000 йен" right, but 面影を感じる → "почувствуете старую сцену", 桃香さんの服で桃香さんの表情をする → "делает выражение сквозь одежду" |
| Lens, the sentence drawn as an image with the translate filter (see `lens-protocol.md`) | as in `lens-protocol.md` | 0.9–1.1 s | 4 of 6; turned the meaning of the manga sentence around ("Несмотря на невысокую цену … предметами первой необходимости") |
| Yandex | POST `https://translate.yandex.net/api/v1/tr.json/translate?id=<32 hex>-0-0&srv=android&lang=ja-ru&text=…` → `{"text":[…]}` | 0.1–0.3 s | Fast but wrong in places: 2万円 → "2 миллионов иен", お願いしマス → "массируйтесь" |
| Google, `translate.googleapis.com/translate_a/single?client=gtx` and the site's `batchexecute` (rpc `MkEWBc`) | — | 0.6–2 s | Word for word the same output from both: the old models `ja_en_2023q1` + `en_ru_2023q1`, through English ("я думаю, ты милый", "следы его лица") |

- `edge.microsoft.com/translate/auth` (the Edge token endpoint) answered 404; `translatetext` on the same host needs no token now (2026-10-09).
- The key of Chrome's page translator (`translate-pa.googleapis.com`) is not in `element.js`; not pursued.
- Google's Gemini-based "Advanced" translation is in its app in some countries only, not behind these endpoints.
- Blocking on Russian mobile networks was not checked; Yandex is the one likely to pass whitelists.

## Bing in the app

From the request shapes above and plainheart/bing-translate-api (MIT, `src/index.js`, `src/config.json`):

- Token: GET `https://www.bing.com/translator` (630 KB, about 170 KB gzipped, 2 s). It may redirect to a regional subdomain
  (`cn.bing.com`); later calls go to the host it ended on. Parse `IG:"…"`, `data-iid="…"` and
  `params_AbusePreventionHelper = [key, "token", expiryMs]`. `key` is the issue time in ms: the token is stale after
  `now - key > expiryMs` (3 600 000, an hour). Keep the cookies of that page for the POST.
- Translate: POST form `fromLang` (`auto-detect` works), `to`, `text`, `token`, `key` to
  `/ttranslatev3?isVertical=1&IG=…&IID=…` with a `Referer` of the translator page. Text up to 1000 characters;
  the library's "EPT" variant (`&SFX=<n>&ref=TThis&edgepdftranslator=1`) takes up to 3000 for languages in
  `eptLangs` (ja, ru, en, zh-Hans, zh-Hant, ko and most others) and is less prone to 429.
- Failures: `{"ShowCaptcha":true}` in the body, 401 (limit), 429 (throttled), a token rejected → fetch the page again
  once, then give up with a message. With `tryFetchingGenderDebiasedTranslations=true` some answers come as HTML with
  an `isgenderdebiasedtranslation` header and need a second request; with `false` the answer was plain JSON.
- Language codes are Microsoft's: `zh-Hans`/`zh-Hant`, `pt` vs `pt-PT`, `sr-Cyrl`/`sr-Latn`, `nb`.
- The Edge endpoint above (no `usedLLM` in its answers, hence the weaker results) takes the same codes and answers in the same shape, so it can serve as the fallback with no
  extra parsing.

## Probe of 2026-10-09 (before building request 46)

- Google gtx takes the text as a POST form field: POST `translate_a/single?client=gtx&sl=ja&tl=ru&dt=t` with body
  `q=<text>` answered 200 in 0.6 s, so the text never goes into a URL.
- Edge `translatetext` still needs no token (0.6–0.8 s).
- Bing: the token page took 1.7 s (635 KB), translations 1.7–2.4 s each, `usedLLM: true`. The first request of a
  token therefore misses a 3 s budget; later ones fit. The answer is an array: the first element has `translations`,
  a second one `inputTransliteration`. An `IID` without a suffix and with `.1` both worked. A rejected token answers
  HTTP 200 with `{"statusCode":205,"errorMessage":""}`.
- Language lists: `https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation`
  (138 codes, Bing and Edge) and `https://translate.googleapis.com/translate_a/l?client=gtx&hl=en` (`tl`: 249 codes).
  Codes that differ (canonical BCP 47 tag: Microsoft / Google): `zh-Hans`: zh-Hans / zh-CN, `zh-Hant`: zh-Hant / zh-TW,
  `he`: he / iw, `fil`: fil / tl, `nb`: nb / no, `jv`: – / jw, `mni`: mni / mni-Mtei, `mn`: mn-Cyrl / mn,
  `sr-Cyrl`: sr-Cyrl / sr, `ny`: nya / ny, `lg`: lug / lg, `rn`: run / rn, `prs`: prs / fa-AF, `pt-BR`: pt / pt, and the
  Kurdish pair: Microsoft `ku` is Central (Sorani, `ckb`) while Google `ku` is Kurmanji (`kmr`; Microsoft `kmr`, Google
  `ckb` for Sorani). Microsoft `mww` (Hmong Daw) and Google `hmn` (Hmong) are different languages.

## Built (request 46, 2026-10-09)

- The cascade, the settings test and the popup are in `core:translate`, `overlay/translate` and app `translate/`; the
  design is in `ai/plans/2026-09-29-feedback-after-0.1.4.md`, section "Sentence translation".
- Logs: each service's outcome with its time and `TranslationError` kind (`SentenceTranslator`), the settings test's
  failures too; never the sentence, the translation or a URL. Exceptions go through `redacted()`.
- The emulator's clock may lag behind the real date (seen: 2026-09-27 against 2026-10-09). Microsoft's certificates
  (Bing, Edge) were newer than that clock, so both failed with `CertificateNotYetValidException` (wrapped in
  `SSLHandshakeException`, shown as "Secure connection failed (certificate or TLS)") and only Google answered; Bing's
  test took about 9 s before it failed (OCSP). Not a service problem: check the device clock before blaming a service.
- A scan result that arrives after the translate tap (the cloud result after app text) renders the popup again; the
  page keeps the translation only while the popup stays open and the word and the sentence are unchanged.

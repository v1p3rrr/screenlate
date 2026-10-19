# Fonts in the lookup page (WebView)

Findings from the emulator (Android 17, WebView/Chrome), 2026-09-27.

## Glyph forms

- Android ships Noto Sans/Serif CJK as `.ttc` collections. `fonts.xml` lists one face per region (JP, KR, SC, TC, HK) with `lang` and `postScriptName` (`NotoSansCJKjp-Regular` etc.).
- The faces are Pan-CJK: each carries the other regions' glyphs behind the OpenType `locl` feature. The element's language picks them, so the JP face renders Chinese forms under `lang="zh-Hans"` and vice versa. **The page must set `lang` to the source language** (`LanguageSupport.languageTag`); the font choice alone is not enough.
- The same holds outside CJK: Noto Sans has Serbian forms (б, and italic г д п т) behind `locl`. The page sets `lang` to the language's code: `sr` for Serbian gives the Serbian forms (checked on the emulator, 2026-10-19: the headword брдо shows the Serbian б), `hr` for Croatian the default ones. Before the split the entries' `sh-Cyrl` and `sh-Latn` gave the Serbian forms too. Native views (the search field) keep the Russian forms.
- Without an explicit font, Chrome's fallback for Han characters follows the element's language. That works on stock Android. A vendor default font (`sans-serif`) that itself covers Han would win before the fallback, hence the explicit `"Screenlate Sans"` face.

## `local()` in `@font-face`

- WebView resolves `local()` by PostScript name and full name through its own font index: `NotoSansCJKjp-Regular`, `NotoSansCJKJP-Regular` (case-insensitive) and `Noto Sans CJK JP Regular` load; the family name `Noto Sans CJK JP` does not (the serif one, `Noto Serif CJK JP`, does). Probe with `new FontFace('x', 'local("…")').load()`.
- Korean on the Android 17 emulator (2026-10-15): `NotoSansCJKkr-Regular`, `NotoSansCJKKR-Regular`, `Noto Sans CJK KR
  Regular`, `NotoSerifCJKkr-Regular` and `Noto Serif CJK KR` load; `Noto Sans CJK KR` and the Noto Sans/Serif KR
  names do not. `fonts.xml`'s `ko` family points at index 1 of the `.ttc` files but names the PostScript name
  `NotoSansCJKJP-Regular`, so its attribute cannot be trusted. Under `lang="ko"` 次 takes its Korean form.
- Devanagari (2026-10-19, AOSP `fonts.xml` main): the families are declared for the script alone, `lang="und-Deva"`
  (Noto Sans Devanagari as a variable font, Noto Serif Devanagari), not for `hi`, so `SystemFontFiles` also matches a
  font whose locale has no language and the script ICU's likely subtags give the language (`hi` → `Deva`). Hindi
  declares no sans face (`systemFonts.sans` empty: the default font falls back to Noto Sans Devanagari by itself) and
  the serif one by `NotoSerifDevanagari-Regular`, `Noto Serif Devanagari Regular` and `Noto Serif Devanagari`. On the
  Android 17 emulator the first two load and the family name does not; Noto Sans Devanagari loads by its PostScript
  name only.
- Family names in plain CSS (`font-family: "Noto Sans CJK JP"`) do not reach system fallback fonts; only the families and aliases of `fonts.xml` (sans-serif, serif, monospace, arial, times, courier, …) resolve by name.
- The system face is declared with the script's `unicode-range`, so Latin and Cyrillic keep Roboto.
- The chosen installed font is always declared a second time as `"Screenlate Chosen"` (with the script's `unicode-range` when it is "only for Japanese text"), and the page font list names only that alias. Its own family name stays unlimited for custom CSS; naming the family itself in the list would mix in other installed fonts of the same family (an own file "Klee One" next to the downloaded one). A range-limited face loads only for characters in its range, so `document.fonts.load` needs a sample text (`preloadText`, the language's `fontSample`); with the default single space nothing loads.

## Weights

- Blink picks the weight of a variable face by clamping the requested weight to the face's declared `font-weight` range, and synthesizes bold only when the request is 600 or more and the face's maximum is below 600. A face without a declared range counts as 400 only, so a variable font declared without its range never gets its heavier instances.
- Declare a range only for true variable fonts (an `fvar` table with a `wght` axis). `FontFiles.describe` reads it (axis min/max, Fixed 16.16); for static files it reads the `OS/2` `usWeightClass` (offset 4). `SystemFontFiles.weights` does the same for the phone's CJK faces: on the Android 17 emulator `NotoSansCJK-Regular.ttc` is variable with `wght` 400–900 (not 100–900), so the page's system face is declared `font-weight: 400 900`; `NotoSerifCJK-Regular.ttc` is static. It reads only the file whose full or PostScript name (`name` IDs 4 and 6) is one of the page's `local()` names: `fonts.xml` also lists the variable `NotoSerifHentaigana.ttf` for `ja` at weight 400, and its range declared on the static serif face would stop synthetic bold.
- Heavier text (the weight and letter thickness sliders): with the "all" scope the page sets `font-weight` on `body` and `-webkit-text-stroke-width` on every element, and `--bold-weight` (used where Yomitan's styles say 600) becomes at least the chosen weight. With the "script" scope `popup.js` wraps runs of the script's characters (a regex built from the `unicode-range`) in `span.script-run` through a `MutationObserver`, and adds `.heavier` when the parent's computed weight is below the chosen one. Weights are read before any wrapping, since wrapping changes layout; `takeRecords()` after wrapping keeps the observer from seeing its own changes.
- Android's `Font.Builder` reads `OS/2` itself when no weight is set. The settings preview sets the weight and, for a variable font, the `'wght'` variation clamped to its range; Compose's `FontWeight` then picks the face, and minikin adds fake bold only for a request of 600 or more that is at least 200 above the face's weight. The outline preview is a second `Text` with `drawStyle = Stroke`.

## Custom CSS and own font files

- `CssCheck` appends the page font list to every `font-family` of a style rule, never to the `font-family` descriptor of `@font-face`: a descriptor takes exactly one name, and a list makes the browser drop the whole rule. Families defined by `@font-face` count as known, so they get no "missing font" warning.
- Own font files are one font per file, named by the typographic family and subfamily (`name` IDs 16/17, else 1/2; "Regular", "Normal", "Roman" and "Book" are left out, and for a variable font every weight word, since its style names only the default instance). Only a file whose name matches an existing own font replaces it. WOFF and WOFF2 are refused on import (`FontImport.WebFont`): Android's `Font.Builder` cannot read them, so the settings preview and the name would be wrong; fonts added before still work in the page.
- Own font files get a new name on every import (`<id>-<random>.<ext>`), even when a file with the same family replaces an older one: the lookup page lives long and keeps a font it loaded from `/fonts/<name>`, so a reused name would keep the old font. `LookupPage` serves and `PopupFonts` lists only plain names (`FontFiles.isFileName`), which keeps a restored `fonts.json` inside the fonts folder.

## Checking

- `scripts/popup-eval.mjs` evaluates JavaScript in the page of a debug build. Glyph comparisons: draw 直 on a canvas (`ctx.lang` set) with each font and hash the alpha channel; equal hashes mean equal forms.
- Chrome on a fresh emulator asks to accept its terms; use the app's own page instead.

# Fonts in the lookup page (WebView)

Findings from the emulator (Android 17, WebView/Chrome), 2026-09-27.

## Glyph forms

- Android ships Noto Sans/Serif CJK as `.ttc` collections. `fonts.xml` lists one face per region (JP, KR, SC, TC, HK) with `lang` and `postScriptName` (`NotoSansCJKjp-Regular` etc.).
- The faces are Pan-CJK: each carries the other regions' glyphs behind the OpenType `locl` feature. The element's language picks them, so the JP face renders Chinese forms under `lang="zh-Hans"` and vice versa. **The page must set `lang` to the source language** (`LanguageSupport.languageTag`); the font choice alone is not enough.
- Without an explicit font, Chrome's fallback for Han characters follows the element's language. That works on stock Android. A vendor default font (`sans-serif`) that itself covers Han would win before the fallback, hence the explicit `"Screenlate Sans"` face.

## `local()` in `@font-face`

- WebView resolves `local()` by PostScript name and full name through its own font index: `NotoSansCJKjp-Regular`, `NotoSansCJKJP-Regular` (case-insensitive) and `Noto Sans CJK JP Regular` load; the family name `Noto Sans CJK JP` does not (the serif one, `Noto Serif CJK JP`, does). Probe with `new FontFace('x', 'local("…")').load()`.
- Family names in plain CSS (`font-family: "Noto Sans CJK JP"`) do not reach system fallback fonts; only the families and aliases of `fonts.xml` (sans-serif, serif, monospace, arial, times, courier, …) resolve by name.
- The system face is declared with the script's `unicode-range`, so Latin and Cyrillic keep Roboto.

## Checking

- `scripts/popup-eval.mjs` evaluates JavaScript in the page of a debug build. Glyph comparisons: draw 直 on a canvas (`ctx.lang` set) with each font and hash the alpha channel; equal hashes mean equal forms.
- Chrome on a fresh emulator asks to accept its terms; use the app's own page instead.

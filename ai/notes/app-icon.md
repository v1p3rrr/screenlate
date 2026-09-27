# App icon

Chosen by the owner on 2026-09-27 (cloud session, branch `claude/elegant-allen-xmu3fx`); the path from six concepts
to the final one is in the plan's changelog.

## Design

A word lifted out of a line of text: grey rows, the word's former place highlighted, the bubble's aim dot on that
place, the word itself solid violet above the rows. White background (`#F8F6FD`), grey rows `#2E2848` at 32%, violet
`#7C5CFF` (the overlay's bubble, aim and word highlight color), the aim dot with a white outline as in the overlay.
No letters of any language, so the icon stays when more languages arrive.

- Debug build: a dark badge (`#2E2848`) with an amber bug (`#FFC83D`, the overlay's all-lines color) at the lower
  right, about a third of the icon wide. The bug is Material Icons' `bug_report` (Apache 2.0, in NOTICE).
- Themed icon (Android 13+): the monochrome layer keeps the translucency (rows 50%, highlight 45%); the aim dot's
  white outline and the bug are gaps.
- Quick Settings tile (`overlay/.../ic_tile_bubble.xml`) and the import notification
  (`dictionary/api/.../ic_dictionary_import.xml`): the same silhouette at 24 dp, rows at 55%.

## Files and tools

`scripts/icon/icon.py` holds the design (shapes in the 108-unit adaptive icon grid) and writes every icon resource:
launcher background, foreground and monochrome layers (`app/src/main`, debug overrides in `app/src/debug`), the
adaptive icon XMLs, the tile and notification icons and `docs/images/icon.svg` for the README. Holes (the aim dot's
outline, the badge's gap) are cut with skia-pathops, so the drawables are plain filled paths with `fillAlpha`; no
masks, clip paths, gradients or text. Legacy PNG/WebP mipmaps were removed: with minSdk 30 launchers always use
`mipmap-anydpi`.

`scripts/icon/check.mjs` renders every drawable (converted back to SVG) and the design's reference SVG in Chromium
and compares them pixel by pixel; it fails when a pixel differs by more than half (edges differ slightly because arcs
become cubic curves), and when the launcher foreground or monochrome layer has pixels outside the 66-unit safe zone.
It also writes `scripts/icon/build/drawables.png` (masks, sizes, wallpapers, themed, small icons).

```bash
pip install fonttools skia-pathops
python3 scripts/icon/icon.py
node scripts/icon/check.mjs          # PLAYWRIGHT=<path to the playwright module> if it is not resolvable
```

Without the Android SDK, aapt2 validates the resources: take `com.android.tools.build:aapt2:<AGP version>-<build>`
(`aapt2-*-linux.jar` from Google's Maven, unzip `aapt2`) and `android.jar` from `platform-37.0_r02.zip`
(dl.google.com/android/repository), `aapt2 compile` the icon files, `aapt2 link` them with a minimal manifest,
`-I android.jar --min-sdk-version 30`, and the debug files as `-R` overlays. On 2026-09-27 this linked without warnings;
`mipmap-anydpi` is stored as `mipmap-anydpi-v21`.

## Checked only on a device

Launcher masks of real launchers (Pixel, MagicOS), the themed icon, the splash screen (Android 12+ shows the adaptive
icon on the window background, which is white too), app info and recents, the tile in the shade, the notification
icon in the status bar, debug and release side by side. The handoff prompt lists the steps.

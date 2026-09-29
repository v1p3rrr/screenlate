# Interface translations

Separate from phase 8 (more lookup languages): only the app's own interface strings.

Owner request (2026-09-29): interface in Spanish, French, German, Chinese (probably both scripts), Korean, Japanese, Vietnamese, Portuguese, Italian, Turkish and Polish through Android string resources, next to the existing English and Russian; check that no layout breaks with them. Done after the current fixes (merged audio card, missing dictionaries card, About text, audio source checks, the owner's custom audio URL, removal of mentions), then released as v0.1.4.

## Owner requests added during the work (2026-09-29)

- Check every interface language on the emulator, not only de, fr, ja, zh, vi.
- With cloud recognition turned off (engines: device only), the popup must not show the "no network, recognized on device only" kind of error.
- Screenshot crop mode: after tapping "Whole screen" there is no way back to the adjustable frame; fix.
- The crop frame must by default surround the looked-up word plus a margin of a few tens of pixels.
  Owner clarification: keep the paragraph, with 48 dp padding; the real problem was a frame that once jumped to the top-left corner (fixed: size-less boxes are ignored).
- Crop mode "Whole screen" becomes a toggle: "Whole screen" ⇄ "Frame", returning to the previous frame (owner).
- Device-only mode: the source chip says just "On-device", with no ⚠ (owner).
- Check the crop frame in the app-text-only mode as well.

## Decisions

| Topic | Decision |
|---|---|
| Locales | es, fr, de, zh Simplified (`b+zh+Hans`), zh Traditional (`b+zh+Hant`), ko, ja, vi, pt, it, tr, pl (owner) |
| Portuguese | One `values-pt` in Brazilian Portuguese, used for every Portuguese locale (owner) |
| Scope | Every translatable string resource of `app`, `overlay`, `core:anki`, `dictionary:api`; plurals follow each language's CLDR categories. Release notes, docs and dictionary content stay English |
| Wording | Same rules as English: "cloud recognition", no vendor or app names; "Screenlate", "Anki", "AnkiDroid", "Yomitan" and dictionary names untranslated; one glossary per language |
| Disclaimer | "Not reviewed by native speakers; corrections welcome" in the README and as a hint under the language picker in Appearance (owner) |
| Flags in the picker | pt shows 🇧🇷 (Brazilian text); both Chinese scripts show 🇨🇳 (owner) |
| Checks | Unit test that every locale has every string and matching format arguments; layouts on the emulator in de, fr, ja, zh, vi (long and CJK text) |

## Changelog

- 2026-09-29: plan created from the owner's request.
- 2026-09-29: decisions recorded from the owner's answers (both Chinese scripts, Brazilian Portuguese for all, disclaimer in README and under the language picker); flags and checks chosen during implementation.
- 2026-09-29: owner decision — Traditional Chinese also shows the Chinese flag.
- 2026-09-29: owner requests added: check all languages on the emulator, no offline error in device-only mode, return from whole screen to the frame in crop mode, default crop frame around the word with a margin.
- 2026-09-29: owner answers — crop toggle, paragraph frame with 48 dp padding, plain "On-device" chip in device-only mode; check the crop frame in the app-text-only mode.
- 2026-09-29: found on the emulator: the language picker showed "System" when the system settings set a more specific tag (pt-BR, zh-TW, zh-Hans-CN); the current option now matches by language and script.
- 2026-09-29: found on the emulator: a long segment label was cut ("Wszy…" in Polish); segmented button labels now shrink to fit (down to 10 sp) before they are cut.
- 2026-09-29: all 12 new locales checked on the emulator (home, search, every settings screen, the language picker); no other layout problems.
- 2026-09-29: found on the emulator: a language chosen in the app reached activities only; the bubble, popup, crop editor, notifications and anything built from the application context stayed in the system language. The application and the accessibility service now return resources in the app's language (`AppLanguageResources`, re-read from the system at most every 2 s).
- 2026-09-29: crop editor buttons take the height of the tallest one when a label wraps ("Ganzer Bildschirm").

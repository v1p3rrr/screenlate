# Settings rework and the docked bubble's menu (2026-10-28)

Branch `features/settings-rework` from `languages/main` (`52f5fde4`), worktree `.worktrees/camera`. Owner's requests
of 2026-10-28; request 61 of `2026-10-10-languages-implementation.md` (where settings live) and its request 75 (the
docked bubble's hold menu).

## Goal

1. Text recognition gets its own settings entry, named so that a user without technical background understands it.
2. The dictionary catalog is collapsed by groups; installed dictionaries stay as they are.
3. "Anki and audio" becomes two entries.
4. A review of every settings screen: split or merge where it helps.
5. A note on Background work about a security app's allowlist (the ColorOS report of 2026-10-20 was settled by it).
6. After the rework: a phone build (Dev, installs over the current one), then the docked bubble's hold menu.

## Decisions

| Decision | Choice |
| --- | --- |
| Recognition entry | A "Text recognition" entry ("Распознавание текста") in the settings list, summary "How text is read: cloud, device, app text". It holds, in this order: Engines, OCR boost, Reduce load on the device, the on-device models, the app text switches (read app text, app text only, with its confirmation), and a Test section whose row opens the OCR test (moved from About → Tools, which goes away). Camera keeps High accuracy; its Engines row opens the new screen. The e-ink hint's app text link and the links to a language's on-device models open it too (owner, 2026-10-28) |
| Bubble screen | Keeps: show the bubble, aim point, dock side, docking at the top and bottom, size, highlight, vibration, and a row "Hide the bubble in apps" with "Hidden in N apps" or "Shown in all apps"; the row opens the app list with its search on its own screen, hidden apps first as now (owner, 2026-10-28) |
| Catalog groups | Each group of the catalog (each translation language and each other kind) is a header with its count ("→ Russian · 3"); a tap opens or closes it. By default only the translations into the interface language are open; a group with a download running or an update waiting opens by itself unless the user closed it. The state lasts while the app's process lives. Installed dictionaries are not grouped this way (owner, 2026-10-28) |
| Anki and Audio | Two entries: "Anki" (connection, setup check, note, fields, duplicates) and "Audio" (auto-play, sources, regions, test, volume), each with the language card at the top as now. Home's audio link opens Audio. Each page has its own reset: Anki's no longer resets the audio sources (owner, 2026-10-28) |
| Settings order | Function, then look, then system, no group headings (decision "Settings order" of `2026-09-29-feedback-after-0.1.4.md`): Bubble, Text recognition, Lookup, Dictionaries, Anki, Audio, Popup, Translation, Camera, Appearance, Background work, Backup and restore, About (owner, 2026-10-28) |
| Section resets | Bubble's reset covers the bubble's own keys and the hidden apps; Text recognition's covers the text source, OCR boost, engines, reduce load and the on-device model choices; Audio's covers the audio keys (with the shared ones as Anki's did). The backup format and its sections stay as they are ("Floating bubble and recognition" covers both pages) (technical, follows the split) |
| Allowlist note | Background work, on every phone, a section "If the service turns off" in documentation tone: when the system keeps turning Screenlate's service off and the bubble disappears, the phone's security app may restrict apps installed outside an app store; add Screenlate to its allowlist (trusted apps) and turn the service on again. No vendor names, no button (owner, 2026-10-28) |
| Other screens | Reviewed, no change: Lookup, Popup (elements and auto-hide already come first), Translation, Camera, Appearance, Backup, About. The repeated switches (translation button on Popup and Translation, translation in notes on Anki and Translation) stay as decided on 2026-10-09 |
| Phone build | One build with both the settings rework and the docked bubble menu (owner, 2026-10-28 night): the release type with the Dev package and label as in `notes/build-environment.md`, signed with the local debug key so it installs over the current Dev build as an update, and with the debug icon's bug badge (the debug launcher drawables) |
| Docked bubble menu | Request 75: holding the docked bubble without moving opens the hold menu with the quick-language flags and Open Screenlate; no items that need a scan (copy the paragraph, copy all); Open Screenlate is left out while Screenlate is in front; a menu without items does not open. A move before the hold time pulls the bubble out or moves the dock as now (backlog row, owner 2026-10-22) |

## Work

- Settings list, routes (`ScreenlateNavHost`): `RecognitionRoute(showAppText, ocrModels)`, `AudioRoute(language)`,
  `HiddenAppsRoute`; `BubbleRoute` and `AnkiRoute` lose their scroll arguments.
- `recognition/RecognitionSettingsScreen.kt` with its view model; `bubble/HiddenAppsScreen.kt`;
  `audio/AudioSettingsScreen.kt`; `SettingsSection` RECOGNITION and AUDIO with their keys and reset texts.
- Catalog: collapsible groups in `DictionariesScreen`, the default in a tested function.
- Background work: the allowlist note.
- Icons for the two new entries (Material Icons, Apache 2.0, as the others).
- Strings in all 14 locales; `docs/usage.md`; test model cases; `SettingsKeysTest` and the catalog default's tests.
- Then the phone build, then the docked bubble's menu (`OverlayController.BubbleTouchListener`, `showBubbleMenu`).

## Progress

- Settings rework built (uncommitted until checked on the emulator): the Text recognition page
  (`recognition/RecognitionSettingsScreen.kt`) with cards Screen recognition (engines, OCR boost, reduce load,
  on-device model), App text and Test (OCR test); the hidden apps page (`bubble/HiddenAppsScreen.kt`); the Audio page
  (`audio/AudioSettingsScreen.kt`, cards Playback, Sources, Regions, Test); Anki without audio; the settings list with
  the two new entries (Material `document_scanner` and `volume_up`); About without Tools; Camera's Engines row, the
  e-ink hint and the overlay's models link open Text recognition; the allowlist card on Background work; collapsible
  catalog sections (`CatalogSection.openByDefault`, `CatalogSectionsTest`). Strings in 14 locales; the language hint
  of Anki and of Audio each name their own per-language settings. `docs/usage.md`, `docs/architecture.md`, test model
  cases SET14–SET17 and DIC36.
- Docked bubble hold menu: the hold is posted while docked too; the menu leaves out the copy items there.

## Deferred questions (owner asleep, decided provisionally)

- Text recognition groups its rows into three cards (Screen recognition, App text, Test) instead of one flat list.
- The Audio page splits the former Audio card into Playback (auto play, volume), Sources, Regions and Test cards; a
  "Playback" heading is new.
- The bubble page keeps its flat layout; the hidden apps count leaves out apps uninstalled since they were hidden.

## Changelog

- 2026-10-28: plan written from the owner's answers (recognition entry and contents, catalog groups, Anki and Audio
  order, the order inside Text recognition, the hidden apps screen, the allowlist note on every phone).
- 2026-10-28 night: built the rework and the docked menu; three layout choices made without the owner are listed under
  Deferred questions.

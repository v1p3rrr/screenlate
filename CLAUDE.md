# CLAUDE.md

Screenlate: Poe-like pop-up dictionary for Android (Lens OCR, Yomitan dictionaries, AnkiDroid export). Personal project of the repo owner, developed for their phone (Honor, Android 15, MagicOS 9).

## Start of every session

1. Read `ai/status.md` (progress, open items) and the current plan in `ai/plans/` (latest dated file).
2. Read the notes in `ai/notes/` that touch the area you work on:
   - `build-environment.md` — JDK/SDK paths, AGP 9 specifics, tooling gotchas;
   - `lens-protocol.md` — Lens request/response field map;
   - `yomitan-behavior.md` — lookup, grouping, sorting, Anki duplicate defaults, structured content;
   - `references.md` — upstream projects, licenses, dictionary URLs;
   - `dictionary-engine.md` — hoshidicts API, JNI design, bundled dictionary URLs;
   - `webview-fonts.md` — CJK glyph forms, `local()` font names and checks in the lookup page.
   - `app-icon.md` — the icon design, its generator and checks (`scripts/icon/`).
   - `ocr-engines.md` — OCR and app text costs, ML Kit quirks, trusted character boxes, popup renderer priority.
3. Human-facing docs: `docs/architecture.md` (modules and data flow), `docs/usage.md` and `docs/development.md` (build, tests, releases); keep them current when behavior changes.

## Working rules

- Everything in the repo is English: code, KDoc, docs, commit messages. Talk to the owner in Russian. UI strings: English in `values/`, Russian in `values-ru/`.
- Before writing or rewriting a plan, ask the owner (AskUserQuestion) about every choice not already settled in the plan's decision table. Put the recommended option first. When the owner asks "why", explain and ask again.
- The owner prefers to be asked rather than to find decisions made for them, including defaults and UI placement. When new feedback arrives, first collect every open question it raises and ask them all (in batches of up to four) before continuing the work; don't leave some for later unless the owner says so.
- Give every question enough context to be answered cold: which feature it is about, how it works now, and what each option changes. If the owner didn't understand a question, explain it more simply and ask it again.
- The owner often writes while you work. Answer each message in the same turn, and add every request or remark to the plan right away so nothing gets lost. Send short progress updates in Russian during long work.
- The decision table in the plan is binding. Changing a decision needs the owner's confirmation; record it in the plan's changelog.
- Plans: small updates are edited in place with a changelog line; a major re-plan is a new dated file in `ai/plans/`.
- Update `ai/status.md` at the end of each work session. Add notes to `ai/notes/` when you learn something a future session would otherwise rediscover (and link it here if it is a new file).
- Commit after each finished phase or a coherent milestone and push to `origin main` (github.com/v1p3rrr/screenlate). Commit messages describe the change itself; never mention plan phases.
- Human-facing docs (README, `docs/`, NOTICE) stay generic: no mentions of specific devices, vendors, or the apps that inspired the project. Such context belongs in `ai/`.
- Do not mention Google Lens in human docs and descriptive texts (README, `docs/`, About, settings hints, the service description): call it cloud recognition. The popup's OCR source chip may say "Lens". Code and `ai/` notes keep technical names; NOTICE keeps only the license attribution it needs.
- Privacy: logs never contain recognized text, looked-up words, note contents, or URLs carrying terms (redact them).
- The owner's Anki data is off limits: never sync, and never add to or change their AnkiWeb collection. The emulator's AnkiDroid is a local collection without an account; the owner's backup is in `testdata/anki-backup/`.
- `testdata/` holds local test material (OCR screenshots, dictionaries, the Anki backup). It stays out of git; check `.gitignore` before adding a new folder there.
- New logic comes with unit tests, including the page scripts (`note.js`, `anki.js`, `popup.js`) once their test setup exists.

## Architecture rules

- Modules and responsibilities are listed in `docs/development.md`, `docs/architecture.md` and the plan. Keep dependencies pointing inward: `app`/`overlay` → `core:*` / `dictionary:api`; engine and renderer modules implement interfaces from `dictionary:api`.
- GPL-3.0 third-party code lives only in `dictionary:engine-hoshidicts` and `dictionary:render-yomitan`. Nothing outside them may depend on their internals. Porting Yomitan (GPL) logic into other modules counts as GPL code: put derived code in those two modules, or write an own implementation from the behavior or from permissively licensed sources (e.g. Mozc's romaji table, BSD).
- Shared Gradle configuration goes into convention plugins in `build-logic/`, not copied between modules.
- DI: Hilt. App data: Room. Preferences: DataStore. No annotation processors besides KSP.
- Language is a parameter (`core.common.Language`) in OCR, lookup and rendering; do not hard-code Japanese outside Japanese-specific classes. Language-specific behavior belongs behind the per-language support class (see the plan's language audit).
- UI: anything that may not fit must scroll; long text wraps. Details not needed by default (licenses, release notes, long explanations) go behind an ⓘ icon or a button that opens a dialog.

## Code style

- Match the surrounding code. Kotlin official style.
- KDoc for public APIs and non-obvious behavior only. Short, factual, no emojis, no restating the function name.
- Every third-party code or bundled data addition gets an entry in `NOTICE`.

## Build and test

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew assembleDebug testDebugUnitTest
npm --prefix scripts/page-tests ci && npm --prefix scripts/page-tests test   # page scripts (node:test + jsdom)
```

Devices: `D:\Android\Sdk\platform-tools\adb.exe`. Verify UI and overlay changes on an emulator or the phone, not only with unit tests.

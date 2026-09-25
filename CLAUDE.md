# CLAUDE.md

Screenlate: Poe-like pop-up dictionary for Android (Lens OCR, Yomitan dictionaries, AnkiDroid export). Personal project of the repo owner, developed for their phone (Honor, Android 15, MagicOS 9).

## Start of every session

1. Read `ai/status.md` (progress, open items) and the current plan in `ai/plans/` (latest dated file).
2. Read the notes in `ai/notes/` that touch the area you work on:
   - `build-environment.md` — JDK/SDK paths, AGP 9 specifics, tooling gotchas;
   - `lens-protocol.md` — Lens request/response field map;
   - `yomitan-behavior.md` — lookup, grouping, sorting, Anki duplicate defaults, structured content;
   - `references.md` — upstream projects, licenses, dictionary URLs.

## Working rules

- Everything in the repo is English: code, KDoc, docs, commit messages. Talk to the owner in Russian. UI strings: English in `values/`, Russian in `values-ru/`.
- Before writing or rewriting a plan, ask the owner (AskUserQuestion) about every choice not already settled in the plan's decision table. Put the recommended option first. When the owner asks "why", explain and ask again.
- The decision table in the plan is binding. Changing a decision needs the owner's confirmation; record it in the plan's changelog.
- Plans: small updates are edited in place with a changelog line; a major re-plan is a new dated file in `ai/plans/`.
- Update `ai/status.md` at the end of each work session. Add notes to `ai/notes/` when you learn something a future session would otherwise rediscover (and link it here if it is a new file).
- Commit after each finished phase or a coherent milestone and push to `origin main` (github.com/v1p3rrr/screenlate). Commit messages describe the change itself; never mention plan phases.
- Human-facing docs (README, `docs/`, NOTICE) stay generic: no mentions of specific devices, vendors, or the apps that inspired the project. Such context belongs in `ai/`.

## Architecture rules

- Modules and responsibilities are listed in the README and the plan. Keep dependencies pointing inward: `app`/`overlay` → `core:*` / `dictionary:api`; engine and renderer modules implement interfaces from `dictionary:api`.
- GPL-3.0 third-party code lives only in `dictionary:engine-hoshidicts` and `dictionary:render-yomitan`. Nothing outside them may depend on their internals.
- Shared Gradle configuration goes into convention plugins in `build-logic/`, not copied between modules.
- DI: Hilt. App data: Room. Preferences: DataStore. No annotation processors besides KSP.
- Language is a parameter (`core.common.Language`) in OCR, lookup and rendering; do not hard-code Japanese outside Japanese-specific classes.
- UI: anything that may not fit must scroll; long text wraps.

## Code style

- Match the surrounding code. Kotlin official style.
- KDoc for public APIs and non-obvious behavior only. Short, factual, no emojis, no restating the function name.
- Every third-party code or bundled data addition gets an entry in `NOTICE`.

## Build and test

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew assembleDebug testDebugUnitTest
```

Devices: `D:\Android\Sdk\platform-tools\adb.exe`. Verify UI and overlay changes on an emulator or the phone, not only with unit tests.

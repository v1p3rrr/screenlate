# Handoff: faster collection import (verify and merge)

Prompt for a local session with the Android SDK, the emulator and the owner's phone. The cloud session had neither,
so everything below "Verify locally" is still open.

---

A cloud session sped up the Yomitan dictionary collection import and added a free space check. The work is on branch
`claude/elegant-allen-xmu3fx`: commits 38e1cf5, 52247de, 575940e and the commit that adds this file, on top of
0ff1109. Verify it in the app and, if everything is fine, merge it into main.

## What changed

- `dictionary/api/.../imports/RawJsonScanner.kt`: the scanner reads UTF-8 bytes instead of a `Reader` and copies
  values in whole buffer runs (`capture`: `fill()` saves the pending part of a value before refilling).
- `YomitanBackup.kt`: a row is kept as offsets into one byte buffer, field names are matched as bytes, archives are
  written uncompressed by default (`compress = false`), banks are sized from the previous bank. A new pass,
  `YomitanBackup.measure`, counts rows, text bytes and media bytes of the selected dictionaries and writes nothing.
- New `CollectionSpace.kt`: estimates the peak storage in import order (all archives are written first, each is
  deleted once its dictionary is installed) plus a 128 MB reserve. Owner decisions: uncompressed archives when they
  fit, compressed ones when only those fit, otherwise the import stops before writing anything.
- `DictionaryImportWorker`: stage `check` (measuring) first, then `convert` in the chosen mode. Without enough space
  it throws `NotEnoughSpaceException` and puts `needed_bytes`/`free_bytes` into the worker's output.
- `DictionaryImports.ImportTask`: new state `CHECKING_SPACE` and field `shortage`. `DictionariesScreen`: "Checking
  free space for …" and an error card "Not enough free space … about X needed, Y free" (en and ru strings).
- Tests: `RawJsonScannerTest`, `YomitanBackupJvmTest` (new cases), `CollectionSpaceTest`. Docs: `docs/usage.md`,
  `docs/architecture.md`. Measurements and design: `ai/notes/dictionary-engine.md`, section on import speed. Plan
  and status updated.

## Checked in the cloud (no Android SDK)

- The owner's export `yomitan-dictionaries-2026-01-22-22-46-44.json` (2.73 GB) on a desktop JVM: conversion 46 s →
  about 10 s uncompressed and 21.5 s deflated, measuring about 4 s, hoshidicts import about 4.5 s (hoshidicts built
  for Linux).
- All 16 archives of the new converter match the old ones byte for byte in content (`index.json` equal as JSON).
- 25 JVM tests pass. The converter tests pass on both the old and the new implementation.
- CI run by hand on the branch (build, unit tests, lint, page tests) passed:
  https://github.com/v1p3rrr/screenlate/actions/runs/36327810447, debug APK in the artifact
  `screenlate-debug-52247de`.

## Verify locally

1. `git fetch origin && git checkout claude/elegant-allen-xmu3fx && git submodule update --init --recursive`, then
   `./gradlew assembleDebug testDebugUnitTest lintDebug :build-logic:convention:test` and the page tests.
2. Desktop benchmark on the real file:
   `BENCHMARK_COLLECTION=1 ./gradlew :dictionary:api:testDebugUnitTest --tests "*YomitanBackupJvmTest*benchmark*" -i`.
   The old conversion took 32 s on this machine.
3. Emulator: install the branch's debug build (`scripts/debug-device.sh install`) and import the owner's export
   through "Import a Yomitan dictionary collection" with all dictionaries.
   - Time each stage: "Checking free space", "Unpacking", "Importing". Compare with main (about 5.5 min).
   - Progress moves during both first stages.
   - Afterwards: dictionaries present, order and enabled state kept, lookups work (e.g. 食べる, a word from the
     Russian dictionary, images in Jitendex).
4. Low space: fill the emulator temporarily, e.g. `adb shell dd if=/dev/zero of=/sdcard/fill bs=1M count=N`
   (pick N from `adb shell df /data`).
   - When only uncompressed archives do not fit, the import still succeeds (compressed, slower).
   - When compressed ones do not fit either, the card shows "about X needed, Y free" in Russian and English, and
     the temporary files are gone.
   - Remove the filler afterwards.
5. The owner's phone: the same import with timings, if the owner is up for it.
6. If everything is fine: merge the branch into main, push, remove the "Verify branch…" item from Next in
   `ai/status.md`, and add the emulator and phone timings to `ai/notes/dictionary-engine.md`. If something is
   wrong, fix it on the branch and describe it in the status.

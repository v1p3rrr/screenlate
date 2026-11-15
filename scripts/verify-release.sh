#!/usr/bin/env bash
set -euo pipefail

echo "==> Running full verification (build, unit tests, lint, androidTest compilation, release lint vital, convention tests)..."
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest :app:lintVitalRelease :build-logic:convention:test

echo "==> Running page script tests..."
npm --prefix scripts/page-tests test

echo "==> Verification complete! All checks passed."

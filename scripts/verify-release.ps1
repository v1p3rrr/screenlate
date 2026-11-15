$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:PATH = "C:\Program Files\Git\cmd;$env:PATH"

Write-Host "==> Running full verification (build, unit tests, lint, androidTest compilation, release lint vital, convention tests)..." -ForegroundColor Cyan
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest :app:lintVitalRelease :build-logic:convention:test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Running page script tests..." -ForegroundColor Cyan
npm --prefix scripts/page-tests test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Verification complete! All checks passed." -ForegroundColor Green

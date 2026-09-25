#!/usr/bin/env bash
# Development helper for a connected emulator or device (debug build).
#
#   scripts/debug-device.sh install            install the debug APK and enable the accessibility service
#   scripts/debug-device.sh images             copy testdata/ocr/* into the app's internal files
#   scripts/debug-device.sh show <file>        open a copied image in the debug full-screen viewer
#   scripts/debug-device.sh shot <out.png>     save a screenshot
#
# Set ANDROID_SERIAL to pick a device. On Git Bash, MSYS path conversion is disabled below.
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))"
ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1 && [ -x "${ANDROID_HOME:-D:/Android/Sdk}/platform-tools/adb.exe" ]; then
    ADB="${ANDROID_HOME:-D:/Android/Sdk}/platform-tools/adb.exe"
fi
PKG="com.vpr.screenlate.debug"
SERVICE="$PKG/com.vpr.screenlate.overlay.ScreenlateAccessibilityService"

case "${1:-}" in
    install)
        "$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
        # The system drops the service from the enabled list some time after a package update; retry until bound.
        for _ in 1 2 3 4 5 6; do
            sleep 2
            "$ADB" shell settings put secure enabled_accessibility_services "$SERVICE"
            "$ADB" shell settings put secure accessibility_enabled 1
            sleep 2
            if "$ADB" shell dumpsys accessibility | grep -q "Bound services:{Service\[label=Screenlate"; then
                echo "Service bound"
                break
            fi
        done
        ;;
    images)
        "$ADB" shell "run-as $PKG mkdir -p files/testdata"
        for file in "$ROOT"/testdata/ocr/*; do
            name="$(basename "$file")"
            [ "$name" = "README.md" ] && continue
            "$ADB" push "$file" "/data/local/tmp/$name" >/dev/null
            "$ADB" shell "run-as $PKG cp /data/local/tmp/$name files/testdata/$name"
        done
        "$ADB" shell "run-as $PKG ls files/testdata"
        ;;
    show)
        # No -S: force-stopping the package would also unbind the accessibility service.
        "$ADB" shell am start -f 0x10008000 -n "$PKG/com.vpr.screenlate.MainActivity" --es debug_image "testdata/$2" >/dev/null
        ;;
    shot)
        "$ADB" exec-out screencap -p > "$2"
        ;;
    *)
        sed -n '2,9p' "$0"
        exit 1
        ;;
esac

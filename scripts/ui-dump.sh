#!/usr/bin/env bash
# Prints visible texts and content descriptions with their bounds from the current screen (uiautomator).
# Usage: scripts/ui-dump.sh [filter-regex]
set -euo pipefail
ADB="${ADB:-adb}"
export MSYS_NO_PATHCONV=1
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
"$ADB" shell cat /sdcard/ui.xml | python -c '
import re, sys
xml = sys.stdin.read()
for node in re.finditer(r"<node [^>]*>", xml):
    attrs = dict(re.findall(r"([\w-]+)=\"([^\"]*)\"", node.group(0)))
    label = attrs.get("text") or attrs.get("content-desc")
    if label:
        print(label[:60].replace("\n", " "), attrs.get("bounds", ""))
' | grep -E "${1:-.}" || true

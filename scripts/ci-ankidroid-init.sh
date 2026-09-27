#!/usr/bin/env bash
# Opens a freshly installed AnkiDroid on the CI emulator and taps "Get started" so it creates its local
# collection (no account, no sync). Prints what the screen shows at each step; never fails the job.
set -uo pipefail
ADB="${ADB:-adb}"
PACKAGE=com.ichi2.anki

screen() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" shell cat /sdcard/ui.xml 2>/dev/null | python3 -c '
import re, sys
for node in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    attrs = dict(re.findall(r"([\w-]+)=\"([^\"]*)\"", node.group(0)))
    label = attrs.get("text") or attrs.get("content-desc")
    if label:
        print(label[:60].replace("\n", " "), attrs.get("bounds", ""))
'
}

# Taps the centre of the first element whose label matches the regex; returns 1 if there is none.
tap() {
  local line
  line=$(screen | grep -iE "^($1) \[" | head -1)
  [ -n "$line" ] || return 1
  read -r x1 y1 x2 y2 <<<"$(echo "$line" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]$/\1 \2 \3 \4/')"
  echo "tap: $line"
  "$ADB" shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

"$ADB" shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
"$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
for step in 1 2 3 4 5 6; do
  sleep 8
  echo "--- AnkiDroid screen, step $step"
  screen
  tap "get started|ok|continue|allow" || true
done
"$ADB" shell input keyevent KEYCODE_HOME
exit 0

# Overlay windows and the system UI

What the bubble's accessibility overlay windows can learn about the system bars, the keyboard and fullscreen apps, and
which edge swipes the system takes from them. Measured on the emulator (Pixel_10_Pro, API 37, gesture navigation) on
2026-10-09.

## Insets

- `TYPE_ACCESSIBILITY_OVERLAY` windows with `FLAG_LAYOUT_NO_LIMITS` get no insets dispatch: an
  `OnApplyWindowInsetsListener` on the overlay's views never fired.
- Read them on demand instead. `windowManager.currentWindowMetrics.windowInsets` reflects the foreground app:
  `isVisible(statusBars() / navigationBars() / ime())` and `getInsets(ime()).bottom` (the keyboard's height).
  `maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(...)` gives the bar and gesture sizes whether a
  fullscreen app hides the bars or not.
- The trigger is the accessibility event `TYPE_WINDOWS_CHANGED` (`typeWindowsChanged` in the service config): it fires
  when a keyboard opens or closes and when a fullscreen app hides or shows the bars. The keyboard is a
  `TYPE_INPUT_METHOD` window in `windows`.
- Emulator values (px, density 3): `systemBars` top 156, bottom 72; `mandatorySystemGestures` top 192, bottom 96;
  `systemGestures` left and right 90; the keyboard's top at 1848.

## Edge swipes

- A touch that starts on the overlay inside a mandatory gesture strip and moves away from the edge may be taken by the
  system; the overlay then gets `ACTION_CANCEL`.
  - Bars shown: a pull down that starts on the overlay between the status bar and the strip's end (y 156–192) was
    cancelled at once. Starting below 192 worked.
  - Fullscreen, `BEHAVIOR_DEFAULT`: a swipe up from the bottom strip goes Home at once; a swipe down from the top strip
    shows the bars and cancels the touch (going on opens the notifications).
  - Fullscreen, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`: from the bottom strip the touch is cancelled and the bars
    show; from the top strip the overlay keeps the touch and the bars show for a while.
  - The app's fullscreen behavior cannot be read from the service, so the top and bottom docks stay outside the
    mandatory strips in every mode (`DockPlacement`). The left and right edges carry only the Back gesture, which is
    not mandatory: `systemGestureExclusionRects` on the bubble keeps it, also in fullscreen apps.
- The `ACTION_CANCEL` of a taken touch has no reliable position: one arrived near y 0 and docked the bubble at the top.
  The bubble's listener uses the last `ACTION_MOVE` instead and puts a bubble pulled out of the dock back.

## Testing

- `scripts/debug-device.sh show <file> default|swipe` opens the debug image viewer with the system bars hidden in the
  given behavior (`MainActivity.debugFullscreen`, debug builds only).
- `adb shell dumpsys window windows | grep ty=ACCESSIBILITY_OVERLAY` shows the overlay windows' positions and sizes;
  the first one is the bubble.
- `dumpsys activity activities | grep topResumedActivity` tells whether a swipe went Home.

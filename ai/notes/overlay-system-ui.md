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
  - The app's fullscreen behavior cannot be read from the service; whether a bar shows can. In fullscreen the top and
    bottom docks stand in the strip only with a third of the disc past it (see Placement below). The left and right edges carry only the Back gesture, which is
    not mandatory: `systemGestureExclusionRects` on the bubble keeps it, also in fullscreen apps.
- Placement (`DockPlacement.cap`, 2026-10-09): per edge, a bar shown now (`currentWindowMetrics` insets of
  `systemBars()`, or the keyboard at the bottom) puts the dock at the line past bars, cutout and mandatory strip with
  40% showing; no bar and no mandatory strip: the very edge, 40%; no bar but a strip (fullscreen): the very edge with
  max(60%, strip + a third of the disc) when that fits in the disc, else the line. On the emulator in fullscreen the
  48 dp bubble shows whole at the bottom (strip 32 dp) and its upper third pulls out without going Home; the top
  (strip 64 dp) keeps the line. Every dock window holds only the visible part (`DockPlacement.center` inverts it).
- `BEHAVIOR_DEFAULT` fullscreen: a drag that starts on the bubble at the right edge and ends in the bottom strip
  showed the bars, and the test viewer does not hide them again; the dock then moved to the line, as it should.
- Navigation mode: not needed for placement, the strips come per edge and orientation. If ever needed, the
  `tappableElement()` insets are empty at the bottom with gesture navigation and cover the button bar with buttons
  (the public way); `Settings.Secure` `navigation_mode` (0 buttons, 1 two buttons, 2 gestures) is readable but not
  public API. On phones the three-button bar moves to a side in landscape; the gesture handle stays at the bottom.
- The `ACTION_CANCEL` of a taken touch has no reliable position: one arrived near y 0 and docked the bubble at the top.
  The bubble's listener uses the last `ACTION_MOVE` instead and puts a bubble pulled out of the dock back.

## Window move animation

- The window manager animates a window's move when the same update also changes its size (`WindowState.hasMoved`
  counts a move only together with a changed size; the animation is `window_move_from_decor`, about 400 ms,
  decelerating). Since v0.2.2 the docked bubble's window holds only the part that shows, so pulling it out resizes and
  moves it at once: on the owner's phone (2026-10-10) the bubble stayed at the edge for about half a second while the
  aim, in the layer window, followed the finger, then flew to the finger. Dropping it into the dock, which shrinks and
  moves the window, would glide the same way.
- In a trace (`atrace ... wm gfx view`) the animation shows on system_server's `android.anim.lf` thread
  (`SurfaceAnimationRunner`, `notifyAnimEnd-SurfaceAnimationRunner$SfValueAnimator`), starting 2 ms after the
  client's `relayoutWindow ... resize=true` and lasting about 405 ms on the emulator.
- `LayoutParams.setCanPlayMoveAnimation(false)` (API 34+, sets `PRIVATE_FLAG_NO_MOVE_ANIMATION`, shown as
  `pfl=NO_MOVE_ANIMATION` in `dumpsys window windows`) turns it off; every overlay window sets it in
  `OverlayWindows`. Android 11-13 have no public switch, so the glide likely remains there (not checked on the API 30
  image).
- On the emulator the slow first frame after a pull-out (new surface, buffers) hides the difference in a screen
  recording; the trace is the reliable check.

## Testing

- `scripts/debug-device.sh show <file> default|swipe` opens the debug image viewer with the system bars hidden in the
  given behavior (`MainActivity.debugFullscreen`, debug builds only).
- `adb shell dumpsys window windows | grep ty=ACCESSIBILITY_OVERLAY` shows the overlay windows' positions and sizes;
  the first one is the bubble.
- `dumpsys activity activities | grep topResumedActivity` tells whether a swipe went Home.

# Android 7 support (future plan, 2026-11-12)

Status: the owner requested this as future work. No implementation has started; the current app still requires
Android 11 (API 30).

## Goal and decisions

| Decision | Choice |
| --- | --- |
| Supported versions after the work | Android 7.0/7.1 (API 24–25) and newer. Android 6 (API 23) and older are outside the scope. |
| Distribution | One application and package, with the existing per-ABI and universal APKs. No separate legacy application. |
| Screen capture on API 30+ | Keep the current AccessibilityService capture, including window capture on API 34+. Its behavior must not change. |
| Screen capture on API 24–29 | Add a MediaProjection backend with the system's capture-consent flow and explicit session lifecycle handling. |
| Shared features | Keep lookup, dictionaries, camera and image lookup, Anki, audio, settings and overlay behavior in shared code wherever the platform allows it. |
| APK size | Avoid a noticeable size increase. Do not duplicate native libraries, OCR models or dictionaries. Measure the real APK change; use desugaring only where needed and consider compatible API replacements when they are smaller and clearer. |
| Maintenance | Add API 24 regression coverage while retaining the existing modern-Android checks. |

## Work outline

1. **Run a short compatibility prototype first.** Lower the minimum SDK in the prototype, audit the actual dependency
   minimums and build errors, and check AndroidX, CameraX, ML Kit, WorkManager, the WebView page, hoshidicts, MNN and
   the other native libraries. Identify required desugaring, legacy launcher resources and platform-specific storage,
   notification and locale behavior. Re-estimate the full work from the prototype's results.
2. **Implement the legacy screen-capture backend.** Add the MediaProjection consent entry point, projection service and
   VirtualDisplay/ImageReader capture. Produce the same captured-bitmap contract as the current backend. Handle consent
   denial and cancellation, projection/session loss, process death and reboot, rotation and display-size changes,
   secure windows, capture failures and resource cleanup. Keep hiding Screenlate's own overlays where the capture path
   requires it.
3. **Integrate by API level.** Select MediaProjection only below API 30; retain AccessibilityService capture on newer
   versions. Isolate newer platform types and calls behind API-safe boundaries so old Android never loads them. Add
   understandable states and errors for consent and unavailable capture without changing the modern flow.
4. **Resolve the remaining API 24 compatibility issues found by the prototype.** Check old and updateable WebView builds,
   overlay windows, accessibility text, camera and image import, notifications, background work, app language,
   backups, updates, AnkiDroid and all supported ABIs. Keep any compatibility fallbacks small and shared.
5. **Verify behavior and cost.** Add unit tests for capture selection and lifecycle logic. Exercise the main overlay,
   OCR, popup, camera/image, dictionary and Anki flows on API 24 or 25 and rerun existing modern-Android regressions.
   Use a real Android 7 device in addition to an emulator before calling support release-quality. Measure capture
   latency, peak memory, OCR performance, idle behavior and APK size on both capture paths.
6. **Publish through the existing application.** Update the requirements and usage documentation, CI/device coverage
   and release notes only after the support is verified. The release keeps one application identity and the existing
   update feed; Android 7–10 users see the additional system capture-consent flow, while Android 11+ users keep the
   current one.

The preferred first milestone is only the compatibility prototype and a working MediaProjection screenshot. It will
show whether any dependency or platform limitation changes the scope before full implementation begins.

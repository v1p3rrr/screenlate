# Camera Photo Rotation (90° Clockwise)

Date: 2026-11-17  
Status: In progress

## 1. Context and Goals

### Motivation
When photographing text (books, documents, manga, or screens) using the in-app camera or importing photos from the gallery, users frequently take photos with the phone tilted downward. Accelerometers and orientation sensors often misdetect portrait orientation as landscape (or vice-versa), storing the photo sideways (typically rotated 90° counter-clockwise).

Currently, Screenlate has no mechanism to rotate an already captured or imported photo within the camera lookup view. The user must either delete and retake the photo or use an external gallery editor.

### Objectives
1. **Rotate 90° Clockwise:** Add a rotate action that turns the photo 90° clockwise in oriented space. Rotating 1–3 times can reach any desired upright orientation (90°, 180°, 270°).
2. **Top-Level Placement in Overflow Menu:** Place the "Rotate 90°" action as the first item in the three-dots overflow menu (`ic_more_vert`) when a photo is open (`photo != null`).
3. **Clean OCR Invalidation:** Rotating a photo invalidates previous recognition coordinates and text bounding boxes. The stored recognition must be reset to `PhotoText.None`, ongoing OCR jobs cancelled, and the status strip updated to "Not recognized" so the user can tap "Recognize" when ready.
4. **History and File Synchronization:** When rotating a photo (including one already saved in history), update its orientation in Room database (`PhotoEntity.orientation`), regenerate its thumbnail in history storage, and update the EXIF orientation attribute in the file on disk so exports retain the correct orientation.
5. **Immediate UI Re-fit:** Ensure `PhotoViewer` detects orientation changes, re-fits the viewport within the available screen area, and decodes the new upright preview bitmap without stale orientation glitches.

---

## 2. Decision Table

| Decision | Settled Option | Rationale |
|---|---|---|
| **Rotation direction** | **90° clockwise (↻, вправо)** (User decision) | Solves the most common right-handed phone desk-tilt bug in 1 tap; aligns with universal mobile gallery conventions (Google Photos, Apple Photos). |
| **OCR handling after rotation** | **Reset to `PhotoText.None` (Unrecognized)** (User decision) | Avoids showing stale or rotated word boxes. Allows user to confirm orientation before re-running OCR. |
| **Menu action title** | **"Rotate 90°" / "Повернуть на 90°"** (User decision) | Clearly informs the user of the exact rotation angle and direction. |
| **Menu placement** | **First item under three dots (`ic_more_vert`)** | Immediate discoverability without cluttering the main top bar, which reserves icons for shutter/camera. |
| **Orientation persistence** | **Persist in Room & regenerate thumbnail if saved** | History and future re-openings retain the corrected orientation. |

---

## 3. Architecture and Implementation Steps

### 3.1. `PhotoOrientation`
- Add `fun rotateClockwise(): PhotoOrientation` to `com.vpr.screenlate.core.photos.PhotoOrientation`:
  - `NORMAL (1) -> ROTATE_90 (6)`
  - `ROTATE_90 (6) -> ROTATE_180 (3)`
  - `ROTATE_180 (3) -> ROTATE_270 (8)`
  - `ROTATE_270 (8) -> NORMAL (1)`
  - Flipped forms:
    - `FLIP_HORIZONTAL (2) -> TRANSPOSE (5)`
    - `TRANSPOSE (5) -> FLIP_VERTICAL (4)`
    - `FLIP_VERTICAL (4) -> TRANSVERSE (7)`
    - `TRANSVERSE (7) -> FLIP_HORIZONTAL (2)`

### 3.2. `core:photos` Persistence & Index
- In `PhotoDao`:
  Add query:
  ```kotlin
  @Query("UPDATE photos SET orientation = :orientation, recognitionFile = NULL, recognitionRevision = recognitionRevision + 1, attemptFile = NULL WHERE id = :id")
  abstract suspend fun rotate(id: Long, orientation: Int): Int
  ```
- In `PhotoIndex`:
  Add `suspend fun rotate(id: Long, orientation: Int): Boolean`
- In `RoomPhotoIndex`:
  Implement using `dao.rotate(id, orientation) == 1`
- In `PhotoDocuments`:
  Add `suspend fun rotate(documentId: Long): PhotoDocument?`
- In `PhotoStore`:
  Implement `rotate(documentId: Long)`:
  1. Retrieve row by `documentId`.
  2. Compute new orientation: `PhotoOrientation.fromExif(row.orientation).rotateClockwise()`.
  3. Execute `index.rotate(documentId, newOrientation.exif)`.
  4. Delete existing recognition file and attempt file on disk if present.
  5. Attempt to update EXIF tag in `image` file:
     `runCatching { ExifInterface(image.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, newOrientation.exif.toString()); saveAttributes() } }`
  6. If `row.saved`, regenerate thumbnail atomically with new orientation.
  7. Return updated `documentOf(...)`.

### 3.3. `CameraViewModel`
- Add `fun rotatePhoto()`:
  - Check `state.photo != null && !state.busy`.
  - Set `busy = true`.
  - Cancel any active recognition via `recognition` (or job cancellation).
  - Call `documents.rotate(photo.id)`.
  - Reset `viewport = null`.
  - The updated photo document emits through `documents.current`, setting `text = PhotoText.None`.
  - Set `busy = false`.

### 3.4. `CameraScreen` & `CameraPhotoControls`
- Add vector icon `ic_rotate_right.xml` in `app/src/main/res/drawable/` (Material Design rotate_right).
- Add string resource `camera_rotate`:
  - `values/strings.xml`: "Rotate 90°"
  - `values-ru/strings.xml`: "Повернуть на 90°"
- In `CameraScreen.kt`:
  In `actions = buildList { ... }` when `photo != null`:
  Add `CameraAction(R.drawable.ic_rotate_right, stringResource(R.string.camera_rotate), !state.busy && !working, inMenu = true, run = viewModel::rotatePhoto)` as the first item with `inMenu = true`.

### 3.5. `PhotoViewer`
- Update `remember(document.id)` blocks to `remember(document.id, document.orientation)` for `fit`, `moved`, `bitmap`, `sample`, and `detailRegion`, ensuring the viewer re-fits and redraws cleanly upon rotation.

---

## 4. Verification Plan
1. Unit tests:
   - `PhotoOrientationTest`: verify `rotateClockwise()` on all 8 EXIF orientations (including full 360° cycles).
   - `PhotoStoreTest`: verify `rotate` updates orientation in Room, clears recognition/attempt files, increments revision, and updates thumbnail if saved.
   - `CameraViewModelTest`: verify `rotatePhoto` invokes `documents.rotate` and handles busy state.
2. Build & lint:
   - Run `./gradlew assembleDebug testDebugUnitTest`.
   - Run page tests `npm --prefix scripts/page-tests test`.

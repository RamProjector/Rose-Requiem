# Rose-Requiem feature expansion plan

## Goal

Complete the planned music-library capabilities before dedicating a phase to comprehensive regression tests. Work remains incremental in pull requests so each feature can be reviewed and compiled; tests are not a reason to leave planned features indefinitely unimplemented, but build/security failures must still be fixed before merge.

## Feature order and acceptance criteria

1. **Playlists and organization**
   - Create, rename, and delete playlists; add/remove tracks; avoid duplicate paths and duplicate names.
   - Persist locally using the existing Preferences DataStore, without introducing a history database.
   - Handle tracks that have moved or disappeared; playback must validate file access.
   - Add playlist navigation and useful empty/loading/error states.

2. **Metadata editor**
   - Edit supported local audio tags using the existing TagLib dependency.
   - Preview changes and confirm before writing; show per-file success/failure.
   - Avoid destructive bulk writes, preserve unsupported tags where possible, and refresh MediaStore after changes.

3. **FLAC download workflow**
   - Support only authorized/public sources; do not bypass DRM, paywalls, or access controls.
   - Validate URLs, content type, file extension/signature, storage availability, and duplicate destinations.
   - Expose progress, cancellation, and actionable failures. Do not silently convert lossy audio into FLAC.

4. **Explainable music categorization**
   - Use local metadata and transparent heuristics to suggest categories/playlists.
   - Explain the signals used and let the user accept, edit, or dismiss every suggestion.
   - Keep categorization on-device and avoid a paid AI/API dependency.

5. **Player and queue completion**
   - Build on the existing Media3 playback service and queue.
   - Improve queue visibility, current-track controls, repeat/shuffle behavior, and recovery when a file disappears.
   - Preserve background playback and system media controls.

6. **Workflow and performance integration**
   - Make Home, playlists, player, lyrics search/downloads, and settings navigation coherent.
   - Preserve lazy lists, stable keys, and the 250-track lyrics batch size / 5,000-record history cap.
   - Measure scroll performance before claiming improvements.

## Validation policy

Feature implementation can precede broad regression-test authoring, as requested. Each PR still needs compilation, lint/static analysis, dependency/security checks, and basic manual or emulator verification where available. After feature integration, add focused unit tests for data rules and UI/workflow regression tests, then run the scroll benchmark and an end-to-end emulator pass. No physical-device validation is claimed unless actually performed.

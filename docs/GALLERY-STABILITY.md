# Gallery interaction and stability

## Quiet density transition

The gallery and album grid stay completely still while the fingers move. When a deliberate pinch ends, a single native grid fades out for 65 ms, changes columns and restores its focal point while transparent, then fades in for 135 ms. Layout/measurement frames can add to the 200 ms animation time. The fixed page header, filters and navigation do not fade or resize.

There are no per-cell movement/scale transforms, captured screens, geometry prediction maps, or entering/leaving photo overlays. Photo proportions, album covers and label fonts come directly from their normal native layouts. Item placement animation is suspended during the switch. Invisible items reject input; visible file actions continue to refer to the displayed native layout.

The actual column write is deferred until fade-out finishes. Cancellation before that point never commits a delayed change. Navigation, backgrounding, context changes and viewport resizing cancel the transition and restore opacity/input. A second pinch is not acquired during the short transition. At the column limits, the grid stays unchanged without a fade or fake wait. Compose's reduced-motion duration scale is respected.

## Gesture ownership and anchoring

- Two current pointer positions establish the initial span, including newly pressed fingers. Touch slop and column-relative hysteresis distinguish a deliberate pinch from small release jitter or a two-finger pan.
- The pair ending, including one finger lifting, starts the transition. Third-finger interruptions cancel.
- A stable file key, relative point inside the cell and viewport coordinate determine the new scroll position. The controller waits for the NEW columns to measure, then uses their actual cell height and content padding. The 700 ms deadline is a failure bound, not an animation delay.
- Once committed, visible UI columns update immediately and a cancellable persistence job saves the latest choice after 320 ms, reporting storage failures.
- Displayed rows and keys are held together for the gesture/transition without pausing database sync. Updates are applied after settling. Paging windows are bounded to 400 entries.
- Native scrolling, selection auto-scroll, elastic motion, filter collapse and pull-to-refresh do not compete with the density controller.
- Albums have independent saved browsing positions. Saved neighboring IDs provide a fallback if the original reference disappears. The pager loads the saved reference's ranked window before restoration.
- Accessibility actions offer larger/smaller thumbnails without pinching. Media cells expose appropriate browsing and selection roles.

## Data and thumbnail behavior

The paging request includes the selected tab and query. Returning from Albums to All media starts a media request even if their image/video filters match. Initial sort preferences resolve before media loading. Album loading/error/empty states and retry are distinct.

Normal refresh uses incremental sync; complete repair/reindexing remains separate. Thumbnail revisions include modification, local path, remote file ID, icon ID and content hash, including album covers and startup warmup. Visible thumbnail failures receive at most two delayed retries per revision/connectivity epoch; returning connectivity and explicit refresh permit another bounded attempt. Native decoding and remote thumbnail downloads have separate concurrency limits, with an 8 s remote failure bound. Hidden access checks remain in place.

Fullscreen previews can use known original dimensions while displaying a thumbnail, preserving their fit frame and pan bounds when the original arrives.

## Verification

Automated coverage checks no movement or column changes while pinching, no commit before opacity reaches zero, repeated changes and click targets, focal coordinates with padding, first-pointer release, resize/navigation cancellation, jitter and limits. It also covers Albums → All media after Photos, rapid preference persistence, missing-anchor fallback and cover revision invalidation. `GalleryDensityDeviceTest` exercises the Android pointer and measurement pipeline in the release workflow's emulator.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:compileDebugAndroidTestKotlin
./gradlew :app:connectedDebugAndroidTest
```

Physical-device acceptance should additionally exercise a large library, fast opposing gestures, selection, arriving updates, offline/reconnect, rotation and reduced motion. Emulator checks do not establish frame timing on every phone.

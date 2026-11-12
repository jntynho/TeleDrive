# Gallery interaction and stability

The gallery density gesture now transforms individual live cells. It no longer scales the viewport, records two full-screen graphics layers or fades a captured old screen over new click targets. Media keeps its 4:5 aspect ratio; date text retains its size.

## Gesture ownership and layout

- Two current pointer positions establish the initial span, including newly pressed fingers. Touch slop and column-relative hysteresis distinguish a deliberate pinch from a two-finger pan or small release jitter.
- One density step commits when the pair ends, including when one finger remains down. Third-finger interruptions cancel. Limits do not create a fake commit or a 600 ms wait.
- UI columns change immediately. A cancellable persistence job saves the final requested value after 320 ms and reports storage failures.
- A stable file key, relative point inside its tile and viewport coordinate determine the new scroll position. The controller awaits actual cell measurement and includes content padding; 700 ms is a failure bound, not an animation delay.
- One 280 ms progress value settles geometry. Only entering/leaving live cells receive temporary overlay compositions, with real file actions and matching hit bounds. There is no screenshot overlay. Normal lazy-item placement animation is suspended during density changes.
- Displayed rows/keys are held together for the gesture/settle, without pausing database sync. Updates are applied after settling. Paging windows are bounded to 400 entries.
- Selection auto-scroll, native scrolling, elastic motion, filter collapse and pull-to-refresh do not compete with the density controller. Context changes, viewport resizing, navigation and backgrounding reset it. Animation follows Compose's system duration scale.
- Albums use the same geometry controller and keep independent saved browsing positions. Accessibility actions provide larger/smaller thumbnails without a pinch; media cells expose their appropriate browsing/selection roles.

## Data and thumbnail behavior

The paging request includes the selected gallery tab as well as the query. Returning from Albums to All media therefore creates a new media request even when its image/video filters are identical. Initial sort preferences are resolved before media loading. Album loading/error/empty states and retry are distinct. Browsing positions are not overwritten when leaving a tab/query.

Normal refresh uses incremental sync; complete repair/reindexing remains separate. Thumbnail requests share a revision containing file modification, local path, remote file ID, icon ID and content hash, including album covers and startup cache warmup. Visible thumbnail failures get at most two delayed retries per revision/connectivity epoch; returning connectivity and explicit refresh allow another bounded attempt. Native thumbnail decoding and remote thumbnail downloads have separate concurrency limits; remote requests have an 8 s failure bound. Existing Hidden access checks remain in place.

Fullscreen image previews can use the known original dimensions while the thumbnail is displayed, preserving their fit frame and pan bounds when the original arrives.

## Verification

Automated coverage includes density geometry at every level, fixed header/album-label heights, focal coordinates with padding, jitter, reversal and boundaries; repeated real Compose reflows and click targets; first-pointer release; Albums → All media after Photos; and album-cover revision invalidation without a timestamp change. `GalleryDensityDeviceTest` runs the native Android pointer/measurement pipeline in the release workflow's emulator.

Run the relevant checks with the normal Android toolchain:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:compileDebugAndroidTestKotlin
./gradlew :app:connectedDebugAndroidTest
```

Physical-device acceptance should also exercise top/middle/end of a large library, rapid opposing gestures, selection, updates arriving during a gesture, offline/reconnect, screen rotation and reduced motion. Automated emulator checks do not establish frame timing on every phone. No universal frame-rate guarantee is implied.

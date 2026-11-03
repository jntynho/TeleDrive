# Elastic scrolling

The shared overscroll effect now applies bounded translation and actual axis-specific scaling in a draw layer. Pull resistance saturates at the smaller of 18% of the viewport and 72dp; scaling is capped at 4%. The spring return is critically damped and a new drag cancels it from the current visual position. Detaching cancels animation and clears state.

Native lazy lists, rows, grids and ScrollState containers use shared adapters. They calculate distance to the actual first/last visible items, including trailing padding, without guessing from item counts. Within 48dp, a smoothstep curve introduces up to 0.9% anticipatory stretch. It follows the drag direction and does not change logical scroll consumption or block the final item. Unknown/unmeasured edges do not trigger anticipation. Programmatic scrolls do not drive anticipation. Horizontal and vertical containers have separate axis treatment.

All application call sites for LazyColumn, LazyRow, LazyVerticalGrid, verticalScroll and horizontalScroll use the adapters, including settings, onboarding, dialogs, sheets, lyrics, file pickers and retained legacy UI. The global factory remains a boundary fallback for native scrollables such as pagers. The adapter passes one effect to the native scrolling container, which attaches the visual node itself; it does not attach a second overscroll modifier. Parent nested scrolling and pull-to-refresh participate in native consumption before residual edge pull is handled. Zoom and media seek gestures are not intercepted.

Validation includes bounded/symmetric resistance and proximity tests, a real Compose touch test demonstrating changed draw geometry before the end and a return without moving logical scroll position, repeated first/last grid boundary pulls, and the existing navigation/component tests. The previous unbuilt FileActionsSheet also needed Material3 experimental API opt-in; this is fixed in this build.

This release also builds the previously pushed welcome/sign-in, library pass and file-browser/shared-sheet changes. No new animation dependency is added, and the three-ABI release/signing checks remain unchanged. Automated checks do not establish a device-specific frame rate.

Local validation completed: 241 app tests plus 66 shared tests (307 total), no failures or skipped tests. `lintRelease`, `assembleDebug` and `assembleDebugAndroidTest` succeeded. Signed release packaging and installation are checked separately by GitHub Actions.

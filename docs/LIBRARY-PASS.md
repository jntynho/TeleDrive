# Home library pass

The approved Home concept is implemented with an asymmetric 12/32 dp silhouette, a small lilac backing layer and a cream → blush → lavender gradient. The card uses dark text in every app theme. A faint vector fold adds depth without image assets or rendering libraries.

Overview displays indexed bytes and the indexed file count. Its footer opens All files; it does not claim every indexed file has already been uploaded. Collections offers Favorites, Archived and Trash. The sample collection counts from the concept are not displayed as real data.

Both faces remain measured inside the same container, including the two heading texts. Switching changes draw-layer opacity and an 8 dp horizontal offset with one matched 340 ms FastOutSlowIn transition. There is no layout-size animation or 3D rotation. Rapid reversals retarget the same animation. The inactive face has its semantics cleared and actions disabled. The toggle reserves the same width for either label. Large text can increase the shared measured height rather than clipping collection labels.

Category navigation stays outside the card in a two-column grid. Photos and Videos open their corresponding Gallery filter; Audio opens Audio, and Files opens the file browser. All files / Activity / Backups use a compact row with a stacked fallback at narrow widths or large font scales. Home loading uses the same pass and category components and spacing.

Validation: Kotlin syntax parsing, call-site review and whitespace checks only. Per the user's instruction, Gradle, compilation, APK builds and device tests were not run. The commit uses `[skip ci]` to prevent the release workflow from starting on push. Runtime animation, layout and navigation checks remain pending a later build request.

## Build validation update

The earlier build deferral is superseded by the user-authorized release [v1.0.0-build.44](https://github.com/jntynho/TeleDrive/releases/tag/v1.0.0-build.44). [Workflow 44](https://github.com/jntynho/TeleDrive/actions/runs/41704922759) passed configuration validation, tests/lint/release packaging, native device tests, signing/installation/phone-page checks and publication. Local validation passed 241 app tests plus 66 shared tests (307 total). All three standalone APK assets were verified on the public release page. Live Telegram account login and device-specific frame-rate measurement were not exercised. This documentation update does not modify the released application.

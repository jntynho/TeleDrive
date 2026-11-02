# TeleDrive interaction behavior

The public app name is **TeleDrive**. Keep `app.tgdrive.next`, the existing signing certificate, Telegram channel discovery names and existing download directories compatible with previous releases.

- Search replaces the toolbar in place. The toolbar keeps its height; opening search requests focus, closing it clears the query and dismisses the keyboard. Back closes search before navigating.
- Home keeps its library overview and backup shortcut, without the separate backup status dashboard. Upload has one circular action.
- File browsing defaults to List on upgrade. `files_list_default_applied` records this migration on the next preference write. An explicit later Grid choice is retained.
- Compose scrolling receives `ElasticOverscrollFactory` from the shared theme. Each scrollable owns its own effect. Nested scroll/pull-to-refresh consumes movement before the boundary spring. The effect animates a layer and releases its coroutine on detach.
- This boundary behavior was researched in [Only Player](https://github.com/Kindness-Kismet/only_player), which provides Miuix 0.9.4's overscroll factory through its theme. TeleDrive's implementation uses Compose's public overscroll contract and its own bounded resistance function; it adds no Miuix UI dependency.
- Navigation commits a normal horizontal pop after Back. Route-local back handlers retain priority for selection and search; gesture progress does not reveal the previous destination.
- Image pan bounds use the actual fitted image dimensions. Pinch movement updates synchronously; double-tap is animated. Zoom disables paging and hides chrome until the image returns to fit.
- Video uses a TextureView so the surface follows Compose navigation. Controls stay visible while scrubbing, seeking commits on release, and lifecycle observation follows the current player instance.
- Sliders keep native touch, keyboard and accessibility behavior with slim custom tracks. Settings changes persist on drag completion rather than on every pointer update.

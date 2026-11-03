# TeleDrive interaction behavior

The public app name is **TeleDrive**. Keep `app.tgdrive.next`, the existing signing certificate, Telegram channel discovery names and existing download directories compatible with previous releases.

- Search replaces the toolbar in place on the same theme background. Fixed slots keep its height and action positions stable; a restrained fade completes before keyboard focus. Closing clears the query and dismisses the keyboard. Back closes search before navigating. Search results use a draw-only reveal without moving rows. The transition was researched in [Mpv](https://github.com/jntynho/Mpv).
- Home keeps its library overview and backup shortcut, without the separate backup status dashboard. Its circular floating action opens exactly two choices: Upload files and Upload folder. Each calls the platform picker directly, then the shared application-scoped importer; it never navigates to the file manager first. Other library tabs have no upload FAB.
- The library card toggles between storage composition and Favorites / Archived / Trash. The overview reserves the same measured space on both faces, including at larger font scales. Hidden files remain accessible in Settings → Security.
- Home, Audio and Gallery loading placeholders share the real components or their exact cover/grid metrics. One draw-only pulse per scene avoids per-frame layout and recomposition. Album loading uses the real album card. Equal-width media/audio tabs keep native selection semantics and omit press ripples; Albums / All media are in Gallery Sort.
- Audio uses one playback controller across the mini player, its expanded sheet and full-screen preview. Previous, next, seek, retry and stop address that controller. Starting video pauses audio. Expanding the mini player uses an overlay sheet rather than changing the page's content height.
- The startup surface covers actual session/preferences initialization and waits for two rendered frames before fading away. There is no fixed artificial warmup delay. Startup/update dialogs wait until the surface is ready.
- File browsing defaults to List on upgrade. `files_list_default_applied` records this migration on the next preference write. All files now uses a single-column list; obsolete file-grid preferences are no longer exposed. Gallery keeps its independent grid settings.
- Compose scrolling receives `ElasticOverscrollFactory` from the shared theme. Each scrollable owns its own effect. Nested scroll/pull-to-refresh consumes movement before the boundary spring. The effect animates a layer and releases its coroutine on detach. Resistance is bounded at 20% of the viewport with a critically damped return spring; reversing a drag unwinds accumulated displacement.
- This boundary behavior was researched in [Only Player](https://github.com/Kindness-Kismet/only_player), which provides Miuix 0.9.4's overscroll factory through its theme. TeleDrive's implementation uses Compose's public overscroll contract and its own bounded resistance function; it adds no Miuix UI dependency.
- Navigation uses a 360 ms shared horizontal transition in both directions. It commits a normal horizontal pop after Back. Route-local back handlers retain priority for selection and search; gesture progress does not reveal the previous destination.
- Image pan bounds use the actual fitted image dimensions. Pinch movement updates synchronously; double-tap is animated. Zoom disables paging and hides chrome until the image returns to fit.
- Video uses a TextureView so the surface follows Compose navigation. Controls stay visible while scrubbing, seeking commits on release, and lifecycle observation follows the current player instance.
- Sliders keep native touch, keyboard and accessibility behavior with slim custom tracks. Settings changes persist on drag completion rather than on every pointer update.

- Top-level destinations save and restore their back stacks, scroll positions, filters and search state. Rapid tab presses coalesce to the latest requested destination after the current transition resumes; they do not add duplicate routes. Peer transitions travel one eighth of the viewport instead of sweeping across the whole screen.
- Preview sequences carry the local-copy restriction, including Audio → On device. Gallery and collection query failures show a retry action instead of an empty-library message.
- Secondary screens share a 64 dp header and title typography. Standalone search uses the same expandable search chrome. Connected settings groups draw separators on a continuous surface.
- Activity counts paused tasks separately. Library information has its own explicit action; entering API credentials starts near the top of the page, with secondary help and named setup phases.

## Media and presentation refinement

- Library and grouped cards use tonal gradients. Library details no longer has a dedicated info action. Recent rows include a date, inset divider and contextual sheet; audio rows show metadata artist and duration.
- Video thumbnails share a central play badge. Transfers use a 52 dp rounded square. Album corners use 12/9 dp rather than large capsule corners. Gallery selection scales its thumbnail and fades its badge.
- Audio results open the full preview. Expanded mini-player uses a full-window dialog. Artwork-derived ambient gradients animate in draw, with a static position while paused. No Salt Player APK, playback engine or new UI dependency is included; its release repository provides no full player source.
- Local lyrics are bounded to 32,000 characters, with bounded ID3 USLT, FLAC Vorbis-comment and M4A lyric atom parsing. Missing or malformed lyrics never prevent playback. Remote-only metadata remains dependent on metadata available locally.
- Video secondary tools sit above the seek bar; rotation restores the previous activity orientation. A top scrim preserves photo/video chrome contrast.
- Startup preloads the recent-library query with a 400 ms budget alongside existing initialization. It does not promise to prerender every animation or hold startup for network update checks.
- Update comparison includes the Android build code, targets the renamed TeleDrive repository, bypasses stale HTTP cache and distinguishes manual check failures from an up-to-date result. Background failures retry; disabled automatic checks are respected.
- Transfer notifications sample actual queue progress at most once per second, display current names and a completion summary, and release observation when work ends.

# Transfer and playback changes

TeleDrive uses the Telegram account's private drive channel. This is an independent Kotlin implementation informed by the behavior and architecture of [Telegrive](https://github.com/daveragos/telegrive), reviewed at `6489e79f0350ac8d3440f9c00871f7a85159e404`. No Flutter engine, Dart source, application credentials, or resources from that project are bundled.

## Queue and originals

- Two upload slots, one server-side copy slot, and three independent download slots. The reduced concurrency setting uses one upload and two downloads. Copies are spaced by 400 ms; ordinary upload submissions have no invented messages-per-minute quota or per-file sleep.
- A cold background worker restores TDLib from the stored/bundled application identity before starting transfers; it does not rely on an Activity being open.
- Android selection records provider metadata and the source URI before reading the original. The foreground worker stages one original per available upload slot. Persistent URI grants are requested; temporary scanner output is copied immediately instead.
- The selected drive session is reused; uploads do not re-discover and re-validate the storage channel for every file.
- Each original is sent as a document with its manifest in the same caption. Photos are uncompressed and use Telegram's generated preview; videos retain client-generated thumbnails. Files exceeding Telegram's per-file limit use the existing resumable part protocol.
- Pending message identities are recorded. Interrupted sends reconnect to TDLib's pending delivery, reconciling the channel's most recent 100 messages before another send. Completed parts are skipped. This bounds recovery work; it does not claim distributed exactly-once delivery if a delivery is lost from both local state and the reconciliation window.
- Telegram-provided waits pause all new uploads/copies, persist across process recreation, and do not consume the transient-failure retry budget. Downloads continue independently. Network retries use 5/20/60-second backoff; permanent input/permission failures surface without repeated retries.
- Pausing/canceling observes database controls independently of network progress; a stalled TDLib send is canceled immediately. A process-local ownership registry prevents reopening the UI from requeuing live worker operations. Canceled uploads remain suppressed during automatic orphan recovery, including after their Activity history is cleared; an explicit upload/retry enables them again.
- Queue claims are conditional database updates. A successor WorkManager drain closes the race between adding a batch and the running worker exiting. Empty lanes stay available while another lane is active.
- Database migration 11→12 preserves existing files and adds source references, offline protection, retry attempts, and retry deadlines. No uploaded files, Telegram sessions, signing keys, package identity, or channel IDs are reset.

## Adding files

The shared addition sheet offers files, original photos/videos, PDF document scanning, private camera capture, folders, Google Photos import, and copying from Telegram chats. Scanner correction/cropping runs through Google Play Services and requires a supported device; the APK includes only the scanner client, with no bundled OCR/model/native engine.

Name conflicts offer Replace, Keep both, and Skip with an apply-to-remaining-conflicts choice. Replace moves the previous copy to Trash rather than permanently deleting it. A hidden conflict dialog is canceled on a drive/session change.

Google Photos uses local Google Takeout ZIP parts, not Google OAuth or scraping. The importer reads original media, dates, album folders and favorite sidecars. It prepares one entry at a time, validates entry length and disk space, and never extracts archive-controlled filesystem paths. Preparation is app-scoped; queued uploads continue with the worker. If Android kills preparation, selecting the same export parts again rechecks indexed originals. Multiple album folders are represented as server-side drive copies once their original is backed up.

Copying from a Telegram chat uses a forwarded copy with a replacement caption in one request. Protected chats or inaccessible messages can be rejected by Telegram. Direct chat copies are unavailable with content encryption enabled; downloading and uploading an original uses the encrypted upload path instead.

## File actions

A shared sheet covers Open, Download to this phone, Send in Telegram, Make available offline, Share, Starred, Rename, Move, Show in folder, Details, Show in Telegram, Archive, and Trash. Public downloads are separate from private offline copies. Offline pins prevent automatic local cleanup. Sharing a remote file first obtains a local copy; encrypted files use a decrypted local share rather than sending ciphertext as a normal document.

## Audio and video

One application-owned audio player and MediaSessionService serve Now Playing, the mini player, notifications, headset and lock-screen controls. Reopening the current track does not toggle playback. Rapid skip commands reserve their destinations synchronously; stale lookups are canceled and cannot restart an intermediate track. Manual skips preserve pause, and Repeat all wraps in both directions.

The pager changes tracks only after settling; images and search do not pause music. The player follows live deletion/rename state. Video requests proper audio focus and preserves play intent when revisiting a page. Metadata/artwork/lyrics are optional parallel work, coalesced and bounded, and do not block decoder preparation. Lyrics interpolate only in visible playback UI; missing timing is never invented.

## Verification limits

Automated tests simulate hundreds of transfers, server waits, transport races, pending-send recovery, migration, URI preparation, and rapid audio skipping. They do not use a person's Telegram session or assert that Telegram will never rate-limit an account. Native camera/scanner interaction and sustained throughput should also be exercised on physical phones with representative originals and networks.

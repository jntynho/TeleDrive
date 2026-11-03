# Security and privacy — private-channel rebuild

TG Drive 1.0 uses the Android engine from [DriveInfinity](https://github.com/ZHINFINITY/DriveInfinity), pinned in [the rebuild inventory](REBUILD-PARITY.md). New uploads go through the on-device TDLib client into a private broadcast channel owned by the signed-in Telegram account. Multiple channels are supported. There is no mandatory application storage backend. Android backup of app data is disabled.

## Telegram and encryption

Telegram cloud channels are not end-to-end encrypted. Ordinary uploads remain readable as documents in Telegram. Optional file encryption uses the reference `TDE1` authenticated AES-256-GCM chunked format; optional encrypted captions protect names and folder metadata, and thumbnail encryption is a separate preference. File size, account/channel identity, timing and access patterns can remain visible. Existing TG Drive 0.x vaults use their original Tink format and are only read by the compatibility reader.

Content keys, API credentials and the local TDLib key are wrapped with Android Keystore. Existing session/content keys are never silently replaced when they cannot be read. Wrapped-key writes use atomic replacement. An existing TDLib database with a missing key fails safely. Cache/thumbnail keys can be regenerated because their content is disposable.

Use the key backup/passphrase and recovery options before relying on encrypted uploads. The reference passphrase KDF is PBKDF2-HMAC-SHA256 (310,000 iterations, random salt); keep recovery material outside the phone. Telegram login cannot recover a lost content key. This derivative has not received an independent cryptographic audit.

## Local access and permissions

App lock uses device authentication and a configurable timeout. Screen-capture blocking is available. A UI lock is not a promise to stop uploads: the reference background workers can continue with device-held keys. Biometric verification itself is implemented by Android.

The reference supports broad file access for folder scans, media permissions for photos/videos, notifications, and optional background backup. Permission and backup settings remain visible and configurable. A selected SAF document can be staged privately when a durable readable path is unavailable. Staging selections with identical names/sizes are kept separate; downloads normalize names and reject parent-directory traversal.

Imports, new TDLib storage, thumbnails and generated previews use the `drive-v2` namespace. Explicit downloads go to the configured destination, defaulting to `Downloads/TG Drive`. Shares/exports create readable copies outside the app; deleting a remote original cannot recall exported copies. App lock and cache clearing cannot protect copies another application already holds.

## Upgrades and previous libraries

The fresh distribution uses `app.tgdrive.next` and a new signing identity, as authorized after the old signing-setup failure. It installs alongside `app.tgdrive`; it never uninstalls or clears the original app. Android isolates their private data. The compatibility session-copy/reader code only operates inside the current package’s own namespace, so it cannot read the other app’s session or index.

Old queue/backup work is cancelled by its exact old work name. Old worker/service classes are inert compatibility tombstones; new launches never construct an old session pool or drain its queue. Unfinished old uploads must be selected again from their original sources. New uploads never use Saved Messages.

Settings → Account → Previous library reads the signed-in account's previous index without writing its database or Telegram messages. It can unlock old vaults and save copies after checking stored-part hashes, plaintext sizes and the full hash when recorded. Early v1 imports with no recorded original hash can only be checked against known sizes and Telegram's delivered bytes. Remote bytes, original indexes and old keys remain untouched. Old files are not silently re-uploaded or converted to a new encryption format.

## Distribution

The TDLib dependency remains the community prebuilt `io.github.tdlib-android:core:0.1.1`, preserving the installed native API/session compatibility. Native provenance and dependency licensing still require review before wider production distribution; alignment and compilation are not supply-chain audits. See [third-party notices](../THIRD_PARTY_NOTICES.md).

Next release builds bootstrap a new signer once, cache it under a separate key, and record its public fingerprint in `scripts/next-cert-sha256.txt`. Later builds require that same fingerprint. A lost initialized key must be restored through `NEXT_RELEASE_*` secrets; the build refuses to rotate it silently. Cache is not a durable signing-key backup and is not suitable as the only production key store. Configure independently backed-up signing secrets for durable distribution. The old signer/cache/pin are left untouched. Application API credentials are supplied through repository Secrets for releases; debug tests exclude them. No real user account is used in automated authentication tests.

Do not attach sessions, API hashes, login codes, recovery keys, documents or signing keys to public issues. Keep another copy of important files. Validation status is recorded in [TESTING.md](TESTING.md).

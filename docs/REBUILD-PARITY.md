# TG Drive rebuild: DriveInfinity foundation

Baseline: ZHINFINITY/DriveInfinity `8a17d352802b8229038ce78363e670ace45c0c55` (Apache-2.0). Android is the target; previous instructions excluded desktop. The fresh distribution is `app.tgdrive.next` (TG Drive Next), with a new pinned certificate and the retained `app.tgdrive.MainActivity` class. It installs beside the original application. Android prevents the fresh package from accessing the old package’s private session/index; old data stays in the original app.

The reference `shared` engine and `ui` presentation modules are integrated rather than imitating their README. TG Drive changes are isolated in the platform integration and `skin` design module. The source attribution and license are under `third_party/driveinfinity`.

| Reference capability | Rebuild path |
| --- | --- |
| Phone / code / password / email / QR authentication | Shared onboarding + native Telegram adapter |
| Owned private storage channels; discover / create / switch / rename / delete | Channel repository + Channels screen |
| Files / folders / import / favorites / hidden / archive / bulk actions | Reference files, collection, search and selection flows |
| Notes and Markdown | Note editor + file/transfer repositories |
| Photos / videos / albums | Gallery and native preview |
| Audio / embedded artwork | Dedicated TG Drive Audio tab + reference media engine |
| PDF / text / ZIP previews | Platform preview + common content resolver |
| Seekable audio / video, encrypted multi-part streaming, tracks and subtitles | Reference Media3 and byte sources |
| Persistent parallel upload / download queue, pause / retry / resume / cancel | Reference executor, drainer, workers and outbox |
| Automatic / instant / scheduled backup, Wi-Fi / charging rules, size limits | Reference backup engine and Android permission/watcher/scheduler glue |
| Exclusion patterns and selected folders | Exclusions screen and backup repositories |
| Optional encryption, key backup / passphrase / recovery hint | Reference crypto and key recovery flows |
| Cache budget, local cleanup, trash / expiry / restore | Reference cache, trash and maintenance |
| App lock, screenshot blocking, lock timeout | Platform biometrics and app lock manager |
| Theme / density / layout / grid / sorting / language options | Reference preferences with TG Drive dark/English defaults and design tokens |
| Playback preferences and language / subtitle choices | Reference playback settings |
| Backup and failure notifications | Reference notification channels and workers |
| SOCKS5 / HTTP / MTProto proxies, testing and failover | Proxy repository, TDLib adapter and Android failover |
| Diagnostics, concurrency/retry settings, optional update check | Reference Advanced/About sections; updates target TG Drive releases |
| Existing TG Drive Saved Messages files and vaults | Separate compatibility reader; no automatic removal or reinterpretation as the new format |

Upgrade gates: preserve installed sessions/keys, never overwrite an existing new session, retire only old worker names, enforce owned/private channel checks, keep legacy and new storage paths separate, preserve launcher/Solar design, verify builds/unit/device tests and signed in-place upgrade. Validation results and any remaining limitations will be recorded before publication. This inventory is not itself proof that device or live Telegram checks have passed.

TG Drive UI adaptation: Home/Gallery/Audio/Settings remain the four primary destinations; All files and Activity are Home shortcuts. Search and sorting remain scoped to the visible collection, including audio preview sequences. The approved dashboard is shared through `skin`; Settings uses the same wide capsule switch and serial preference commits. The reference supports one active Telegram account with several storage channels, rather than simultaneous 0.x session profiles.

Transfer differences from upstream: current TDLib document/proxy/search schemas; injected native transport for tests; immediate-final send/copy replies; generation isolation; account-wide pacing applied before each retry; safe missing session keys; unique import staging; download path confinement and collision-safe creation; private storage namespaces; bounded JPEG upload previews. Existing content keys/session files are never automatically discarded on decrypt failure. The reference's conservative message-write budget remains in effect. See [the Arabic implementation report](REBUILD-1.0.ar.md).

License and attribution copies ship in APK assets under `licenses/` as well as the source distribution. Locally modified upstream source files carry derivative notices; no endorsement is implied.

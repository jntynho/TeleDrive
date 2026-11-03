# APK size policy

Release 34's published ARM64 APK was 42,030,605 bytes (40.08 MiB). Its largest payloads were TDLib (21.68 MiB), retired bundled OCR/native models (11.77 MiB), and bundled SQLite (1.22 MiB).

The current build removes the retired OCR implementation and its ML Kit dependency. Its queue enum and saved text fields remain compatible with previous metadata. PDF/image helpers, previous-library reading, vault decryption, and Tink remain. The active application's private-channel engine had no OCR entry point.

Android Room continues using the framework SQLite path and all existing migrations. The framework dependency is pinned to the same 2.7.1 line as before; the unused bundled native driver is no longer shipped. The application ID, signing configuration, database schema, TDLib version and JNI keep rules are unchanged.

Native libraries are compressed in APKs with `jniLibs.useLegacyPackaging = true`. Android extracts them during installation. This reduces download size; installed storage also contains extracted libraries, so it must not be equated to the APK size. Do not recompress or strip a signed APK manually.

`python3 scripts/check_apk_size.py` checks the actual three release outputs. It requires exactly one ABI per APK, retains TDLib, rejects bundled OCR/models and SQLite, checks compressed native entries and enforces a 20 MiB APK budget. GitHub Actions publishes the measured sizes in its summary and verifies signing/alignment and emulator installation before release publication.

Compare release APKs for the same ABI. Debug APKs include additional unoptimized code/tooling and are not a valid distribution-size baseline. Documents, media caches, user databases, saved sessions and vault keys are not size-cleanup targets.

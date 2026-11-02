> Release output scope is now three standalone APKs: ARM64, ARM32 and x86_64. Five-APK/AAB results below record checks completed before that reduction; those extra outputs are no longer produced by CI.

# Validation — Android private-channel rebuild

The active engine is the pinned DriveInfinity derivative described in [REBUILD-PARITY.md](REBUILD-PARITY.md). Earlier `UPLOAD-0.x`, `SOLAR-0.11` and UI-review documents are historical.

## Local checks

```sh
./gradlew --max-workers=1 :shared:allTests :app:testDebugUnitTest
./gradlew --max-workers=1 :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --max-workers=1 :app:lintRelease :app:assembleRelease -PsplitApks=true
./gradlew --max-workers=1 :app:bundleRelease -PbundleOnly=true
python3 scripts/check_native_alignment.py app/build/outputs/apk/release/*.apk
python3 scripts/collect_release.py --verify-only
python3 scripts/generate_launcher.py --check
```

AGP 9.4 exposes the debug unit-test variant; the workflow runs that suite alongside release lint, R8 and packaging. The KSP host-test/lint dependency is declared explicitly for Gradle 9.7.

The current suite includes **178 app tests and 66 shared host tests**, with no Telegram account. Coverage includes the imported crypto/model/path/query tests and dependency registration; fake-wire direct-send completion, early native success updates, cancellation, server-copy completion, stale clients, native loading failure, burst pacing and shared 429 waits; safe session copying and missing/corrupt key preservation; old multipart verification, corruption rejection and old-vault decryption. Legacy regression tests are retained to protect old-format reading, but do not represent the new active uploader.

`RebuiltUiTest` uses the real modules, SQLite database, view models and navigation with synthetic local rows. Only device key wrapping is replaced for the host environment. It exercises Home/Gallery/Audio/Settings, gallery sorting, audio-only filtering, the previous-library entry, and returning to the real welcome/API form. This is an integration check, not a frame-rate measurement or a live Telegram test.

## Device and distribution gates

`assembleDebugAndroidTest` compiles the native device suite. `connectedDebugAndroidTest` must run on a phone or accelerated emulator. Tests cover actual application startup/Keystore wiring, native TDLib loading, the rebuilt navigation, Room DAO behavior and retained compatibility component/media tests. A compiled test APK does not mean the device tests were executed.

The release workflow requires device tests, local suites, release lint/R8, ABI/native payload consistency, the existing signing certificate, ZIP/ELF page alignment, and a signed in-place upgrade before publication. The upgrade gate seeds synthetic app-owned data on a disposable emulator; it does not authenticate a real session. Only signed releases with the original certificate can update installed release builds normally.

No KVM device is attached in this workspace. No real Telegram account or release signing key was supplied. Live authentication, private-channel creation, large uploads through a disconnect, backup under Android process eviction, encrypted streaming and recovery on a second physical phone remain manual acceptance cases. Telegram's dynamic rate limits cannot be disproved by fake-wire timing tests. See [SECURITY.md](SECURITY.md) before relying on encrypted storage.

Final local packaging results are appended after the build completes. GitHub device/signature/publication results must be reported from the actual workflow run, separately from local results.

## Local results, 2026-11-02

- App unit/integration suite: **178 passed**; shared Android host suite: **66 passed**.
- Release lint: **0 errors**, 72 non-blocking warnings. Debug and instrumented-test APK compilation succeeded; device execution is a separate CI gate.
- R8/resource-shrunk standalone APKs built for all four ABIs plus universal. Identical DEX/resources/assets and per-ABI native bytes verified across all five outputs.
- All **40 native library entries** passed ELF alignment; every APK passed ZIP alignment for 16 KB packaging. The Aero Fold launcher check passed.
- Unsigned local APK sizes: ARM64 **40.03 MiB**, ARM32 **29.84 MiB**, x86_64 **42.88 MiB**, x86 **44.60 MiB**, universal **137.81 MiB**. Exact signed sizes are determined by the release workflow.
- License texts/attribution are packaged in APK assets. The removed upstream launcher bitmap is absent; welcome uses the retained TG Drive vector.

The separate resource-shrunk **AAB build also passed** (about 68 MiB). GitHub device/signature/publication results are recorded separately when available. These local APKs are unsigned; they are not substitutes for the existing signed release identity.

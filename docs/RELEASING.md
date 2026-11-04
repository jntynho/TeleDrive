# Building the two TeleDrive ARM APKs

The workflow publishes only `tg-drive-arm64-v8a.apk` and `tg-drive-armeabi-v7a.apk`. No Universal, x86, x86_64 or AAB release output is built. Internal test reports remain separate Actions artifacts.

## Independent installation

The original signing-setup step failed before Gradle ran. The authorized fresh distribution uses application ID **app.tgdrive.next**, launcher label **TeleDrive**, and a newly initialized signer. It installs beside the original `app.tgdrive`; do not uninstall the original to install Next. Sign in again in Next. The new package cannot access the original app's private session/index.

## Signing

`scripts/configure_next_signing.sh` restores optional `NEXT_RELEASE_KEYSTORE_BASE64`, `NEXT_RELEASE_STORE_PASSWORD`, `NEXT_RELEASE_KEY_ALIAS`, and `NEXT_RELEASE_KEY_PASSWORD` secrets, or uses the separate `tgdrive-next-signing-key-v1` cache. On first initialization only, it generates a new key. The workflow saves it before building and commits its **public certificate fingerprint only** to `scripts/next-cert-sha256.txt`. Private keys are never committed or included in release assets.

Future builds verify that fingerprint. If an initialized key is unavailable, the workflow refuses to generate a different signer for the same Next application. Cache can expire: use an independently backed-up key through the Next secrets for durable distribution. Legacy signing secrets/cache/fingerprint remain untouched.

## Checks and publication

Device tests and the build run in parallel. The build runs shared host tests, app unit tests, release lint and R8, then verifies signatures, alignment, the two ARM ABI filters and matching code/resources/assets. A separate `package-check` gate downloads both signed ARM APKs and verifies their public certificate, package ID, launch activity, minimum Android version, ABI and matching app version.

Native UI smoke tests still install a debug test application on an x86_64 emulator; this is an internal test build, not an x86_64 release output. Because ARM-only release APKs cannot be assumed to run on that emulator, the former signed-x86 installation gate is replaced by the ARM manifest/signature gate. Installation and live Telegram login with the signed ARM binaries remain physical-device acceptance checks. Publication requires all gates to pass and uploads exactly two APKs to Releases.

Main pushes create immutable `v<version>-build.<run-number>` releases. Manual/tag builds retain their normal version tags. GitHub's automatic source ZIP/TAR files contain source, not the app. No Google Play publishing is configured.

## Local build

Use JDK 21, the checked-in Gradle 9.7 wrapper, Android SDK `platforms;android-37.0`, and Build Tools `37.0.0`. CI also installs `36.0.0` for its signature/package scripts.

```sh
./gradlew --max-workers=1 :shared:allTests :app:testDebugUnitTest
./gradlew --max-workers=1 :app:lintRelease :app:assembleRelease -PsplitApks=true
python3 scripts/collect_release.py --verify-only
python3 scripts/check_native_alignment.py app/build/outputs/apk/release/*.apk
```

Local release APKs are unsigned unless all four Gradle `RELEASE_*` signing values are supplied. Debug builds have a separate debug signature. Compiled tests do not prove live Telegram authentication or transfer performance.

## Application API identity

Configure `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` as repository Actions Secrets before a release build. The workflow validates format without logging values. These identify TeleDrive, not a user account. Debug and device tests use blank build-time values. See [setup and session compatibility](TELEGRAM_APP_CREDENTIALS.md).

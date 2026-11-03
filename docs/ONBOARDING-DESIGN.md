# TeleDrive sign-in presentation

The welcome and authentication UI is independently implemented in Compose, with Nekogram v13.1.1's native Android onboarding as a visual reference:

- https://github.com/Nekogram/Nekogram/blob/v13.1.1/TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java
- https://github.com/Nekogram/Nekogram/blob/v13.1.1/TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java

No Nekogram source, assets, client engine or dependencies were imported.

The welcome pager presents three short introductions with the packaged TeleDrive icon, page dots and a pill action. Sign-in uses centered headings, thin outlined fields, a circular phone continuation action and a light/dark toggle backed by the appearance preference. API setup, email, password and QR sign-in retain the existing authentication implementation. Country selection, manual-number fallback, proxy setup and error messages remain available.

Numeric codes use one editable field with decorative digit slots, maintaining native paste and accessibility. Slot count follows Telegram metadata. Word/phrase codes use a text field. Verification remains an explicit action, and resend errors follow the real Telegram response; no fictional countdown is displayed. The system keyboard is used. Phone drafts restore between steps, while secret fields remain outside saved UI state. Back from verification restarts the pending authentication flow before presenting the phone form.

Validation for this change: Kotlin syntax parsing and git diff checks only. Gradle, APK generation and device tests were deliberately not run at the user's request. The push commit uses `[skip ci]` to suppress the automatic release workflow. Compilation, UI/device checks and release generation remain pending explicit authorization to build.

## Build validation update

The earlier build deferral is superseded by the user-authorized release [v1.0.0-build.44](https://github.com/jntynho/TeleDrive/releases/tag/v1.0.0-build.44). [Workflow 44](https://github.com/jntynho/TeleDrive/actions/runs/41704922759) passed configuration validation, tests/lint/release packaging, native device tests, signing/installation/phone-page checks and publication. Local validation passed 241 app tests plus 66 shared tests (307 total). All three standalone APK assets were verified on the public release page. Live Telegram account login and device-specific frame-rate measurement were not exercised. This documentation update does not modify the released application.

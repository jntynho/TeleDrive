# Telegram application identity

Create a Telegram application at https://my.telegram.org/apps for TeleDrive. In the repository's Settings → Secrets and variables → Actions, add repository secrets named `TELEGRAM_API_ID` (positive numeric ID) and `TELEGRAM_API_HASH` (32 hexadecimal characters). Do not commit their values.

The release workflow injects these into Gradle's environment and checks their format without printing them. A new build is required after changing a secret; changing a secret does not modify an already downloaded APK. Release credentials are excluded from debug builds and automated unit/device tests.

The release client supplies the application identity to TDLib, so users enter their phone number and Telegram's requested verification steps. Existing encrypted, user-supplied credentials take priority for session compatibility. An unreadable existing credential is an error, never permission to overwrite a session. Development builds without bundled credentials retain the manual setup page.

GitHub Secrets protect the repository and workflow configuration. Values embedded in a distributed APK cannot be made impossible to extract; obfuscation is not a secrecy boundary. Application credentials alone do not authenticate a user's Telegram account. Account, operation and application restrictions imposed by Telegram still apply.

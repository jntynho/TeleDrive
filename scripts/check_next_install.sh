#!/usr/bin/env bash
# Runs on the disposable CI emulator. Leaves any existing TG Drive installation alone.
set -euo pipefail
app_id=app.tgdrive.next
tools="$ANDROID_HOME/build-tools/36.0.0"
: "${EXPECTED_CERT_SHA256:?Missing build signer fingerprint}"
for apk in upgrade/new/*.apk; do
  actual="$("$tools/apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
  [[ "$actual" == "$EXPECTED_CERT_SHA256" ]]
  adb install -r "$apk"
done
adb logcat -c
adb shell pm grant "$app_id" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell am start -W -n "$app_id/app.tgdrive.MainActivity"
sleep 3
app_pid="$(adb shell pidof "$app_id" | tr -d '\r')"
[[ "$app_pid" =~ ^[0-9]+$ ]]
if adb logcat --pid="$app_pid" -d -s AndroidRuntime | grep -q 'FATAL EXCEPTION'; then
  adb logcat --pid="$app_pid" -d -s AndroidRuntime
  exit 1
fi
python3 scripts/check_phone_onboarding.py
printf '## Signed installation\n\nTG Drive Next installed and launched as app.tgdrive.next. It does not replace or clear app.tgdrive. Live Telegram authentication was not exercised.\n' >> "$GITHUB_STEP_SUMMARY"

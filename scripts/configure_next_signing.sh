#!/usr/bin/env bash
# A new install identity; never replace the signer of an already initialized identity.
set -euo pipefail
signing_dir="${NEXT_SIGNING_DIR:-$HOME/.tgdrive-signing-next}"
pin_file="${NEXT_SIGNING_PIN:-scripts/next-cert-sha256.txt}"
mkdir -p "$signing_dir"
keyfile="$signing_dir/release.jks"
storepass="${KS_PASS:-tgdrive-next-sideload-key}"
keypass="$storepass"
keyalias=tgdrive-next
if [[ -n "${NEXT_RELEASE_KEYSTORE_BASE64:-}" ]]; then
  : "${NEXT_RELEASE_STORE_PASSWORD:?Missing new store password}"
  : "${NEXT_RELEASE_KEY_ALIAS:?Missing new key alias}"
  : "${NEXT_RELEASE_KEY_PASSWORD:?Missing new key password}"
  keyfile="${RUNNER_TEMP:?}/next-restored-signing.jks"
  printf '%s' "$NEXT_RELEASE_KEYSTORE_BASE64" | base64 --decode > "$keyfile"
  chmod 600 "$keyfile"
  storepass="$NEXT_RELEASE_STORE_PASSWORD"
  keyalias="$NEXT_RELEASE_KEY_ALIAS"
  keypass="$NEXT_RELEASE_KEY_PASSWORD"
elif [[ ! -s "$keyfile" ]]; then
  [[ ! -f "$pin_file" ]] || { echo 'The initialized Next signer is missing. Restore NEXT_RELEASE_KEYSTORE_BASE64 and its matching password/alias secrets; refusing to rotate it.'; exit 1; }
  keytool -genkeypair -noprompt -keystore "$keyfile" -storetype JKS \
    -storepass "$storepass" -keypass "$keypass" -alias "$keyalias" \
    -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=TG Drive Next'
  chmod 600 "$keyfile"
fi
certfile="$(mktemp "${RUNNER_TEMP:-/tmp}/tgdrive-next-cert.XXXXXX")"
trap 'rm -f "$certfile"' EXIT
keytool -exportcert -keystore "$keyfile" -storepass "$storepass" -alias "$keyalias" -file "$certfile" >/dev/null
actual="$(sha256sum "$certfile" | cut -d ' ' -f1)"
new_identity=false
if [[ -f "$pin_file" ]]; then
  expected="$(tr -d '\r\n ' < "$pin_file")"
  [[ "$actual" == "$expected" ]] || { echo 'Next signing certificate mismatch; refusing an incompatible update.'; exit 1; }
else
  mkdir -p "$(dirname "$pin_file")"
  printf '%s\n' "$actual" > "$pin_file"
  new_identity=true
fi
{
  echo "RELEASE_STORE_FILE=$keyfile"
  echo "RELEASE_STORE_PASSWORD=$storepass"
  echo "RELEASE_KEY_ALIAS=$keyalias"
  echo "RELEASE_KEY_PASSWORD=$keypass"
} >> "${GITHUB_ENV:?}"
{
  echo "sha256=$actual"
  echo "new_identity=$new_identity"
} >> "${GITHUB_OUTPUT:?}"
echo "Next signing identity verified: $actual"

#!/usr/bin/env bash
# Static checks for signed ARM release APKs. Native device tests run separately on debug builds.
set -euo pipefail
: "${EXPECTED_CERT_SHA256:?Missing build signer fingerprint}"
tools="$ANDROID_HOME/build-tools/36.0.0"
manifest_dir="${RUNNER_TEMP:-/tmp}/teledrive-arm-manifests"
mkdir -p "$manifest_dir"
mapfile -t packages < <(find release-check -type f -name '*.apk' | sort)
[[ ${#packages[@]} -eq 2 ]] || { echo 'Expected exactly two ARM release APKs.'; exit 1; }
for apk in "${packages[@]}"; do
  actual="$("$tools/apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
  [[ "$actual" == "$EXPECTED_CERT_SHA256" ]] || { echo 'Release signing certificate differs from the pinned identity.'; exit 1; }
  "$tools/aapt" dump badging "$apk" > "$manifest_dir/$(basename "$apk").txt"
done
python3 - "$manifest_dir" <<'PY'
import pathlib, re, sys
expected = {'arm64-v8a', 'armeabi-v7a'}
seen, versions = set(), set()
for path in pathlib.Path(sys.argv[1]).glob('*.apk.txt'):
    text = path.read_text()
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", text, re.M)
    assert package and package[1] == 'app.tgdrive.next', 'Incorrect application identity'
    assert int(package[2]) > 11, 'Invalid release version code'
    versions.add((package[2], package[3]))
    native = re.search(r'^native-code: (.+)$', text, re.M)
    abis = set(re.findall(r"'([^']+)'", native[1])) if native else set()
    assert len(abis) == 1 and abis <= expected, f'Unexpected release ABI: {abis}'
    abi = next(iter(abis))
    assert abi not in seen, f'Duplicate ABI: {abi}'
    assert path.name.endswith(f'-{abi}.apk.txt'), f'Wrong filename for {abi}'
    seen.add(abi)
    sdk = re.search(r"^sdkVersion:'(\d+)'", text, re.M)
    assert sdk and sdk[1] == '26', 'Android 8.0 minimum SDK changed unexpectedly'
    launcher = re.search(r"^launchable-activity: name='([^']+)'", text, re.M)
    assert launcher and launcher[1] == 'app.tgdrive.MainActivity', 'Missing launcher activity'
assert seen == expected and len(versions) == 1, 'Incomplete or inconsistent ARM release'
print('Both signed ARM APKs have the pinned certificate, expected package/launcher, Android 8 minimum, correct ABI and matching version.')
PY
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  printf '## Signed ARM package validation\n\nBoth ARM APK manifests and signatures passed. Native debug UI tests run separately; signed ARM installation requires an ARM device.\n' >> "$GITHUB_STEP_SUMMARY"
fi

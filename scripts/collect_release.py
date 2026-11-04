#!/usr/bin/env python3
"""Validate standalone APK variants and collect separate downloads without changing their bytes."""
import hashlib
import json
import pathlib
import shutil
import sys
import zipfile

root = pathlib.Path('app/build/outputs/apk/release')
metadata = json.loads((root / 'output-metadata.json').read_text())
expected = {'arm64-v8a', 'armeabi-v7a'}
variants = {}
versions = set()
for entry in metadata['elements']:
    filters = entry['filters']
    abi = next((f['value'] for f in filters if f['filterType'] == 'ABI'), 'universal')
    assert not any(f['filterType'] != 'ABI' for f in filters), filters
    assert abi not in variants, f'Duplicate output: {abi}'
    path = (root / entry['outputFile']).resolve()
    assert path.parent == root.resolve() and path.is_file(), path
    variants[abi] = path
    versions.add((entry['versionCode'], entry['versionName']))
assert set(variants) == expected, variants
assert metadata['applicationId'] == 'app.tgdrive.next' and len(versions) == 1
code, version = next(iter(versions))
assert code > 11 and all(c.isalnum() or c in '.-' for c in version), (code, version)

def digest(data): return hashlib.sha256(data).hexdigest()
def payload(archive):
    return {n: digest(archive.read(n)) for n in archive.namelist()
            if n.endswith('.dex') or n == 'resources.arsc' or n.startswith(('res/', 'assets/'))}
with zipfile.ZipFile(variants['arm64-v8a']) as reference:
    common = payload(reference)
    assert any(n.endswith('.dex') for n in common)
for abi in sorted(expected):
    with zipfile.ZipFile(variants[abi]) as apk:
        assert payload(apk) == common, f'Code/resources differ for {abi}'
        libraries = [n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')]
        assert libraries and {n.split('/')[1] for n in libraries} == {abi}, abi
        assert any(n.endswith('/libtdjni.so') for n in libraries), abi
        assert not any('mlkit' in n.lower() or n.endswith('/libsqliteJni.so') for n in libraries), abi

print('Matching code/resources/assets and correct native ABI verified for both ARM APKs.')
assert sys.argv[1:] in ([], ['--verify-only']), 'Supported option: --verify-only'
if sys.argv[1:]:
    raise SystemExit(0)

dest = pathlib.Path('dist')
assert not dest.exists(), 'Remove or move previous dist before collecting a new build.'
checksums = []
for abi, source in sorted(variants.items()):
    folder = dest / f'tg-drive-{abi}'
    folder.mkdir(parents=True)
    target = folder / f'tg-drive-{version}-{abi}.apk'
    shutil.copyfile(source, target)
    line = f'{digest(target.read_bytes())}  {target.name}\n'
    (folder / 'SHA256SUMS').write_text(line)
    checksums.append(line)
    print(f'{abi}: {target.stat().st_size / 1024**2:.1f} MiB')
(dest / 'SHA256SUMS').write_text(''.join(checksums))
print(f'Shared code/resources/assets and two standalone ARM APKs verified; package app.tgdrive.next, version {version} ({code}).')

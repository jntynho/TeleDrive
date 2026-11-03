#!/usr/bin/env python3
"""Validate distribution APKs, including libraries that resource shrinking cannot remove."""
import os
from pathlib import Path
import sys
import zipfile

EXPECTED = {"arm64-v8a", "armeabi-v7a", "x86_64"}
BUDGET_BYTES = 20 * 1024 * 1024


def main(paths):
    seen = set()
    rows = []
    for path in paths:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            native = [entry for entry in archive.infolist() if entry.filename.startswith("lib/") and entry.filename.endswith(".so")]
            abis = {entry.filename.split("/")[1] for entry in native}
            if len(abis) != 1 or not abis <= EXPECTED:
                raise SystemExit(f"Unexpected architectures in {path.name}: {abis}")
            abi = next(iter(abis))
            if abi in seen:
                raise SystemExit(f"Duplicate architecture: {abi}")
            seen.add(abi)
            if f"lib/{abi}/libtdjni.so" not in names:
                raise SystemExit(f"Telegram native library is missing: {path.name}")
            forbidden = [name for name in names if "mlkit" in name.lower() or "ocr-models" in name.lower() or name.endswith("libsqliteJni.so")]
            if forbidden:
                raise SystemExit(f"Retired bundled libraries reappeared: {forbidden}")
            if any(entry.compress_type != zipfile.ZIP_DEFLATED for entry in native):
                raise SystemExit(f"Native libraries must use install-time extraction: {path.name}")
            size = path.stat().st_size
            if size > BUDGET_BYTES:
                raise SystemExit(f"APK exceeds the 20 MiB budget: {path.name} ({size} bytes)")
            rows.append(f"| {abi} | {size / 1048576:.2f} MiB |")
    if seen != EXPECTED:
        raise SystemExit(f"Missing architectures: {EXPECTED - seen}")
    report = "## APK size validation\n\n| Architecture | APK size |\n|---|---:|\n" + "\n".join(rows) + "\n\nAll three APKs retain TDLib, use compressed native libraries and exclude retired OCR/models and bundled SQLite.\n"
    print(report)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write(report)


if __name__ == "__main__":
    main([Path(value) for value in sys.argv[1:]] or sorted(Path("app/build/outputs/apk/release").glob("*.apk")))

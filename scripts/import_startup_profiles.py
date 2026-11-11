#!/usr/bin/env python3
"""Import actual device-generated HRF profiles; never synthesize rules from source names."""
import argparse
from pathlib import Path
import shutil

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--from-dir',type=Path,required=True)
args=parser.parse_args()
candidates=sorted(args.from_dir.rglob('*baseline-prof.txt'),key=lambda p:p.stat().st_mtime,reverse=True)
if not candidates:raise SystemExit('No device-generated baseline profile found. Run StartupProfileGenerator first.')
baseline=candidates[0]
startup=baseline.with_name(baseline.name.removesuffix('baseline-prof.txt')+'startup-prof.txt')
if not startup.is_file():raise SystemExit('Matching device-generated startup profile is missing.')
for source in (baseline,startup):
    if not 0<source.stat().st_size<8_000_000:raise SystemExit(f'Invalid profile size: {source.name}')
    text=source.read_text()
    if not any(x in text for x in ('Lcom/infinity/drive/','Lapp/tgdrive/')):raise SystemExit('Profile contains no TeleDrive code.')
    for line in text.splitlines():
        if line.strip() and not line.startswith('#') and ';' not in line:raise SystemExit('Input is not a human-readable ART profile.')
root=Path(__file__).resolve().parents[1]/'app/src/main'
shutil.copyfile(baseline,root/'baseline-prof.txt')
shutil.copyfile(startup,root/'startup-prof.txt')
print('Imported matching, device-generated baseline/startup profiles. Review them before committing.')

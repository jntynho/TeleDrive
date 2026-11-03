#!/usr/bin/env python3
"""Disposable CI emulator: verify bundled-identity UI without entering a phone or sending a code."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def hierarchy():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/teledrive-onboarding.xml"],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
    data = subprocess.check_output(["adb", "exec-out", "cat", "/sdcard/teledrive-onboarding.xml"], timeout=10)
    return list(ET.fromstring(data).iter("node"))


deadline = time.monotonic() + 60
clicked = False
while time.monotonic() < deadline:
    try:
        nodes = hierarchy()
    except (subprocess.SubprocessError, ET.ParseError):
        time.sleep(1)
        continue
    texts = [node.get("text", "") for node in nodes]
    if any("API access" in text or "API hash" in text for text in texts):
        raise SystemExit("Release unexpectedly showed manual API setup; check application-identity integration.")
    if any("Phone number" in text for text in texts):
        print("Signed release opens phone sign-in without requesting API credentials. No phone or verification code submitted.")
        break
    if not clicked:
        button = next((node for node in nodes if node.get("text") == "Get started"), None)
        if button is not None:
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", button.get("bounds", ""))
            if bounds:
                x1, y1, x2, y2 = map(int, bounds.groups())
                subprocess.run(["adb", "shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2)], check=True)
                clicked = True
    time.sleep(1)
else:
    raise SystemExit("Phone sign-in did not become visible within the onboarding check timeout.")

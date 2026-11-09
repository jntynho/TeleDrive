# only_player liquid floating navigation

Source: https://github.com/Kindness-Kismet/only_player
Pinned commit: d2b5dd9 (the full commit is recorded below).

Imported FloatingBottomBar, liquid helpers, DampedDragAnimation, InteractiveHighlight and DragGestureInspector into app/src/main/java/com/infinity/drive/presentation/navigation/liquid.

The repository is GPL-3.0. Individual files retain their Apache-2.0 adaptation notices for compose-miuix-ui / Kyant0 AndroidLiquidGlass. GPL-covered imported code is distributed with corresponding source in this repository and the GPL text alongside this notice. Existing Apache-2.0 notices remain applicable to their original portions.

Imported and adapted for TeleDrive on 2026-11-09.

Changes: package names, MaterialTheme color bridge, explicit selected-tab semantics at the adapter, explicit Android API guard at shader drawing, and selection callback synchronization to handle external navigation changes. Lens shaders, drag physics, highlights, dimensions and refraction constants are retained.

Dependency: top.yukonga.miuix.kmp:miuix-blur-android:0.9.4 (Apache-2.0).
d2b5dd9bcf8978bc0e7524363a902a81da4bcb02

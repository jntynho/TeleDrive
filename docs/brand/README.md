# TeleDrive adaptive app icon

The approved design combines a pearl/ice-blue cloud, a blue folder and a white paper plane. The neutral graphite gradient background and foreground emblem are packaged independently, allowing the launcher to choose its own circle, squircle or rounded-square mask.

- `tg-drive-icon.png`: browser-rendered full-color presentation of the current packaged layers, including the neutral rounded tile. It is used in the repository README, **not** as an adaptive foreground.
- `tg-drive-mark.webp`: transparent high-resolution foreground, extracted using the image tool and encoded as lossless WebP. It contains the cloud, folder and plane, without the colored tile. The shaded artwork is raster artwork, not an editable SVG.
- `tg-drive-background.svg`: editable full-bleed gray gradient without baked-in corner rounding.
- `tg-drive-monochrome.svg`: editable vector for system-themed icons. A gap separates the cloud and folder; the plane is negative space so it remains visible after system tinting.
- `launcher-preview.png`: browser-rendered mask/layout previews using the packaged Android layer geometry; themed colors are examples. Android resource compilation is checked separately.

## Android behavior

The adaptive foreground and background occupy a 108dp layer artboard. Android normally reveals the central 72dp through its own mask. The colored emblem's opaque width is 52dp, with its opaque extent inside a 30dp radius, leaving margin within the 33dp safe radius. Fractional insets keep this proportion when icons are enlarged, including About. The foreground is stored once in `drawable-nodpi`; no unnecessary per-density copies are packaged.

Android 8+ receives independent full-bleed background/foreground resources. Android 13+ additionally receives a dedicated monochrome vector, allowing a supporting launcher to apply wallpaper/theme colors. The foreground's existing cloud/folder/plane shading is preserved in normal color mode. Both manifest `icon` and `roundIcon` point to the adaptive resource. System/launcher masks, motion, themed-icon availability and tint colors vary by launcher; the app supplies the layers rather than forcing a launcher shape or color.

Regenerate and check the packaged resources offline:

```sh
python3 scripts/generate_launcher.py
python3 scripts/generate_launcher.py --check
```

The generator uses only Python's standard library and copies the lossless foreground without resampling. AAPT validates the fractional inset bitmap, vector gradients, adaptive resources and monochrome vector:

```sh
./gradlew :app:processDebugResources
```

Solar interface glyphs and the separate notification icon are unaffected.

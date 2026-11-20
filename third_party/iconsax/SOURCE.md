# Iconsax Free

TeleDrive uses the Outline and Broken glyphs from the original Iconsax Free collection, which Iconsax continues to offer under its Proprietary Free License. The developer terms in section 1.B permit integrating these glyphs in source code and redistributing that code with a notice. [Icon browser](https://app.iconsax.io/) · [Developer license](https://docs.iconsax.io/license-and-terms/license) · [Usage Manifesto](https://docs.iconsax.io/license-and-terms/usage-manifesto) · [Notice](NOTICE.txt)

The path geometry is from the official `lusaxweb/iconsax` repository at `e5dba15361f69d34d0f3f3f35408a28fe74c5727`. TeleDrive selects the two 24 × 24 styles under `docs/Iconsax/Svg/All`, preserving path data, fill, stroke width, cap and join. Compose uses a tintable black brush and the website uses `currentColor`. [Pinned upstream source](https://github.com/lusaxweb/iconsax/tree/e5dba15361f69d34d0f3f3f35408a28fe74c5727/docs/Iconsax/Svg/All)

`teledrive-icons.json` maps in-app and website controls to the geometry they render. The website inlines the Outline subset in the pages that use it. Run `python3 scripts/generate_icons.py` to rebuild the Compose source and bundled Android notice, or add `--check` to verify their contents. The conversion runs offline.

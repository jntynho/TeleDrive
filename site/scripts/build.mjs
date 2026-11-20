import { readFile, writeFile, cp, mkdir, rm } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import QRCode from "qrcode";

export const ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const REPOSITORY = "jntynho/TeleDrive";
export const SITE_URL = "https://jntynho.github.io/TeleDrive/";
const expected = ["tg-drive-arm64-v8a.apk", "tg-drive-armeabi-v7a.apk"];

// Icon assets from Iconsax - https://iconsax.io (Proprietary Free License).
function iconSprite(source, icons) {
  const names = new Set(
    Array.from(source.matchAll(/href="#i-([a-z-]+)"/g), ([, name]) => name),
  );
  if (!names.size) return "";
  const symbols = Array.from(names, (name) => {
    const paths = icons.geometry.outline[icons.site[name]];
    if (!paths) throw new Error(`Missing Iconsax website glyph: ${name}`);
    const body = paths
      .map(({ d, strokeWidth, strokeLineCap, strokeLineJoin, strokeMiterLimit }) => {
        if (!strokeWidth) return `<path d="${d}" fill="currentColor"/>`;
        const cap = strokeLineCap ? ` stroke-linecap="${strokeLineCap}"` : "";
        const join = strokeLineJoin ? ` stroke-linejoin="${strokeLineJoin}"` : "";
        const miter = strokeMiterLimit ? ` stroke-miterlimit="${strokeMiterLimit}"` : "";
        return `<path d="${d}" fill="none" stroke="currentColor" stroke-width="${strokeWidth}"${cap}${join}${miter}/>`;
      })
      .join("");
    return `<symbol id="i-${name}" viewBox="0 0 24 24">${body}</symbol>`;
  }).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" class="icon-definitions" aria-hidden="true" width="0" height="0"><defs>${symbols}</defs></svg>`;
}

export function releaseManifest(release) {
  if (
    release.draft ||
    release.prerelease ||
    !/^v[\w.+-]+$/.test(release.tag_name || "")
  )
    throw new Error("A published stable release is required");
  const prefix = `https://github.com/${REPOSITORY}/releases/download/${release.tag_name}/`;
  const assets = expected.map((name) => {
    const matches =
      release.assets?.filter((asset) => asset.name === name) || [];
    if (matches.length !== 1)
      throw new Error(`Missing or duplicate APK: ${name}`);
    const asset = matches[0];
    if (
      asset.browser_download_url !== prefix + name ||
      !Number.isSafeInteger(asset.size) ||
      asset.size <= 0
    )
      throw new Error(`Invalid release asset: ${name}`);
    return {
      name,
      abi: name.includes("arm64") ? "arm64-v8a" : "armeabi-v7a",
      bytes: asset.size,
      url: asset.browser_download_url,
      ...(/^sha256:[a-f\d]{64}$/.test(asset.digest || "")
        ? { sha256: asset.digest.slice(7) }
        : {}),
    };
  });
  return {
    tag: release.tag_name,
    name: release.name || release.tag_name,
    url: `https://github.com/${REPOSITORY}/releases/tag/${release.tag_name}`,
    publishedAt: release.published_at || null,
    assets,
  };
}

export async function build({
  releaseFile = process.env.TELEDRIVE_RELEASE_FILE ||
    path.join(ROOT, "release-snapshot.json"),
  output = path.join(ROOT, "dist"),
} = {}) {
  const release = releaseManifest(
    JSON.parse(await readFile(releaseFile, "utf8")),
  );
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await cp(path.join(ROOT, "public"), output, { recursive: true });
  const repo = path.resolve(ROOT, "..");
  await cp(
    path.join(repo, "docs/brand/tg-drive-icon.svg"),
    path.join(output, "assets/icon.svg"),
  );
  const icons = JSON.parse(
    await readFile(path.join(repo, "third_party/iconsax/teledrive-icons.json"), "utf8"),
  );
  for (const file of ["index.html", "privacy.html", "404.html"]) {
    const source = await readFile(path.join(output, file), "utf8");
    await writeFile(
      path.join(output, file),
      source.replace("<!-- ICONS -->", iconSprite(source, icons)),
    );
  }
  await writeFile(
    path.join(output, "release.json"),
    JSON.stringify(release, null, 2) + "\n",
  );
  const qr = await QRCode.toString(SITE_URL + "#download", {
    type: "svg",
    margin: 2,
    width: 240,
    color: { dark: "#172022", light: "#ffffff" },
    errorCorrectionLevel: "M",
  });
  await writeFile(path.join(output, "assets/phone-qr.svg"), qr);
  await mkdir(path.join(output, "assets/licenses"), { recursive: true });
  await cp(
    path.join(repo, "third_party/iconsax/NOTICE.txt"),
    path.join(output, "assets/licenses/Iconsax.txt"),
  );
  await cp(
    path.join(repo, "third_party/fonts/Roboto-OFL.txt"),
    path.join(output, "assets/licenses/Roboto.txt"),
  );
  await writeFile(path.join(output, ".nojekyll"), "");
  console.log(
    `Website built: ${release.tag}; ${release.assets.length} standalone ARM APKs`,
  );
  return release;
}
if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
)
  await build();

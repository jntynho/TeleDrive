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
  const icons = {
    download: "cloud-download",
    upload: "cloud-upload",
    folder: "folder-open",
    gallery: "gallery",
    audio: "music-notes",
    home: "home-2",
    document: "document-text",
    cloud: "cloud",
    info: "info-circle",
    theme: "palette-round",
    shield: "shield-check",
    phone: "smartphone",
    code: "code",
    check: "check",
    arrow: "alt-arrow-right",
    close: "close",
    qr: "qr-code",
    lock: "lock-keyhole",
    history: "history",
    search: "rounded-magnifier",
    settings: "settings-minimalistic",
    heart: "heart",
  };
  let sprite =
    '<svg xmlns="http://www.w3.org/2000/svg" class="icon-definitions" aria-hidden="true" width="0" height="0"><defs>';
  for (const [name, file] of Object.entries(icons)) {
    const raw = await readFile(
      path.join(repo, `third_party/solar/icons/${file}-linear.svg`),
      "utf8",
    );
    const body = raw.replace(/^.*?<svg[^>]*>/s, "").replace(/<\/svg>\s*$/, "");
    sprite += `<symbol id="i-${name}" viewBox="0 0 24 24">${body}</symbol>`;
  }
  sprite += "</defs></svg>";
  for (const file of ["index.html", "privacy.html", "404.html"]) {
    const source = await readFile(path.join(output, file), "utf8");
    await writeFile(
      path.join(output, file),
      source.replace("<!-- ICONS -->", sprite),
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
    path.join(repo, "third_party/solar/LICENSE"),
    path.join(output, "assets/licenses/Solar.txt"),
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

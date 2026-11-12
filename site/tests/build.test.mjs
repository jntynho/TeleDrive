import test from "node:test";
import assert from "node:assert/strict";
import { readFile, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { ROOT, build, releaseManifest } from "../scripts/build.mjs";
const fixture = JSON.parse(
  await readFile(path.join(ROOT, "release-snapshot.json"), "utf8"),
);

test("publishes precisely the two signed-release asset names, independent of API asset ordering", () => {
  const result = releaseManifest({
    ...fixture,
    assets: [...fixture.assets].reverse(),
  });
  assert.deepEqual(
    result.assets.map((asset) => asset.abi),
    ["arm64-v8a", "armeabi-v7a"],
  );
  assert.equal(result.tag, fixture.tag_name);
  assert.ok(result.assets.every((asset) => /^[a-f\d]{64}$/.test(asset.sha256)));
});
test("rejects incomplete, duplicate and untrusted APK metadata", () => {
  assert.throws(
    () => releaseManifest({ ...fixture, assets: fixture.assets.slice(1) }),
    /Missing/,
  );
  assert.throws(
    () =>
      releaseManifest({
        ...fixture,
        assets: [...fixture.assets, fixture.assets[0]],
      }),
    /duplicate/,
  );
  assert.throws(
    () =>
      releaseManifest({
        ...fixture,
        assets: fixture.assets.map((asset) => ({
          ...asset,
          browser_download_url: "https://example.com/app.apk",
        })),
      }),
    /Invalid/,
  );
  assert.throws(
    () =>
      releaseManifest({
        ...fixture,
        assets: fixture.assets.map((asset) => ({ ...asset, size: -1 })),
      }),
    /Invalid/,
  );
});
test("does not promote draft or prerelease APKs", () => {
  assert.throws(() => releaseManifest({ ...fixture, draft: true }), /stable/);
  assert.throws(
    () => releaseManifest({ ...fixture, prerelease: true }),
    /stable/,
  );
  assert.throws(
    () => releaseManifest({ ...fixture, tag_name: "../release" }),
    /stable/,
  );
});
test("creates a standalone project-site artifact with metadata, QR and attribution", async () => {
  const output = await mkdtemp(path.join(tmpdir(), "teledrive-web-"));
  try {
    await build({ output });
    for (const file of [
      "index.html",
      "privacy.html",
      "404.html",
      ".nojekyll",
      "release.json",
      "assets/phone-qr.svg",
      "assets/icon.svg",
      "assets/icon-180.png",
      "assets/share.png",
      "assets/fonts/roboto-latin.woff2",
      "assets/licenses/Solar.txt",
      "assets/licenses/Roboto.txt",
    ]) {
      assert.ok(
        (await readFile(path.join(output, file))).length > 0 ||
          file === ".nojekyll",
        file,
      );
    }
    const html = await readFile(path.join(output, "index.html"), "utf8");
    assert.ok(!html.includes("<!-- ICONS -->"));
    assert.ok(html.includes("tg-drive-arm64-v8a.apk"));
    assert.ok(!html.includes("tg-drive-x86_64.apk"));
    assert.ok(!html.includes("daveragos"));
    assert.match(
      await readFile(path.join(output, "assets/phone-qr.svg"), "utf8"),
      /<svg/,
    );
  } finally {
    await rm(output, { recursive: true, force: true });
  }
});

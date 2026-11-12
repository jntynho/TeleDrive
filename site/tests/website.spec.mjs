import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdir, readFile } from "node:fs/promises";
import { releaseManifest } from "../scripts/build.mjs";
const releaseFixture = releaseManifest(
  JSON.parse(
    await readFile(
      new URL("../release-snapshot.json", import.meta.url),
      "utf8",
    ),
  ),
);
const screenshots = new URL("../test-results/previews/", import.meta.url)
  .pathname;

test("mobile widths keep every page within the viewport and the menu remains usable", async ({
  page,
}) => {
  for (const width of [320, 360, 393, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("./");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
      `overflow at ${width}`,
    ).toBe(true);
    if (width < 601) {
      await page.getByRole("button", { name: "Menu", exact: true }).click();
      await expect(page.locator("#site-nav")).toBeVisible();
      await page
        .locator("#site-nav")
        .getByRole("link", { name: "Features", exact: true })
        .click();
      await expect(
        page.getByRole("button", { name: "Menu", exact: true }),
      ).toHaveAttribute("aria-expanded", "false");
    }
  }
});
test("app preview responds to keyboard and rapid changes without stale images", async ({
  page,
}) => {
  await page.goto("./");
  const home = page.getByRole("tab", { name: "Home", exact: true });
  await home.focus();
  await page.keyboard.press("ArrowRight");
  await expect(
    page.getByRole("tab", { name: "Gallery", exact: true }),
  ).toBeFocused();
  await expect(page.locator("#preview-image")).toHaveAttribute(
    "src",
    /gallery\.webp$/,
  );
  await page.keyboard.press("End");
  await expect(
    page.getByRole("tab", { name: "Settings", exact: true }),
  ).toBeFocused();
  await page.getByRole("tab", { name: "Audio", exact: true }).click();
  await page.getByRole("tab", { name: "Home", exact: true }).click();
  await expect(page.locator("#preview-image")).toHaveAttribute(
    "src",
    /home\.webp$/,
  );
  await expect(page.locator('[role="tab"][aria-selected="true"]')).toHaveCount(
    1,
  );
});
test("theme persists and reduced motion has no reveal animation", async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: "light", reducedMotion: "reduce" });
  await page.goto("./");
  await page
    .getByRole("button", { name: "Switch to dark theme", exact: true })
    .click();
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  expect(
    await page
      .locator(".reveal")
      .first()
      .evaluate((element) => getComputedStyle(element).opacity),
  ).toBe("1");
  expect(
    await page
      .locator("html")
      .evaluate((element) => getComputedStyle(element).scrollBehavior),
  ).toBe("auto");
});
test("download metadata selects the matching APK and exposes its published digest", async ({
  page,
}) => {
  await page.route("**/release.json", (route) =>
    route.fulfill({ json: releaseFixture }),
  );
  await page.goto("./");
  await expect(page.locator("[data-release-name]").first()).toContainText(
    "Release",
  );
  for (const abi of ["arm64-v8a", "armeabi-v7a"]) {
    const links = page.locator(`[data-download="${abi}"]`);
    for (const link of await links.all())
      await expect(link).toHaveAttribute(
        "href",
        new RegExp(`/releases/download/v[^/]+/tg-drive-${abi}\\.apk$`),
      );
    await expect(page.locator(`[data-size="${abi}"]`)).toContainText("MiB");
  }
  await expect(page.locator("#checksums")).toBeVisible();
  await page.locator("#checksums summary").click();
  await expect(page.locator("#checksum-list code")).toHaveCount(2);
});
test("GitHub download links still work when release metadata is unavailable", async ({
  page,
}) => {
  await page.route("**/release.json", (route) =>
    route.fulfill({ status: 503, body: "Unavailable" }),
  );
  await page.goto("./");
  await expect(
    page.locator('[data-download="arm64-v8a"]').first(),
  ).toHaveAttribute(
    "href",
    "https://github.com/jntynho/TeleDrive/releases/latest/download/tg-drive-arm64-v8a.apk",
  );
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});
test("QR dialog supports copying, Escape and restored keyboard focus", async ({
  page,
  context,
}) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await page.goto("./");
  const trigger = page.getByRole("button", {
    name: "Open on your phone",
    exact: true,
  });
  await trigger.click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page
    .getByRole("button", { name: "Copy download-page link", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Link copied");
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(
    "https://jntynho.github.io/TeleDrive/#download",
  );
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(trigger).toBeFocused();
});
test("privacy page has working mobile navigation and loads without third-party assets", async ({
  page,
}) => {
  await page.setViewportSize({ width: 393, height: 852 });
  const failures = [];
  const external = [];
  page.on("pageerror", (error) => failures.push(error.message));
  page.on("request", (request) => {
    if (!request.url().startsWith("http://127.0.0.1:4173/"))
      external.push(request.url());
  });
  await page.goto("./privacy.html");
  await page.getByRole("button", { name: "Menu", exact: true }).click();
  await expect(
    page
      .locator("#site-nav")
      .getByRole("link", { name: "Download", exact: true }),
  ).toBeVisible();
  expect(external).toEqual([]);
  expect(failures).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
});
test("core content and APK links remain available without JavaScript", async ({
  browser,
}) => {
  const context = await browser.newContext({
    javaScriptEnabled: false,
    viewport: { width: 393, height: 852 },
  });
  const page = await context.newPage();
  await page.goto("http://127.0.0.1:4173/TeleDrive/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.locator("#site-nav")).toBeVisible();
  await expect(
    page.locator('[data-download="arm64-v8a"]').first(),
  ).toHaveAttribute("href", /releases\/latest\/download/);
  await context.close();
});
for (const theme of ["light", "dark"]) {
  test(`accessible contrast and semantics in ${theme} mode`, async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: theme, reducedMotion: "reduce" });
    await page.goto("./");
    const result = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(result.violations).toEqual([]);
    await page
      .getByRole("button", { name: "Open on your phone", exact: true })
      .click();
    const dialog = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(dialog.violations).toEqual([]);
  });
}
test("exports desktop and phone previews with complete assets", async ({
  page,
}) => {
  await mkdir(screenshots, { recursive: true });
  for (const [name, width, height, theme] of [
    ["desktop-light", 1440, 1080, "light"],
    ["desktop-dark", 1440, 1080, "dark"],
    ["mobile", 393, 852, "light"],
  ]) {
    await page.setViewportSize({ width, height });
    await page.emulateMedia({ colorScheme: theme, reducedMotion: "reduce" });
    await page.goto("./");
    await page.evaluate(() => document.fonts.ready);
    expect(
      await page
        .locator("img")
        .evaluateAll((images) =>
          images
            .filter((image) => image.complete && !image.naturalWidth)
            .map((image) => image.src),
        ),
    ).toEqual([]);
    await page.screenshot({
      path: `${screenshots}${name}.png`,
      fullPage: true,
    });
  }
});

test("release downloads remain usable when GitHub omits optional digests", async ({
  page,
}) => {
  await page.route("**/release.json", (route) =>
    route.fulfill({
      json: {
        ...releaseFixture,
        assets: releaseFixture.assets.map(({ sha256, ...asset }) => asset),
      },
    }),
  );
  await page.goto("./");
  await expect(page.locator('[data-size="arm64-v8a"]')).toContainText("MiB");
  await expect(page.locator("#checksums")).toBeHidden();
  await expect(
    page.locator('[data-download="arm64-v8a"]').first(),
  ).toHaveAttribute("href", releaseFixture.assets[0].url);
});

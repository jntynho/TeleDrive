# TeleDrive download website

An original, static product website for [TeleDrive](https://github.com/jntynho/TeleDrive), intended for **https://jntynho.github.io/TeleDrive/**.

## Design and content

The Telegrive reference was reviewed for its product presentation, download flow, app previews, installation instructions and FAQ. This site uses its own design, copy, assets and implementation: warm neutral surfaces, blue and sage accents, Roboto and the app's Solar icons. No Telegrive page code, artwork or screenshots are included.

The site includes real app screens with fictional sample data, light/dark themes, keyboard-accessible preview tabs, a phone QR code, installation guidance, privacy information, and exactly two standalone Android downloads. It makes no iOS/desktop or unconditional unlimited-storage claims. Telegram service limits and optional encryption are explained explicitly.

## Local development

Requires Node 22 or newer.

```sh
npm ci --prefix site
npm test --prefix site
npm run build --prefix site
npm run serve --prefix site
```

Open http://127.0.0.1:4173/TeleDrive/.

```sh
cd site
npx playwright install chromium
npm run test:browser
```

To use an existing Chromium binary, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium`. Browser tests cover responsive layouts, keyboard navigation, rapid preview changes, theme persistence, reduced motion, metadata failures, QR interaction, no-JavaScript downloads and WCAG contrast/semantics in both themes. Full-page previews are saved under `site/test-results/previews/`.

## GitHub Pages deployment

`.github/workflows/pages.yml` validates the latest stable release, builds and tests the site, uploads browser reports/previews and deploys the `site/dist` artifact. It then verifies the public HTML and release manifest. No Android build is required for a website update.

For the first deployment, a repository administrator must choose **Settings → Pages → Build and deployment → Source → GitHub Actions**. `actions/configure-pages` cannot enable Pages with the workflow's standard `GITHUB_TOKEN`; it does not have repository administration permission. No personal token is needed when enabling the source in Settings.

Website changes trigger the Pages workflow. The Android release workflow explicitly dispatches it after publishing both APKs, because events created with `GITHUB_TOKEN` do not normally trigger another release workflow. The website workflow also supports manual dispatch and externally published releases.

## Release information

CI reads GitHub's latest stable release once per build and writes a same-origin `release.json`. The browser never needs GitHub API credentials or its API rate limit. The builder rejects missing, duplicate, nonpositive-size or noncanonical APK assets. Published SHA-256 digests appear when GitHub supplies both of them.

`release-snapshot.json` is a verified build-62 snapshot for offline/local previews. CI always supplies fresh release data through `TELEDRIVE_RELEASE_FILE`. Static `releases/latest/download/` links still work without JavaScript or if loading the manifest fails. Select ARM64 for most modern phones; use ARM32 for a 32-bit ARM system. Both require Android 8.0+.

## Assets and privacy

All fonts, screenshots, icons and scripts are hosted on Pages. There is no analytics SDK, account form, Telegram sign-in form or user upload endpoint. Only the selected website theme is saved in browser storage. GitHub's hosting/download practices still apply. A restrictive HTML content security policy limits scripts, styles, images and data requests to the site itself.

The brand comes from `docs/brand/tg-drive-icon.svg`. Solar icons retain CC BY 4.0 attribution; the self-hosted Roboto subset retains its OFL license. The build copies both license texts. The sample libraries use fictional file names and placeholder thumbnails; no real user files or profile data are shown.

For optional native screenshot regeneration (Android toolchain required), from the repository root:

```sh
./gradlew --init-script site/tools/previews.init.gradle :app:testDebugUnitTest --tests '*WebsiteScreenshotsTest'
```

This opt-in fixture is added only to that unit-test compilation. It seeds a temporary sample library and exports real Compose screens to `site/tools/generated/`; normal Android builds do not include it. Encode reviewed captures as WebP in `site/public/assets/screens/` and refresh the social preview if screenshots change. The fixture does not assemble or publish APKs.

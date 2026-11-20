(() => {
  "use strict";
  const root = document.documentElement;
  const repository = "https://github.com/jntynho/TeleDrive";
  const pageUrl = "https://jntynho.github.io/TeleDrive/#download";
  const themeButton = document.querySelector(".theme-toggle");
  let themePreference;
  try {
    themePreference = localStorage.getItem("teledrive-theme");
  } catch {
    /* Storage can be disabled. */
  }
  function watchMedia(query, onChange) {
    const preference = matchMedia(query);
    if (typeof preference.addEventListener === "function") {
      preference.addEventListener("change", onChange);
    } else {
      preference.addListener?.(onChange);
    }
  }
  function reflectTheme() {
    const dark = root.dataset.theme === "dark";
    if (themeButton) {
      themeButton.hidden = false;
      themeButton.setAttribute(
        "aria-label",
        `Switch to ${dark ? "light" : "dark"} theme`,
      );
    }
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = dark ? "#111614" : "#f6f5f1";
  }
  reflectTheme();
  themeButton?.addEventListener("click", () => {
    themePreference = root.dataset.theme === "dark" ? "light" : "dark";
    root.dataset.theme = themePreference;
    try {
      localStorage.setItem("teledrive-theme", themePreference);
    } catch {
      /* Theme still works for this page. */
    }
    reflectTheme();
  });
  watchMedia("(prefers-color-scheme: dark)", (event) => {
    if (themePreference !== "dark" && themePreference !== "light") {
      root.dataset.theme = event.matches ? "dark" : "light";
      reflectTheme();
    }
  });

  const header = document.querySelector(".site-header");
  const menu = document.querySelector(".menu-toggle");
  const nav = document.querySelector("#site-nav");
  if (header && menu && nav) {
    function closeMenu() {
      header.classList.remove("nav-open");
      menu.setAttribute("aria-expanded", "false");
    }
    menu.addEventListener("click", () => {
      const open = header.classList.toggle("nav-open");
      menu.setAttribute("aria-expanded", String(open));
    });
    nav.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", (event) => {
        if (!header.classList.contains("nav-open")) return;
        closeMenu();
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        if (
          link.origin === location.origin &&
          link.pathname === location.pathname &&
          link.hash
        ) {
          document.getElementById(link.hash.slice(1))?.focus({ preventScroll: true });
        }
      });
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && header.classList.contains("nav-open")) {
        closeMenu();
        menu.focus();
      }
    });
    watchMedia("(max-width: 600px)", closeMenu);
    header.dataset.menuReady = "";
  }

  const screens = {
    home: {
      title: "The big picture, at a glance.",
      description:
        "Your library overview, recent files and the collections you reach for most. Everything starts here.",
      alt: "TeleDrive Home: library overview, shortcuts and recent files, using sample data.",
    },
    gallery: {
      title: "Make room for the memories.",
      description:
        "Photos and videos together, with albums, search, sorting and a gallery grid you can make your own.",
      alt: "TeleDrive Gallery with media filters, sorting and a thumbnail grid, using sample data.",
    },
    audio: {
      title: "Keep your favorites on repeat.",
      description:
        "Songs, recordings and audio files in one place, with favorites, on-device filtering and available music metadata.",
      alt: "TeleDrive Audio library with All, Favorites and On device filters, using sample data.",
    },
    settings: {
      title: "Your space. Your preferences.",
      description:
        "Manage your account, storage channels, backup, appearance and security. Make TeleDrive feel right for you.",
      alt: "TeleDrive Settings: account and preferences, using sample data.",
    },
  };
  const tablist = document.querySelector(".preview-tabs");
  const tabs = [...(tablist?.querySelectorAll("[data-screen]") || [])];
  const panel = document.querySelector("#app-preview");
  const previewImage = document.querySelector("#preview-image");
  const previewHeading = document.querySelector("#preview-heading");
  const previewDescription = document.querySelector("#preview-description");
  const previewStatus = document.querySelector(".preview-status");
  const previewReady =
    panel && previewImage && previewHeading && previewDescription && previewStatus;
  let screenRequest = 0;
  function showPreviewError() {
    previewImage.hidden = true;
    previewStatus.textContent = "Preview image unavailable.";
    panel.removeAttribute("aria-busy");
  }
  async function showScreen(key, focus = false) {
    const data = screens[key];
    const button = tabs.find((tab) => tab.dataset.screen === key);
    if (!data || !button) return;
    const request = ++screenRequest;
    tabs.forEach((tab) => {
      const selected = tab === button;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });
    panel.setAttribute("aria-labelledby", button.id);
    panel.setAttribute("aria-busy", "true");
    previewHeading.textContent = data.title;
    previewDescription.textContent = data.description;
    previewImage.hidden = true;
    previewStatus.textContent = "Loading preview…";
    if (focus) button.focus();
    const image = new Image();
    const loaded = new Promise((resolve) => {
      image.onload = () => resolve(true);
      image.onerror = () => resolve(false);
    });
    image.src = `./assets/screens/${key}.webp`;
    const available = await loaded;
    if (request !== screenRequest) return;
    if (!available) {
      showPreviewError();
      return;
    }
    previewImage.src = image.src;
    previewImage.alt = data.alt;
    previewImage.hidden = false;
    previewStatus.textContent = "";
    panel.removeAttribute("aria-busy");
    if (
      !matchMedia("(prefers-reduced-motion: reduce)").matches &&
      typeof previewImage.animate === "function"
    )
      previewImage.animate([{ opacity: 0.55 }, { opacity: 1 }], {
        duration: 180,
        easing: "ease-out",
      });
  }
  if (tablist && previewReady) {
    panel.setAttribute("role", "tabpanel");
    panel.setAttribute("aria-labelledby", "tab-home");
    panel.setAttribute("aria-describedby", "preview-heading preview-description");
    panel.tabIndex = 0;
    previewImage.addEventListener("error", () => {
      if (!previewImage.hidden) showPreviewError();
    });
    if (previewImage.complete && !previewImage.naturalWidth) showPreviewError();
    tabs.forEach((tab, index) => {
      tab.addEventListener("click", () => showScreen(tab.dataset.screen));
      tab.addEventListener("keydown", (event) => {
        const next =
          event.key === "ArrowRight"
            ? (index + 1) % tabs.length
            : event.key === "ArrowLeft"
              ? (index + tabs.length - 1) % tabs.length
              : event.key === "Home"
                ? 0
                : event.key === "End"
                  ? tabs.length - 1
                  : null;
        if (next !== null) {
          event.preventDefault();
          showScreen(tabs[next].dataset.screen, true);
        }
      });
    });
    tablist.hidden = false;
  }

  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  if (typeof IntersectionObserver === "function" && !reduced.matches) {
    const observer = new IntersectionObserver(
      (entries) =>
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        }),
      { threshold: 0.05 },
    );
    document
      .querySelectorAll(".reveal")
      .forEach((element) => observer.observe(element));
  }

  const phoneButton = document.querySelector("#phone-button");
  const dialog = document.querySelector("#phone-dialog");
  if (phoneButton && dialog && typeof dialog.showModal === "function") {
    const status = dialog.querySelector("#copy-status");
    let copyAttempt = 0;
    phoneButton.hidden = false;
    phoneButton.addEventListener("click", () => {
      copyAttempt++;
      status.textContent = "";
      dialog.showModal();
    });
    dialog
      .querySelector(".dialog-close")
      .addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", (event) => {
      const r = dialog.getBoundingClientRect();
      if (
        event.target === dialog &&
        (event.clientX < r.left ||
          event.clientX > r.right ||
          event.clientY < r.top ||
          event.clientY > r.bottom)
      )
        dialog.close();
    });
    dialog.querySelector("#copy-link").addEventListener("click", async () => {
      const attempt = ++copyAttempt;
      let message = "Link copied. See you there.";
      try {
        await navigator.clipboard.writeText(pageUrl);
      } catch {
        message =
          "Copy is unavailable here. You can open or copy the address below.";
      }
      if (dialog.open && attempt === copyAttempt) status.textContent = message;
    });
  }

  // Same-origin metadata is generated on Pages, rather than calling GitHub's API from every browser.
  if (document.querySelector("[data-download]")) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    fetch("./release.json", { cache: "no-cache", signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error("Release metadata unavailable");
        return response.json();
      })
      .then((release) => {
        if (
          typeof release?.tag !== "string" ||
          !/^v[\w.+-]+$/.test(release.tag) ||
          release.url !== `${repository}/releases/tag/${release.tag}` ||
          !Array.isArray(release.assets)
        )
          return;
        const valid = ["arm64-v8a", "armeabi-v7a"].map((abi) => {
          const name = `tg-drive-${abi}.apk`;
          const matches = release.assets.filter((asset) => asset?.name === name);
          const [asset] = matches;
          if (
            matches.length === 1 &&
            asset.abi === abi &&
            asset.url === `${repository}/releases/download/${release.tag}/${name}` &&
            Number.isSafeInteger(asset.bytes) &&
            asset.bytes > 0
          ) return asset;
        });
        if (valid.some((asset) => !asset)) return;
        document.querySelectorAll("[data-release-link]").forEach((link) => {
          link.href = release.url;
        });
        const build = release.tag.match(/-build\.(\d+)$/)?.[1];
        document.querySelectorAll("[data-release-name]").forEach((element) => {
          element.textContent = build
            ? `Release ${release.tag.slice(1).replace(/-build\..*$/, "")} · Build ${build}`
            : `Release ${release.tag.slice(1)}`;
        });
        for (const asset of valid) {
          document
            .querySelectorAll(`[data-download="${asset.abi}"]`)
            .forEach((link) => {
              link.href = asset.url;
            });
          document
            .querySelectorAll(`[data-size="${asset.abi}"]`)
            .forEach((element) => {
              element.textContent = `${(asset.bytes / 1048576).toFixed(2)} MiB`;
            });
        }
        if (valid.every((asset) => /^[a-f\d]{64}$/.test(asset.sha256 || ""))) {
          const list = document.querySelector("#checksum-list");
          if (list) {
            list.replaceChildren();
            valid.forEach((asset) => {
              const name = document.createElement("p");
              name.textContent = asset.name;
              const digest = document.createElement("code");
              digest.textContent = asset.sha256;
              list.append(name, digest);
            });
            document.querySelector("#checksums").hidden = false;
          }
        }
      })
      .catch(() => {
        /* The static latest/download links remain usable without metadata or JavaScript. */
      })
      .finally(() => clearTimeout(timeout));
  }
})();

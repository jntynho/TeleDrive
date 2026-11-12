(() => {
  "use strict";
  const root = document.documentElement;
  const repository = "https://github.com/jntynho/TeleDrive";
  const pageUrl = "https://jntynho.github.io/TeleDrive/#download";
  const themeButton = document.querySelector(".theme-toggle");
  function reflectTheme() {
    const dark = root.dataset.theme === "dark";
    if (themeButton) {
      themeButton.hidden = false;
      themeButton.setAttribute(
        "aria-label",
        `Switch to ${dark ? "light" : "dark"} theme`,
      );
      themeButton.setAttribute("aria-pressed", String(dark));
    }
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = dark ? "#111614" : "#f6f5f1";
  }
  reflectTheme();
  themeButton?.addEventListener("click", () => {
    root.dataset.theme = root.dataset.theme === "dark" ? "light" : "dark";
    try {
      localStorage.setItem("teledrive-theme", root.dataset.theme);
    } catch {
      /* Theme still works for this page. */
    }
    reflectTheme();
  });
  matchMedia("(prefers-color-scheme: dark)").addEventListener(
    "change",
    (event) => {
      let saved;
      try {
        saved = localStorage.getItem("teledrive-theme");
      } catch {
        /* No persistent preference. */
      }
      if (saved !== "dark" && saved !== "light") {
        root.dataset.theme = event.matches ? "dark" : "light";
        reflectTheme();
      }
    },
  );

  const header = document.querySelector(".site-header");
  const menu = document.querySelector(".menu-toggle");
  function closeMenu() {
    header?.classList.remove("nav-open");
    menu?.setAttribute("aria-expanded", "false");
  }
  menu?.addEventListener("click", () => {
    const open = header.classList.toggle("nav-open");
    menu.setAttribute("aria-expanded", String(open));
  });
  document
    .querySelectorAll("#site-nav a")
    .forEach((link) => link.addEventListener("click", closeMenu));
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && header?.classList.contains("nav-open")) {
      closeMenu();
      menu.focus();
    }
  });
  matchMedia("(min-width: 601px)").addEventListener("change", closeMenu);

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
  const tabs = [...document.querySelectorAll("[data-screen]")];
  const panel = document.querySelector("#app-preview");
  const previewImage = document.querySelector("#preview-image");
  let screenRequest = 0;
  async function showScreen(key, focus = false) {
    const data = screens[key];
    const button = tabs.find((tab) => tab.dataset.screen === key);
    if (!data || !button || !panel || !previewImage) return;
    const request = ++screenRequest;
    tabs.forEach((tab) => {
      const selected = tab === button;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });
    panel.setAttribute("aria-labelledby", button.id);
    panel.setAttribute("aria-busy", "true");
    if (focus) button.focus();
    const image = new Image();
    image.src = `./assets/screens/${key}.webp`;
    try {
      await image.decode();
    } catch {
      if (request === screenRequest) panel.removeAttribute("aria-busy");
      return;
    }
    if (request !== screenRequest) return;
    previewImage.src = image.src;
    previewImage.alt = data.alt;
    document.querySelector("#preview-heading").textContent = data.title;
    document.querySelector("#preview-description").textContent =
      data.description;
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

  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  const reveals = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window && !reduced.matches) {
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
    reveals.forEach((element) => observer.observe(element));
  } else reveals.forEach((element) => element.classList.add("is-visible"));

  const phoneButton = document.querySelector("#phone-button");
  const dialog = document.querySelector("#phone-dialog");
  if (phoneButton && dialog && typeof dialog.showModal === "function") {
    phoneButton.hidden = false;
    phoneButton.addEventListener("click", () => dialog.showModal());
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
    document.querySelector("#copy-link").addEventListener("click", async () => {
      const status = document.querySelector("#copy-status");
      try {
        await navigator.clipboard.writeText(pageUrl);
        status.textContent = "Link copied. See you there.";
      } catch {
        status.textContent =
          "Copy is unavailable here. You can open or copy the address below.";
      }
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
          !/^v[\w.+-]+$/.test(release.tag || "") ||
          release.url !== `${repository}/releases/tag/${release.tag}` ||
          !Array.isArray(release.assets)
        )
          return;
        const valid = ["arm64-v8a", "armeabi-v7a"].map((abi) =>
          release.assets.find(
            (asset) =>
              asset.abi === abi &&
              asset.name === `tg-drive-${abi}.apk` &&
              asset.url ===
                `${repository}/releases/download/${release.tag}/${asset.name}` &&
              Number.isSafeInteger(asset.bytes) &&
              asset.bytes > 0,
          ),
        );
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

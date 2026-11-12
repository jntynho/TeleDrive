(() => {
  const root = document.documentElement;
  root.dataset.js = "true";
  let saved;
  try {
    saved = localStorage.getItem("teledrive-theme");
  } catch {
    /* Storage can be disabled. */
  }
  root.dataset.theme =
    saved === "dark" || saved === "light"
      ? saved
      : matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";
})();

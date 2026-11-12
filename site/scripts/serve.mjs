import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { ROOT } from "./build.mjs";
const directory = path.join(ROOT, "dist");
const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json",
  ".svg": "image/svg+xml",
  ".webp": "image/webp",
  ".woff2": "font/woff2",
  ".png": "image/png",
  ".txt": "text/plain; charset=utf-8",
  ".xml": "application/xml; charset=utf-8",
};
const server = http.createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(
      new URL(req.url, "http://localhost").pathname,
    );
    // Exercise GitHub project-site URLs locally as well as the root URL.
    const relative = pathname
      .replace(/^\/TeleDrive(?=\/|$)/, "")
      .replace(/^\/+/, "");
    const file = path.resolve(
      directory,
      relative.endsWith("/") || !relative ? relative + "index.html" : relative,
    );
    if (!file.startsWith(directory + path.sep)) {
      res.writeHead(403);
      res.end();
      return;
    }
    const data = await readFile(file);
    res.writeHead(200, {
      "Content-Type": types[path.extname(file)] || "application/octet-stream",
    });
    res.end(data);
  } catch {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("Not found");
  }
});
server.listen(Number(process.env.PORT || 4173), "0.0.0.0", () =>
  console.log("TeleDrive website: http://localhost:4173/TeleDrive/"),
);

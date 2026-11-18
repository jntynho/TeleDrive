import { randomBytes, createHash } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, rm, readdir } from 'node:fs/promises';
import { createReadStream, createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import { Transform } from 'node:stream';
import path from 'node:path';

const validId = id => /^[A-Za-z0-9_-]{43}$/.test(id);
const newId = () => randomBytes(32).toString('base64url');
const fail = (status, message) => Object.assign(new Error(message), { status });

export async function uploadRequests({ directory, origin, maxBytes, clock, reply, headers, body, rate }) {
  const root = path.join(directory, 'incoming');
  const requests = new Map();
  const busy = new Set();
  const maxReserved = Math.min(2 * 1024 * 1024 * 1024, 4 * maxBytes);

  await mkdir(root, { recursive: true, mode: 0o700 });
  const location = (id, suffix) => path.join(root, id + suffix);
  for (const name of await readdir(root)) {
    const id = name.slice(0, 43);
    if (!validId(id)) continue;
    if (name === id + '.json') {
      const entry = JSON.parse(await readFile(location(id, '.json'), 'utf8'));
      if (entry.id === id) requests.set(id, entry);
    }
    if (name === id + '.part' || name === id + '.new') await rm(path.join(root, name), { force: true });
  }

  const save = async entry => {
    await writeFile(location(entry.id, '.new'), JSON.stringify(entry), { mode: 0o600 });
    await rename(location(entry.id, '.new'), location(entry.id, '.json'));
  };

  const remove = async id => {
    requests.delete(id);
    await Promise.all(['.bin', '.json', '.part', '.new'].map(suffix => rm(location(id, suffix), { force: true })));
  };
  const cleanup = async () => {
    for (const entry of requests.values()) {
      if (entry.expiresAt <= clock() && !busy.has(entry.id)) await remove(entry.id);
    }
  };
  const details = entry => ({ ...entry, url: new URL('/u/' + entry.id, origin).toString() });

  async function owner(req, res, segments) {
    await cleanup();
    const id = segments[2];
    if (segments.length === 2 && req.method === 'GET') {
      reply(res, 200, [...requests.values()].filter(entry => !busy.has(entry.id) || entry.incoming).map(details));
      return;
    }
    if (segments.length === 2 && req.method === 'POST') {
      let input;
      try {
        input = JSON.parse(await body(req));
      } catch (error) {
        if (error.status) throw error;
        throw fail(400, 'Invalid request options');
      }
      if (!input || typeof input !== 'object') throw fail(400, 'Invalid request options');
      const { expiresIn, maxUploadBytes } = input;
      if (!Number.isSafeInteger(expiresIn) || expiresIn < 300 || expiresIn > 604800 ||
          !Number.isSafeInteger(maxUploadBytes) || maxUploadBytes < 1 || maxUploadBytes > maxBytes) {
        throw fail(400, 'Invalid expiry or upload limit');
      }
      const reserved = [...requests.values()].reduce((bytes, request) => bytes + (request.incoming?.size ?? request.maxUploadBytes), 0);
      if (requests.size >= 100 || reserved + maxUploadBytes > maxReserved) throw fail(507, 'Request capacity reached');
      const entry = { id: newId(), expiresAt: clock() + expiresIn * 1000, maxUploadBytes, incoming: null };
      requests.set(entry.id, entry);
      busy.add(entry.id);
      try {
        await save(entry);
        reply(res, 201, details(entry));
      } catch (error) {
        await remove(entry.id);
        throw error;
      } finally {
        busy.delete(entry.id);
      }
      return;
    }
    const entry = validId(id) && requests.get(id);
    if (!entry || entry.expiresAt <= clock()) throw fail(404, 'Upload request unavailable');
    if (segments.length === 3 && req.method === 'DELETE') {
      if (busy.has(id)) throw fail(409, 'Upload in progress');
      await remove(id);
      reply(res, 200, { revoked: true });
      return;
    }
    if (segments.length === 4 && segments[3] === 'file' && req.method === 'GET' && entry.incoming) {
      // Open before setting headers so a concurrent revoke returns an ordinary failure.
      const file = createReadStream(location(id, '.bin'));
      await new Promise((resolve, reject) => { file.once('open', resolve); file.once('error', reject); });
      res.writeHead(200, {
        ...headers, 'Content-Type': 'application/octet-stream', 'Content-Length': entry.incoming.size,
        'Content-Disposition': 'attachment'
      });
      await pipeline(file, res);
      return;
    }
    throw fail(404, 'Upload request unavailable');
  }

  async function publicEndpoint(req, res, segments) {
    await cleanup();
    const id = segments[1];
    const entry = segments.length === 2 && validId(id) && requests.get(id);
    if (!entry || entry.incoming || entry.expiresAt <= clock()) throw fail(404, 'Upload request unavailable');
    if (req.method === 'GET') {
      const nonce = newId();
      const page = `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
        <title>TeleDrive upload request</title><main><h1>Send a file to TeleDrive</h1>
        <p>One file is allowed. Size limit ${entry.maxUploadBytes.toLocaleString('en')} bytes.</p>
        <form id="submit"><input id="file" aria-label="Select file" type="file" required><button>Upload file</button></form>
        <p id="state" role="status"></p></main>
        <script nonce="${nonce}">
          const form = document.getElementById('submit');
          const status = document.getElementById('state');
          const button = form.querySelector('button');
          form.addEventListener('submit', async event => {
            event.preventDefault();
            const file = document.getElementById('file').files[0];
            if (!file || file.size === 0 || file.size > ${entry.maxUploadBytes}) {
              status.textContent = 'Choose a file within the size limit.';
              return;
            }
            button.disabled = true;
            status.textContent = 'Uploading…';
            try {
              const name = Array.from(new TextEncoder().encode(file.name), byte => String.fromCharCode(byte)).join('');
              const response = await fetch(window.location.pathname, {
                method: 'POST', headers: { 'X-File-Name': btoa(name).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '') },
                body: file, credentials: 'omit'
              });
              if (!response.ok) throw new Error();
              form.remove();
              status.textContent = 'File uploaded.';
            } catch {
              button.disabled = false;
              status.textContent = 'Upload failed. Check the request and try again.';
            }
          });
        </script>`;
      res.writeHead(200, {
        ...headers, 'Content-Type': 'text/html; charset=utf-8',
        'Content-Security-Policy': `default-src 'none'; script-src 'nonce-${nonce}'; connect-src 'self'; form-action 'none'; frame-ancestors 'none'`
      });
      res.end(page);
      return;
    }
    if (req.method !== 'POST') throw fail(405, 'Method not allowed');
    if (req.headers.origin && req.headers.origin !== origin.origin) throw fail(403, 'Unexpected upload origin');
    if (!rate(req, 'incoming-upload', 20)) throw fail(429, 'Too many upload attempts');
    if (busy.has(id)) throw fail(409, 'Upload in progress');
    const expected = Number(req.headers['content-length']);
    if (!Number.isSafeInteger(expected) || expected < 1) throw fail(411, 'Upload length required');
    if (expected > entry.maxUploadBytes) throw fail(413, 'Upload too large');
    const encoded = String(req.headers['x-file-name'] || '');
    if (!/^[A-Za-z0-9_-]{1,1024}$/.test(encoded)) throw fail(400, 'Invalid filename');
    const name = Buffer.from(encoded, 'base64url').toString('utf8');
    if (!name || name.length > 180 || /[\x00-\x1f\x7f/\\]/.test(name) || name === '.' || name === '..') {
      throw fail(400, 'Invalid filename');
    }

    busy.add(id);
    try {
      const digest = createHash('sha256');
      let size = 0;
      const meter = new Transform({
        transform(chunk, _, finished) {
          size += chunk.length;
          if (size > expected) return finished(fail(413, 'Upload too large'));
          digest.update(chunk);
          finished(null, chunk);
        }
      });
      await pipeline(req, meter, createWriteStream(location(id, '.part'), { flags: 'wx', mode: 0o600 }));
      if (size !== expected) throw fail(422, 'Upload incomplete');
      if (entry.expiresAt <= clock()) throw fail(410, 'Upload request expired');
      const complete = { ...entry, incoming: { name, size, sha256: digest.digest('hex') } };
      await rename(location(id, '.part'), location(id, '.bin'));
      await save(complete);
      requests.set(id, complete);
      reply(res, 201, { uploaded: true });
    } catch (error) {
      await Promise.all(['.part', '.bin', '.new'].map(suffix => rm(location(id, suffix), { force: true })));
      throw error;
    } finally {
      busy.delete(id);
    }
  }

  return { owner, publicEndpoint };
}

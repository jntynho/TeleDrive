import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { pipeline } from 'node:stream/promises';
import { Transform } from 'node:stream';
import path from 'node:path';

const token = () => randomBytes(32).toString('base64url');
const hash = value => createHash('sha256').update(value).digest('hex');
const fail = (status, message) => Object.assign(new Error(message), { status });
const idPattern = /^[A-Za-z0-9_-]{43}$/;
const xml = value => String(value).replace(/[<>&"']/g, character => ({
  '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;'
})[character]);

function segment(raw) {
  const value = raw.normalize('NFC');
  if (!value || value === '.' || value === '..' || value === '__proto__' || value.length > 180 ||
      /[\x00-\x1F\x7F/\\]/.test(value)) throw fail(400, 'Invalid file or folder name');
  return value;
}

function pathname(raw) {
  if (typeof raw !== 'string' || raw.length > 2048) throw fail(400, 'Invalid file path');
  return raw ? raw.split('/').map(segment).join('/') : '';
}

const parentOf = name => name.includes('/') ? name.slice(0, name.lastIndexOf('/')) : '';
const partOf = (base, name) => !base || name === base || name.startsWith(base + '/');
const tagged = row => '"' + (row.sha256 || row.updatedAt.toString(16)) + '"';
const encodedPath = name => name.split('/').map(encodeURIComponent).join('/');

export async function createWorkspaceRouter({ directory, origin, clock, maxBytes, authenticated, body, reply, headers }) {
  const root = path.join(directory, 'workspaces');
  const spaces = new Map();
  const busy = new Set();
  await mkdir(root, { recursive: true, mode: 0o700 });
  for (const id of await readdir(root)) {
    if (!idPattern.test(id)) continue;
    const record = await readFile(path.join(root, id, 'state.json'), 'utf8').catch(error => {
      if (error.code === 'ENOENT') return null;
      throw error;
    });
    if (record) spaces.set(id, JSON.parse(record));
    const saved = spaces.get(id);
    if (saved) {
      const live = new Set(Object.values(saved.entries).map(row => row.blob).filter(Boolean));
      for (const name of await readdir(path.join(root, id))) {
        if (name.endsWith('.part') || name.endsWith('.new') ||
            (name.endsWith('.bin') && !live.has(name.slice(0, -4)))) {
          await rm(path.join(root, id, name), { force: true });
        }
      }
    }
  }

  const persist = async (id, space) => {
    const folder = path.join(root, id);
    await mkdir(folder, { recursive: true, mode: 0o700 });
    await writeFile(path.join(folder, 'state.new'), JSON.stringify(space), { mode: 0o600 });
    await rename(path.join(folder, 'state.new'), path.join(folder, 'state.json'));
    spaces.set(id, space);
  };

  async function mutate(id, operation) {
    if (busy.has(id)) throw fail(409, 'Another workspace change is in progress. Retry shortly.');
    busy.add(id);
    try {
      const space = spaces.get(id);
      const copy = structuredClone(space);
      const result = await operation(copy);
      await persist(id, copy);
      await result?.cleanup?.();
      return result?.value;
    } finally {
      busy.delete(id);
    }
  }

  function credentials(req) {
    const value = req.headers.authorization || '';
    if (value.startsWith('Bearer ')) return value.slice(7);
    if (value.startsWith('Basic ')) {
      const decoded = Buffer.from(value.slice(6), 'base64').toString('utf8');
      const split = decoded.indexOf(':');
      return split >= 0 ? decoded.slice(split + 1) : '';
    }
    return '';
  }

  function roleFor(req, space) {
    if (authenticated(req)) return 'owner';
    const key = credentials(req);
    if (!idPattern.test(key)) return null;
    const digest = Buffer.from(hash(key), 'hex');
    return space.members.find(member => timingSafeEqual(Buffer.from(member.digest, 'hex'), digest))?.role ?? null;
  }

  function requireEditor(role) {
    if (role !== 'owner' && role !== 'editor') throw fail(403, 'This member has read-only access');
  }

  function verifyWrite(row, req) {
    if (req.headers['if-none-match'] === '*' && row) throw fail(412, 'The file already exists');
    const expected = req.headers['if-match'];
    if (expected && (!row || (expected !== '*' && expected !== tagged(row)))) {
      throw fail(412, 'The file has changed');
    }
  }

  function addDirectory(space, name) {
    if (!name || Object.hasOwn(space.entries, name)) throw fail(405, 'The folder already exists');
    if (space.entries[parentOf(name)]?.kind !== 'folder') throw fail(409, 'The parent folder does not exist');
    if (Object.keys(space.entries).length >= 5000) throw fail(507, 'Workspace entry limit reached');
    space.entries[name] = { kind: 'folder', updatedAt: clock() };
  }

  async function store(req, res, id, name, role) {
    requireEditor(role);
    const length = Number(req.headers['content-length']);
    if (!Number.isSafeInteger(length) || length < 0) throw fail(411, 'Content-Length is required');
    if (length > maxBytes) throw fail(413, 'The file exceeds the configured server limit');
    let temporary;
    let stored;
    let committed = false;
    try {
      const written = await mutate(id, async space => {
        const old = Object.hasOwn(space.entries, name) ? space.entries[name] : null;
        verifyWrite(old, req);
        if (!name || old?.kind === 'folder' || space.entries[parentOf(name)]?.kind !== 'folder') {
          throw fail(409, 'The file path is unavailable');
        }
        if (!old && Object.keys(space.entries).length >= 5000) throw fail(507, 'Workspace entry limit reached');
        const used = Object.values(space.entries).reduce((total, row) => total + (row.size || 0), 0);
        if (used + length - (old?.size || 0) > space.quotaBytes) throw fail(507, 'Workspace storage limit reached');
        const blob = token();
        temporary = path.join(root, id, blob + '.part');
        stored = path.join(root, id, blob + '.bin');
        let received = 0;
        const digest = createHash('sha256');
        await pipeline(req, new Transform({
          transform(chunk, _, done) {
            received += chunk.length;
            if (received > length) return done(fail(413, 'The file is larger than declared'));
            digest.update(chunk);
            done(null, chunk);
          }
        }), createWriteStream(temporary, { flags: 'wx', mode: 0o600 }));
        const checksum = digest.digest('hex');
        if (received !== length || (req.headers['x-content-sha256'] && req.headers['x-content-sha256'] !== checksum)) {
          throw fail(422, 'File size or checksum mismatch');
        }
        await rename(temporary, stored);
        const row = { kind: 'file', size: received, sha256: checksum, blob, updatedAt: clock() };
        space.entries[name] = row;
        return { value: { row, created: !old }, cleanup: async () => {
          committed = true;
          if (old?.blob) await rm(path.join(root, id, old.blob + '.bin'), { force: true }).catch(() => {});
        } };
      });
      res.writeHead(written.created ? 201 : 204, { ...headers, ETag: tagged(written.row) });
      res.end();
    } finally {
      if (temporary) await rm(temporary, { force: true });
      if (stored && !committed) await rm(stored, { force: true });
    }
  }

  async function sendFile(req, res, id, row) {
    if (row?.kind !== 'file') throw fail(404, 'File unavailable');
    const range = req.headers.range;
    let start = 0;
    let end = row.size - 1;
    let status = 200;
    if (range && (!req.headers['if-range'] || req.headers['if-range'] === tagged(row))) {
      const match = /^bytes=(\d*)-(\d*)$/.exec(range);
      if (match && (match[1] || match[2])) {
        if (match[1]) start = Number(match[1]);
        if (match[1] && match[2]) end = Math.min(Number(match[2]), end);
        if (!match[1]) start = Math.max(0, row.size - Number(match[2]));
        if (Number.isSafeInteger(start) && Number.isSafeInteger(end) && start <= end && start < row.size) status = 206;
        else status = 416;
      } else status = 416;
    }
    if (status === 416) {
      res.writeHead(416, { ...headers, 'Content-Range': 'bytes */' + row.size });
      return res.end();
    }
    const handle = createReadStream(path.join(root, id, row.blob + '.bin'), status === 206 ? { start, end } : {});
    await new Promise((resolve, reject) => { handle.once('open', resolve); handle.once('error', reject); });
    const extra = status === 206 ? { 'Content-Range': `bytes ${start}-${end}/${row.size}` } : {};
    res.writeHead(status, { ...headers, ...extra, ETag: tagged(row), 'Last-Modified': new Date(row.updatedAt).toUTCString(),
      'Content-Type': 'application/octet-stream', 'Content-Length': end - start + 1,
      'Content-Disposition': 'attachment', 'Accept-Ranges': 'bytes' });
    if (req.method === 'HEAD') {
      handle.destroy();
      res.end();
    } else await pipeline(handle, res);
  }

  async function remove(id, req, name, role) {
    requireEditor(role);
    await mutate(id, async space => {
      const row = Object.hasOwn(space.entries, name) ? space.entries[name] : null;
      if (!name || !row) throw fail(404, 'File or folder unavailable');
      verifyWrite(row, req);
      const removed = Object.entries(space.entries).filter(([key]) => partOf(name, key));
      for (const [key] of removed) delete space.entries[key];
      return { cleanup: async () => {
        for (const [, entry] of removed) {
          if (entry.blob) await rm(path.join(root, id, entry.blob + '.bin'), { force: true }).catch(() => {});
        }
      } };
    });
  }

  async function move(id, req, name, destination, role) {
    requireEditor(role);
    if (!name || !destination || partOf(name, destination)) throw fail(409, 'Invalid destination');
    await mutate(id, async space => {
      if (!Object.hasOwn(space.entries, name)) throw fail(404, 'Source unavailable');
      if (space.entries[parentOf(destination)]?.kind !== 'folder') throw fail(409, 'The parent folder is missing');
      if (Object.hasOwn(space.entries, destination)) throw fail(req.headers.overwrite === 'F' ? 412 : 409, 'Destination exists');
      verifyWrite(space.entries[name], req);
      for (const [entry, row] of Object.entries(space.entries)) {
        if (partOf(name, entry)) {
          space.entries[destination + entry.slice(name.length)] = row;
          delete space.entries[entry];
        }
      }
    });
  }

  async function dav(req, res, id, name, role, space) {
    const prefix = '/dav/' + id;
    const methods = 'OPTIONS, PROPFIND, GET, HEAD, PUT, MKCOL, DELETE, MOVE';
    const row = Object.hasOwn(space.entries, name) ? space.entries[name] : null;
    const end = (status) => { res.writeHead(status, headers); res.end(); };
    switch (req.method) {
      case 'OPTIONS':
        res.writeHead(204, { ...headers, DAV: '1', Allow: methods });
        res.end();
        return;
      case 'PROPFIND': {
        if (!row) throw fail(404, 'File or folder unavailable');
        if (!['0', '1'].includes(req.headers.depth ?? '1')) throw fail(403, 'Use Depth 0 or 1');
        req.resume();
        const result = Object.entries(space.entries).filter(([key]) => key === name ||
          (row.kind === 'folder' && req.headers.depth !== '0' && key && parentOf(key) === name));
        const elements = result.map(([key, entry]) => {
          const href = prefix + '/' + encodedPath(key) + (key && entry.kind === 'folder' ? '/' : '');
          const type = entry.kind === 'folder' ? '<D:collection/>' : '';
          return '<D:response><D:href>' + xml(href) + '</D:href><D:propstat><D:prop>' +
            '<D:displayname>' + xml(key.split('/').at(-1) || space.name) + '</D:displayname>' +
            '<D:resourcetype>' + type + '</D:resourcetype><D:getcontentlength>' + (entry.size || 0) +
            '</D:getcontentlength><D:getlastmodified>' + xml(new Date(entry.updatedAt).toUTCString()) +
            '</D:getlastmodified><D:getetag>' + xml(tagged(entry)) +
            '</D:getetag></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>';
        });
        reply(res, 207, '<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:">' +
          elements.join('') + '</D:multistatus>', 'application/xml; charset=utf-8');
        return;
      }
      case 'GET': case 'HEAD': await sendFile(req, res, id, row); return;
      case 'PUT': await store(req, res, id, name, role); return;
      case 'MKCOL':
        requireEditor(role);
        await mutate(id, async current => addDirectory(current, name));
        end(201);
        return;
      case 'DELETE': await remove(id, req, name, role); end(204); return;
      case 'MOVE': {
        let target;
        try { target = new URL(req.headers.destination, origin); }
        catch { throw fail(400, 'Invalid destination header'); }
        if (target.origin !== origin.origin || !target.pathname.startsWith(prefix + '/')) throw fail(403, 'Invalid destination');
        const decoded = target.pathname.slice(prefix.length + 1).replace(/\/$/, '').split('/').map(decodeURIComponent).join('/');
        await move(id, req, name, pathname(decoded), role);
        end(201);
        return;
      }
      default: throw fail(405, 'Method not allowed');
    }
  }

  async function api(req, res, url, parts) {
    const id = parts[2];
    if (parts.length === 2) {
      if (!authenticated(req)) throw fail(401, 'Owner token required');
      if (req.method === 'GET') {
        return reply(res, 200, [...spaces].map(([key, value]) => ({
          id: key, name: value.name, quotaBytes: value.quotaBytes
        })));
      }
      if (req.method !== 'POST') throw fail(405, 'Method not allowed');
      let input;
      try { input = JSON.parse(await body(req)); }
      catch (error) { throw error.status ? error : fail(400, 'Invalid workspace settings'); }
      if (typeof input?.name !== 'string' || spaces.size + busy.size >= 32) throw fail(400, 'Invalid name or workspace limit reached');
      const name = segment(input.name.trim());
      const key = token();
      const quotaBytes = Math.min(4 * maxBytes, 4 * 1024 * 1024 * 1024);
      busy.add(key);
      try {
        await persist(key, { name, quotaBytes, entries: { '': { kind: 'folder', updatedAt: clock() } }, members: [] });
      } finally {
        busy.delete(key);
      }
      return reply(res, 201, { id: key, name, quotaBytes });
    }
    const space = idPattern.test(id) ? spaces.get(id) : null;
    if (!space) throw fail(404, 'Workspace unavailable');
    const role = roleFor(req, space);
    if (!role) throw fail(401, 'Valid workspace token required');
    const endpoint = parts[3];
    if (endpoint === 'members') {
      if (role !== 'owner') throw fail(403, 'Owner access required');
      if (req.method === 'GET' && parts.length === 4) {
        return reply(res, 200, space.members.map(({ digest, ...member }) => member));
      }
      if (req.method === 'DELETE' && parts.length === 5) {
        await mutate(id, async current => { current.members = current.members.filter(member => member.id !== parts[4]); });
        res.writeHead(204, headers);
        return res.end();
      }
      if (req.method === 'POST' && parts.length === 4) {
        let input;
        try { input = JSON.parse(await body(req)); }
        catch (error) { throw error.status ? error : fail(400, 'Invalid invite settings'); }
        if (typeof input?.name !== 'string' || !['viewer', 'editor'].includes(input.role)) throw fail(400, 'Invalid member settings');
        const secret = token();
        const member = { name: segment(input.name.trim()), role: input.role, id: token() };
        await mutate(id, async current => {
          if (current.members.length >= 64) throw fail(507, 'Member limit reached');
          current.members.push({ ...member, digest: hash(secret) });
        });
        return reply(res, 201, { ...member, token: secret });
      }
      throw fail(405, 'Method not allowed');
    }
    if (parts.length === 3 && req.method === 'GET') return reply(res, 200, { id, name: space.name, role });
    const name = pathname(url.searchParams.get('path') ?? '');
    const entry = Object.hasOwn(space.entries, name) ? space.entries[name] : null;
    if (endpoint === 'entries' && req.method === 'GET') {
      if (entry?.kind !== 'folder') throw fail(404, 'Folder unavailable');
      const visible = Object.entries(space.entries).filter(([key]) => key && (url.searchParams.get('recursive') === '1'
        ? key !== name && partOf(name, key) : parentOf(key) === name));
      return reply(res, 200, visible.map(([key, { blob, ...row }]) => ({ path: key, ...row })));
    }
    if (endpoint === 'file' && ['GET', 'HEAD'].includes(req.method)) return sendFile(req, res, id, entry);
    if (endpoint === 'file' && req.method === 'PUT') return store(req, res, id, name, role);
    if (endpoint === 'folder' && req.method === 'POST') {
      requireEditor(role);
      await mutate(id, async current => addDirectory(current, name));
      return reply(res, 201, { path: name });
    }
    if (endpoint === 'entry' && req.method === 'DELETE') {
      await remove(id, req, name, role);
      res.writeHead(204, headers);
      return res.end();
    }
    throw fail(404, 'Endpoint unavailable');
  }

  return async function route(req, res, url, parts) {
    if (parts[0] === 'api' && parts[1] === 'workspaces') {
      await api(req, res, url, parts);
      return true;
    }
    if (parts[0] !== 'dav') return false;
    const id = parts[1];
    const space = idPattern.test(id) ? spaces.get(id) : null;
    if (!space) throw fail(404, 'Workspace unavailable');
    const role = roleFor(req, space);
    if (!role) {
      res.writeHead(401, { ...headers, 'WWW-Authenticate': 'Basic realm="TeleDrive workspace", charset="UTF-8"' });
      res.end();
      return true;
    }
    const name = pathname(parts.slice(2).map(decodeURIComponent).join('/'));
    await dav(req, res, id, name, role, space);
    return true;
  };
}

const byId = id => document.getElementById(id);
let key = '';
let workspace = null;
let folder = '';
let owner = false;
let generation = 0;
let busy = false;
const status = value => { byId('status').textContent = value; };

async function api(endpoint = '', { method = 'GET', path, body, headers } = {}) {
  const suffix = workspace ? '/' + workspace.id : '';
  const url = new URL('/api/workspaces' + suffix + (endpoint ? '/' + endpoint : ''), location.origin);
  if (path !== undefined) url.searchParams.set('path', path);
  const reply = await fetch(url, { method, headers: { Authorization: 'Bearer ' + key, ...headers }, body, cache: 'no-store' });
  if (!reply.ok) {
    const error = await reply.json().catch(() => null);
    throw new Error(error?.error || 'The workspace request failed.');
  }
  return reply;
}

function action(label, run) {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = label;
  button.addEventListener('click', () => perform(run));
  return button;
}

async function perform(run) {
  if (busy) return;
  busy = true;
  const version = generation;
  status('');
  try { await run(version); }
  catch (error) { if (version === generation) status(error.message); }
  finally { busy = false; }
}

function row(label, controls) {
  const element = document.createElement('li');
  const title = document.createElement('span');
  title.textContent = label;
  element.append(title, ...controls);
  return element;
}

async function showOwner() {
  workspace = null;
  const response = await (await api()).json();
  byId('spaces').replaceChildren(...response.map(space => row(space.name, [action('Open', async () => {
    workspace = space;
    await openWorkspace();
  })])));
  byId('owner').hidden = false;
  byId('files').hidden = true;
}

async function openWorkspace() {
  workspace = await (await api()).json();
  folder = '';
  byId('owner').hidden = true;
  byId('files').hidden = false;
  byId('name').textContent = workspace.name;
  byId('back-owner').hidden = !owner;
  byId('editing').hidden = workspace.role === 'viewer';
  byId('members').hidden = workspace.role !== 'owner';
  byId('invite-result').hidden = true;
  byId('member-token').value = '';
  await refresh();
  if (workspace.role === 'owner') await members();
}

async function refresh() {
  const rows = await (await api('entries', { path: folder })).json();
  const names = folder ? folder.split('/') : [];
  byId('path').replaceChildren(action('Files', () => openFolder('')),
    ...names.map((name, i) => action(name, () => openFolder(names.slice(0, i + 1).join('/')))));
  rows.sort((a, b) => (a.kind === b.kind ? a.path.localeCompare(b.path) : a.kind === 'folder' ? -1 : 1));
  byId('entries').replaceChildren(...rows.map(entry => {
    const name = entry.path.split('/').at(-1);
    const controls = [entry.kind === 'folder' ? action('Open', () => openFolder(entry.path))
      : action('Download', () => download(entry.path, name))];
    if (workspace.role !== 'viewer') controls.push(action('Delete', async () => {
      if (!window.confirm('Delete ' + name + ' from this workspace?')) return;
      await api('entry', { path: entry.path, method: 'DELETE' });
      await refresh();
    }));
    return row(name, controls);
  }));
  if (!rows.length) byId('entries').append(row('This folder is empty.', []));
}

async function openFolder(path) {
  folder = path;
  await refresh();
}

async function download(path, name) {
  const response = await api('file', { path });
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function members() {
  const users = await (await api('members')).json();
  byId('member-list').replaceChildren(...users.map(user => row(user.name + ' · ' + user.role, [action('Revoke', async () => {
    if (!window.confirm('Revoke this member’s workspace access?')) return;
    await api('members/' + user.id, { method: 'DELETE' });
    await members();
  })])));
}

function submit(id, callback) {
  byId(id).addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = Object.fromEntries(new FormData(form));
    perform(() => callback(form, payload));
  });
}

submit('login-form', async () => {
  key = byId('access').value.trim();
  const id = byId('workspace').value.trim();
  if (id && !/^[A-Za-z0-9_-]{43}$/.test(id)) throw new Error('Invalid workspace ID.');
  owner = !id;
  workspace = id ? { id } : null;
  if (owner) await showOwner();
  else await openWorkspace();
  byId('access').value = '';
  byId('login').hidden = true;
  byId('sign-out').hidden = false;
});

submit('new-space', async (form, body) => {
  await api('', { method: 'POST', body: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } });
  form.reset();
  await showOwner();
});
submit('mkdir', async (form, { name }) => {
  if (name.includes('/') || name.includes('\\')) throw new Error('Use a single folder name.');
  await api('folder', { method: 'POST', path: [folder, name].filter(Boolean).join('/') });
  form.reset();
  await refresh();
});
submit('invite', async (form, body) => {
  const user = await (await api('members', { method: 'POST', body: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } })).json();
  byId('member-token').value = user.token;
  byId('invite-result').hidden = false;
  form.reset();
  await members();
});
byId('workspace').value = new URLSearchParams(location.search).get('workspace') || '';
byId('back-owner').addEventListener('click', () => perform(showOwner));
byId('copy-invite').addEventListener('click', () => perform(async () => {
  await navigator.clipboard.writeText(byId('member-token').value);
  status('Access token copied. Share it only with the invited member.');
}));
byId('upload').addEventListener('change', event => {
  const input = event.currentTarget;
  const file = input.files[0];
  input.value = '';
  if (!file) return;
  perform(async () => {
    await api('file', { method: 'PUT', path: [folder, file.name].filter(Boolean).join('/'), body: file, headers: { 'If-None-Match': '*' } });
    await refresh();
  });
});
byId('sign-out').addEventListener('click', () => { if (!busy) location.replace('/drive'); });

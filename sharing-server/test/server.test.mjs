import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { createSharingServer } from '../src/server.mjs';

const token='test-only-owner-token-with-enough-entropy';
async function fixture(t,maxBytes=1024){
  const directory=await mkdtemp(path.join(os.tmpdir(),'tg-share-'));let time=Date.now();
  const server=await createSharingServer({directory,ownerToken:token,publicOrigin:'https://share.example/',maxBytes,clock:()=>time});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));await rm(directory,{recursive:true,force:true});});
  const base=`http://127.0.0.1:${server.address().port}`;
  const upload=async({name='report.txt',data='known bytes',password='',checksum,uploadId=''}={})=>fetch(base+'/api/shares',{method:'POST',headers:{Authorization:`Bearer ${token}`,'X-Upload-Id':uploadId,'X-File-Name':Buffer.from(name).toString('base64url'),'X-Share-Expires-In':'300','X-Share-Password':Buffer.from(password).toString('base64url'),'X-Content-SHA256':checksum||createHash('sha256').update(data).digest('hex')},body:data});
  return {base,upload,advance:ms=>{time+=ms;}};
}
test('owner token is required; malformed metadata cannot create shares',async t=>{
  const f=await fixture(t);assert.equal((await fetch(f.base+'/api/shares',{method:'POST',body:'x'})).status,401);
  assert.equal((await f.upload({name:'../escape'})).status,400);
});
test('uploaded bytes download exactly and private paths do not appear in responses',async t=>{
  const f=await fixture(t);const response=await f.upload();assert.equal(response.status,201);const share=await response.json();
  assert.equal(share.url,`https://share.example/s/${share.id}`);
  const result=await fetch(f.base+'/d/'+share.id);assert.equal(result.status,200);assert.equal(await result.text(),'known bytes');assert.match(result.headers.get('content-disposition'),/^attachment/);
});
test('password protection, revocation and expiry are enforced',async t=>{
  const f=await fixture(t);const s=await (await f.upload({password:'long test password'})).json();
  assert.equal((await fetch(f.base+'/d/'+s.id)).status,401);
  assert.equal((await fetch(f.base+'/d/'+s.id,{method:'POST',body:new URLSearchParams({password:'wrong'})})).status,401);
  const good=await fetch(f.base+'/d/'+s.id,{method:'POST',body:new URLSearchParams({password:'long test password'})});assert.equal(await good.text(),'known bytes');
  assert.equal((await fetch(f.base+'/api/shares/'+s.id,{method:'DELETE',headers:{Authorization:`Bearer ${token}`}})).status,200);
  assert.equal((await fetch(f.base+'/d/'+s.id)).status,404);
  const exp=await (await f.upload()).json();f.advance(300001);assert.equal((await fetch(f.base+'/d/'+exp.id)).status,404);
});
test('hash mismatch and oversized upload never become downloadable shares',async t=>{
  const f=await fixture(t,32);assert.equal((await f.upload({checksum:'0'.repeat(64)})).status,422);
  assert.equal((await f.upload({data:'a'.repeat(33)})).status,413);
  const list=await (await fetch(f.base+'/api/shares',{headers:{Authorization:`Bearer ${token}`}})).json();assert.deepEqual(list,[]);
});
test('shared filenames are escaped in the landing page',async t=>{
  const f=await fixture(t);const s=await (await f.upload({name:'<img onerror=x>.txt'})).json();const page=await (await fetch(f.base+'/s/'+s.id)).text();assert.ok(page.includes('&lt;img onerror=x&gt;'));assert.ok(!page.includes('<img onerror=x>'));
});

test('retrying an acknowledged upload returns the same public copy',async t=>{
  const f=await fixture(t);const first=await (await f.upload({uploadId:'same-task'})).json();
  const second=await (await f.upload({uploadId:'same-task'})).json();assert.deepEqual(second,first);
  assert.equal((await f.upload({uploadId:'same-task',data:'changed'})).status,409);
  const list=await (await fetch(f.base+'/api/shares',{headers:{Authorization:`Bearer ${token}`}})).json();assert.equal(list.length,1);
});

test('a request accepts one file and permits only an owner to read it',async t=>{
  const f = await fixture(t, 64);
  const authorize = { Authorization: `Bearer ${token}` };
  const collection = f.base + '/api/requests';
  const create = await fetch(collection, {
    method: 'POST', headers: authorize, body: JSON.stringify({ expiresIn: 300, maxUploadBytes: 40 })
  });
  assert.equal(create.status, 201);
  const request = await create.json();
  assert.equal(request.url, `https://share.example/u/${request.id}`);
  const incomingUrl = f.base + '/u/' + request.id;
  assert.equal((await fetch(incomingUrl)).status, 200);
  const data = 'incoming contents';
  const headers = { 'X-File-Name': Buffer.from('received.txt').toString('base64url') };
  assert.equal((await fetch(incomingUrl, { method: 'POST', headers, body: 'a'.repeat(41) })).status, 413);
  assert.equal((await fetch(incomingUrl, { method: 'POST', headers, body: data })).status, 201);
  assert.equal((await fetch(incomingUrl, { method: 'POST', headers, body: data })).status, 404);
  const fileUrl = collection + '/' + request.id + '/file';
  assert.equal((await fetch(fileUrl)).status, 401);
  const list = await (await fetch(collection, { headers: authorize })).json();
  assert.equal(list[0].incoming.sha256, createHash('sha256').update(data).digest('hex'));
  assert.equal(await (await fetch(fileUrl, { headers: authorize })).text(), data);
  assert.equal((await fetch(collection + '/' + request.id, { method: 'DELETE', headers: authorize })).status, 200);
  assert.equal((await fetch(fileUrl, { headers: authorize })).status, 404);
});

test('upload requests expire and cannot submit via a different browser origin', async t=>{
  const f = await fixture(t);
  const authorize = { Authorization: `Bearer ${token}` };
  const collection = f.base + '/api/requests';
  const request = await (await fetch(collection, {
    method: 'POST', headers: authorize, body: JSON.stringify({ expiresIn: 300, maxUploadBytes: 100 })
  })).json();
  const url = f.base + '/u/' + request.id;
  assert.equal((await fetch(url, {
    method: 'POST', headers: { Origin: 'https://another.example', 'X-File-Name': 'dGVzdA' }, body: 'value'
  })).status, 403);
  f.advance(300_001);
  assert.equal((await fetch(url)).status, 404);
  assert.deepEqual(await (await fetch(collection, { headers: authorize })).json(), []);
});

test('workspace roles scope file access, WebDAV and revoked credentials', async t => {
  const f = await fixture(t);
  const owner = { Authorization: 'Bearer ' + token };
  const base = f.base + '/api/workspaces';
  const create = async name => (await fetch(base, {
    method: 'POST', headers: owner, body: JSON.stringify({ name })
  })).json();
  const one = await create('Team room');
  const two = await create('Other room');
  const address = base + '/' + one.id;
  const invite = async role => (await fetch(address + '/members', {
    method: 'POST', headers: owner, body: JSON.stringify({ name: role, role })
  })).json();
  const member = await invite('viewer');
  const editor = await invite('editor');
  const view = { Authorization: 'Bearer ' + member.token };
  const write = { Authorization: 'Bearer ' + editor.token };
  const url = address + '/file?path=report.txt';
  assert.equal((await fetch(base + '/' + two.id, { headers: view })).status, 401);
  assert.equal((await fetch(url, { method: 'PUT', headers: view, body: 'secret' })).status, 403);
  assert.equal((await fetch(url, { method: 'PUT', headers: { ...write, 'If-None-Match': '*' }, body: 'hello' })).status, 201);
  assert.equal((await fetch(url, { method: 'PUT', headers: { ...write, 'If-Match': '"stale"' }, body: 'overwrite' })).status, 412);
  assert.equal(await (await fetch(url, { headers: view })).text(), 'hello');
  const partial = await fetch(url, { headers: { ...view, Range: 'bytes=1-3' } });
  assert.equal(partial.status, 206);
  assert.equal(await partial.text(), 'ell');
  const dav = f.base + '/dav/' + one.id + '/';
  const basic = { Authorization: 'Basic ' + Buffer.from('member:' + member.token).toString('base64') };
  const listing = await fetch(dav, { method: 'PROPFIND', headers: { ...basic, Depth: '1' } });
  assert.equal(listing.status, 207);
  assert.match(await listing.text(), /report\.txt/);
  assert.equal((await fetch(dav + 'folder', { method: 'MKCOL', headers: basic })).status, 403);
  assert.equal((await fetch(address + '/members/' + member.id, { method: 'DELETE', headers: owner })).status, 204);
  assert.equal((await fetch(url, { headers: view })).status, 401);
});

test('workspace browser serves a same-origin app while files require credentials', async t => {
  const f = await fixture(t);
  const page = await fetch(f.base + '/drive');
  assert.equal(page.status, 200);
  assert.match(page.headers.get('content-security-policy'), /connect-src 'self'/);
  assert.match(await page.text(), /TeleDrive Workspaces/);
  assert.equal((await fetch(f.base + '/drive/app.js')).status, 200);
  assert.equal((await fetch(f.base + '/api/workspaces')).status, 401);
});

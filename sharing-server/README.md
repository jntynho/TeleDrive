# Optional TG Drive copy-sharing service

This is an optional backend for public links, **not a desktop client**. It stores only explicitly exported file copies. It never receives Telegram sessions or signs in to Telegram. The app's normal storage features do not require this server.

Requires Node.js 22+; there are no npm dependencies:

```sh
npm test
OWNER_TOKEN='<generate at least 32 random characters securely>' \
PUBLIC_ORIGIN='https://share.your-domain.example/' \
DATA_DIR='/private/tgdrive-shares' npm start
```

Supply a real owner token through your deployment secret manager, not source control. The example is a placeholder, not a usable credential. The listener defaults to `127.0.0.1:8080`; put a trusted TLS reverse proxy in front. Example Caddy configuration after configuring a real domain:

```caddyfile
share.your-domain.example {
    request_body { max_size 512MB }
    reverse_proxy 127.0.0.1:8080
}
```

Enter the HTTPS origin and owner token in TeleDrive’s Account settings under Public links. Use the action in a backed-up file’s preview to create a link, then return to Public links to copy or revoke it. HTTPS certificates are verified; there is no trust-bypass option. Set filesystem permissions and persistent storage backups appropriate to your deployment. `MAX_UPLOAD_BYTES` defaults to 512 MiB, `maxShares` to 1000, and links expire between five minutes and seven days. Expired files are removed when the service handles cleanup on API use/access; add a scheduled operational cleanup if strict unattended disk reclamation is required.

Android exports a decrypted copy when sharing a vault item, only after explicit confirmation. This service's stored copy is **not end-to-end encrypted**. Link passwords are scrypt-hashed, not stored as plaintext. Revoking a link prevents future downloads; it cannot recall a copy already downloaded or terminate every existing open download. URLs should be treated as bearer secrets. Do not publish private files by default.

POST `/api/shares` requires owner authorization and bounded content length, verifies SHA-256, and atomically commits a private file. GET `/api/shares` lists active links for the authenticated owner. DELETE `/api/shares/:id` revokes access. Public landing pages escape filenames and downloads use attachment disposition. There is no URL-fetch endpoint or access to arbitrary server paths. Use deployment-level abuse controls, monitoring and storage quotas before public production exposure.

Local tests use loopback HTTP to exercise the server. They do not establish deployment, DNS, a real TLS certificate, scalability or availability. No public endpoint has been deployed by the development task.

## Incoming file requests

Open Public links → File requests to make a single-use upload link. The recipient can open the link in a browser and submit one file. Only the authenticated owner can list or download the incoming copy. Use Save to drive to verify its SHA-256 and queue it for the current Telegram drive.

The owner controls the size limit and expiry. The service reserves at most 100 requests and at most 2 GiB in total (or four times the configured server file limit, whichever is smaller). Expiry or revocation removes the server copy. Use Save to drive before expiry. The recipient can submit arbitrary data, so use the normal file preview safeguards when opening the received file.

## Team workspaces and WebDAV

Open **Settings → Account → Team workspaces** in the Android app or visit `https://your-server.example/drive` in a browser. A server owner can create workspaces and issue member tokens with viewer or editor permissions. Copy the workspace ID and the member's access token for them. Revoking the membership stops future requests made with that token.

Android can copy a backed-up file to a selected team workspace or verify and import a team file into the active Telegram drive. Shared workspace copies live on the HTTPS server. Team authorization uses separate tokens. The server receives neither Telegram sign-in credentials nor a content encryption key.

For rclone, configure a **WebDAV** remote against `https://your-server.example/dav/<workspace-id>/`. Use the **Other** vendor, any nonempty username and the member's token as the password. A viewer can list and download files. An editor can also create folders, upload files, move into unoccupied destinations and delete files. HTTP range downloads are supported.

For the desktop window, use [TeleDrive desktop](../desktop/README.md). The web and desktop clients connect to the team workspace service independently of the Android app.

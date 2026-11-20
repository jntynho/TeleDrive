# Optional TG Drive copy-sharing service

This optional service hosts public download links, incoming file requests and team workspaces. It stores files sent directly to it and uses its own owner and workspace tokens for authentication.

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

Use a trusted TLS certificate and restrict filesystem permissions for your service data and backups. `MAX_UPLOAD_BYTES` defaults to 512 MiB, `maxShares` to 1000, and links expire between five minutes and seven days. The service removes expired files during API use or access. Schedule cleanup if your deployment requires unattended disk reclamation.

The service can read its stored copies. It hashes link passwords with scrypt. Revoking a link prevents future downloads. Recipients keep files they already downloaded, and downloads in progress may continue. Treat URLs as bearer secrets.

POST `/api/shares` requires owner authorization and bounded content length, verifies SHA-256, and atomically commits a private file. GET `/api/shares` lists active links for the authenticated owner. DELETE `/api/shares/:id` revokes access. Public landing pages escape filenames and downloads use attachment disposition. There is no URL-fetch endpoint or access to arbitrary server paths. Use deployment-level abuse controls, monitoring and storage quotas before public production exposure.

Local tests use loopback HTTP to exercise the server. They do not establish deployment, DNS, a real TLS certificate, scalability or availability. No public endpoint has been deployed by the development task.

## Incoming file requests

Use an owner bearer token with `POST /api/requests` to create a single-use upload link. The recipient opens the link in a browser and submits one file. The owner can list requests with `GET /api/requests` and download the received file with `GET /api/requests/:id/file`.

The owner controls the size limit and expiry. The service reserves at most 100 requests and at most 2 GiB in total (or four times the configured server file limit, whichever is smaller). Download the received copy before expiry or revocation removes it from the service. The recipient can submit arbitrary data, so use the normal file preview safeguards when opening the received file.

## Team workspaces and WebDAV

Visit `https://your-server.example/drive` in a browser. A server owner can create workspaces and issue member tokens with viewer or editor permissions. Share the workspace ID and access token with each member. Revoking membership stops future requests made with that token.

For rclone, configure a **WebDAV** remote against `https://your-server.example/dav/<workspace-id>/`. Use the **Other** vendor, any nonempty username and the member's token as the password. A viewer can list and download files. An editor can also create folders, upload files, move into unoccupied destinations and delete files. HTTP range downloads are supported.

The desktop client connects directly to the same workspace service. [TeleDrive desktop](../desktop/README.md)

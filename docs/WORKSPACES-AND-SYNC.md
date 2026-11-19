# Workspaces, version history and synchronization

## Personal Telegram drive

- **Version history** is available from a backed-up file’s actions. Choose a file of the same type to make a new version. Saving an edit to a backed-up note also retains the confirmed Telegram revision. Restoring a revision stages it as the next version. History starts with replacements made by this release and uses the same content key as the archived revision.
- **Folder sync** is in **Settings → Backup**. Select a device folder, enter the matching top-level Telegram folder, choose a direction and review the plan. The operation compares file checksums. It skips unresolved conflicts and pending transfers. Download replacements preserve the old local file beside the result with a `.teledrive-sync-previous-` name. Uploads appear in Activity.
- **Telegram accounts** is in **Settings → Account**. Adding an account creates a fresh local profile. Switching restarts the app and resumes the selected profile’s stored transfers as the app reconnects. The default profile keeps its existing settings and storage. Alternate profiles use their own Telegram sessions, local indexes, key records, locks and transfer queues.

## Team workspaces

An optional TeleDrive HTTPS service hosts shared files and verifies member permissions. Members use a separate workspace access token. Owners manage viewer and editor permissions. In the Android app, open **Settings → Account → Team workspaces**. To send a file, open the file’s preview and select **Copy to team workspace**. Verify and import a shared file to queue its upload to your private Telegram drive.

The browser portal lives at `/drive`. The desktop program connects to the same HTTPS service. Use `/dav/<workspace-id>/` with a scoped member token for rclone and other WebDAV clients.

[Server and integration setup](../sharing-server/README.md) · [Desktop program](../desktop/README.md)

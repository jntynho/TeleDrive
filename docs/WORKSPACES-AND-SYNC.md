# Workspaces, Telegram accounts and folder sync

## Personal Telegram drive

- **Folder sync** is in **Settings → Backup**. Select a device folder, enter the matching top-level Telegram folder, choose a direction and review the plan. The operation compares file checksums. It skips unresolved conflicts and pending transfers. Download replacements preserve the old local file beside the result with a `.teledrive-sync-previous-` name. Uploads appear in Activity.
- In **Settings**, open your Telegram profile and select **Switch account** to see saved profiles or add an account. Adding an account creates a fresh local profile. Switching restarts the app and resumes the selected profile’s stored transfers as the app reconnects. Alternate profiles use their own Telegram sessions, local indexes, key records, locks and transfer queues.

## Team workspaces

The optional TeleDrive HTTPS service hosts shared files and verifies member permissions. Owners assign viewer and editor permissions, and members connect with a workspace access token.

The browser portal lives at `/drive`. The desktop program connects to the same HTTPS service. Use `/dav/<workspace-id>/` with a scoped member token for rclone and other WebDAV clients.

[Server and integration setup](../sharing-server/README.md) · [Desktop program](../desktop/README.md)

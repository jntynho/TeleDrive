# TeleDrive desktop

Open a shared workspace from a desktop window on Windows, macOS or Linux. Install Python 3.11 or newer with Tk support, then run:

```sh
python3 teledrive.py
```

Enter the HTTPS origin of your TeleDrive server. Members enter the workspace ID and their access token. An owner can leave the workspace ID empty to browse their workspaces.

Double-click a folder to browse it or a file to save a verified copy. Editors and owners can upload files and create folders. Downloads use a temporary file until the SHA-256 checksum and length match. The client keeps the token in memory for the session.

The workspace service controls member access. Files copied to a workspace from TeleDrive Android belong to that shared workspace. See [server setup](https://github.com/jntynho/TeleDrive/blob/feature/tele-drive-roadmap/sharing-server/README.md).

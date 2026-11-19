#!/usr/bin/env python3
"""TeleDrive desktop workspace client. Access tokens live only in memory."""
import hashlib
import http.client
import json
import os
import re
import ssl
import tempfile
import threading
from pathlib import Path
from urllib.parse import urlencode, urlsplit

ID = re.compile(r"[A-Za-z0-9_-]{43}\Z")


class Workspace:
    def __init__(self, origin, access, workspace=""):
        url = urlsplit(origin.strip())
        if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ("", "/"):
            raise ValueError("Enter an HTTPS server origin without a path.")
        if workspace and not ID.fullmatch(workspace):
            raise ValueError("Invalid workspace ID.")
        if len(access) < 32 or any(not 33 <= ord(char) <= 126 for char in access):
            raise ValueError("Enter a valid access token.")
        self.origin = url.geturl()
        self.host, self.port, self.access, self.id = url.hostname, url.port, access, workspace

    def exchange(self, method, endpoint="", *, parameters=None, body=None, headers=None):
        path = "/api/workspaces"
        if self.id:
            path += "/" + self.id
        if endpoint:
            path += "/" + endpoint
        if parameters:
            path += "?" + urlencode(parameters)
        link = http.client.HTTPSConnection(self.host, self.port, context=ssl.create_default_context(), timeout=90)
        try:
            link.request(method, path, body=body, headers={"Authorization": "Bearer " + self.access, **(headers or {})})
            response = link.getresponse()
            if not 200 <= response.status < 300:
                text = response.read(4096)
                try:
                    message = json.loads(text).get("error", "")
                except (ValueError, AttributeError):
                    message = ""
                raise OSError(message or f"Workspace request failed (HTTP {response.status}).")
            return link, response
        except BaseException:
            link.close()
            raise

    def document(self, endpoint="", parameters=None, method="GET"):
        link, response = self.exchange(method, endpoint, parameters=parameters)
        try:
            data = response.read(4 * 1024 * 1024 + 1)
            if len(data) > 4 * 1024 * 1024:
                raise OSError("Workspace metadata is too large.")
            return json.loads(data) if data else None
        finally:
            link.close()

    def upload(self, local, folder):
        local = Path(local)
        with local.open("rb") as source:
            stat = os.fstat(source.fileno())
            checksum = hashlib.file_digest(source, "sha256").hexdigest()
            source.seek(0)
            headers = {"Content-Length": str(stat.st_size), "If-None-Match": "*", "X-Content-SHA256": checksum}
            name = "/".join(p for p in (folder, local.name) if p)
            link, response = self.exchange("PUT", "file", parameters={"path": name}, body=source, headers=headers)
            try:
                response.read(1)
            finally:
                link.close()

    def download(self, item, target):
        target = Path(target)
        link, response = self.exchange("GET", "file", parameters={"path": item["path"]})
        temporary = None
        try:
            checksum = hashlib.sha256()
            length = 0
            with tempfile.NamedTemporaryFile(dir=target.parent, prefix=".teledrive-", delete=False) as stream:
                temporary = Path(stream.name)
                while chunk := response.read(256 * 1024):
                    length += len(chunk)
                    if length > item["size"]:
                        raise OSError("The remote file changed.")
                    checksum.update(chunk)
                    stream.write(chunk)
            if length != item["size"] or checksum.hexdigest() != item["sha256"]:
                raise OSError("The remote file failed its checksum verification.")
            os.replace(temporary, target)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
            link.close()


def main():
    import tkinter as tk
    from tkinter import filedialog, messagebox, simpledialog, ttk

    root = tk.Tk()
    root.title("TeleDrive Workspaces")
    root.geometry("790x570")
    root.minsize(540, 390)
    root.columnconfigure(0, weight=1)
    root.rowconfigure(2, weight=1)
    form = ttk.Frame(root, padding=12)
    form.grid(sticky="ew")
    for column in range(3):
        form.columnconfigure(column, weight=1)
    origin, key, selected_workspace = tk.StringVar(), tk.StringVar(), tk.StringVar()
    for index, (label, state, obscure) in enumerate((
        ("HTTPS server", origin, ""), ("Workspace ID (optional for owner)", selected_workspace, ""), ("Access token", key, "•"),
    )):
        ttk.Label(form, text=label).grid(row=0, column=index, sticky="w", padx=3)
        ttk.Entry(form, textvariable=state, show=obscure).grid(row=1, column=index, sticky="ew", padx=3)
    connection = None
    role = None
    folder = ""
    rows = []
    busy = False
    bar = ttk.Frame(root, padding=(12, 0))
    bar.grid(sticky="ew")
    location = ttk.Label(bar, text="Workspace disconnected")
    location.pack(side="left", fill="x", expand=True)
    listing = ttk.Treeview(root, columns=("name", "type", "size"), show="headings", selectmode="browse")
    for name, label, width in (("name", "Name", 390), ("type", "Type", 75), ("size", "Bytes", 115)):
        listing.heading(name, text=label)
        listing.column(name, width=width)
    listing.grid(sticky="nsew", padx=12, pady=8)
    commands = ttk.Frame(root, padding=(12, 0))
    commands.grid(sticky="ew")
    status = ttk.Label(root, padding=12)
    status.grid(sticky="ew")

    def perform(work, done):
        nonlocal busy
        if busy:
            return
        busy = True
        status.configure(text="Working…")

        def worker():
            try:
                answer = work()
            except Exception as error:
                answer = error
            root.after(0, finish, answer)

        def finish(answer):
            nonlocal busy
            busy = False
            if isinstance(answer, Exception):
                status.configure(text=str(answer))
            else:
                status.configure(text="")
                done(answer)

        threading.Thread(target=worker, daemon=True).start()

    def display(items):
        nonlocal rows
        rows = sorted(items, key=lambda row: (row.get("kind") == "file", row.get("name", row.get("path", "")).casefold()))
        listing.delete(*listing.get_children())
        for index, row in enumerate(rows):
            name = row.get("name") or row["path"].rsplit("/", 1)[-1]
            listing.insert("", "end", iid=str(index), values=(name, row.get("kind", "Workspace"), row.get("size", "")))
        location.configure(text="/" + folder if role else "Your workspaces")
        editable = role in ("owner", "editor")
        upload_button.configure(state="normal" if editable else "disabled")
        create_button.configure(state="normal" if editable else "disabled")

    def connect():
        try:
            client = Workspace(origin.get(), key.get().strip(), selected_workspace.get().strip())
        except ValueError as error:
            status.configure(text=str(error))
            return
        perform(client.document, lambda answer: connected(client, answer))

    def connected(client, data):
        nonlocal connection, role, folder
        connection, folder, role = client, "", data.get("role") if isinstance(data, dict) else None
        key.set("")
        if role:
            refresh()
        else:
            display(data)

    def refresh():
        if connection:
            perform(lambda: connection.document("entries", {"path": folder}) if role else connection.document(), display)

    def activate(event=None):
        selected = listing.selection()
        if busy or not selected or not connection:
            return
        row = rows[int(selected[0])]
        if role is None:
            next_client = Workspace(connection.origin, connection.access, row["id"])
            perform(next_client.document, lambda answer: connected(next_client, answer))
        elif row["kind"] == "folder":
            open_folder(row["path"])
        else:
            target = filedialog.asksaveasfilename(initialfile=row["path"].rsplit("/", 1)[-1])
            if target:
                perform(lambda: connection.download(row, target), lambda _: status.configure(text="Download verified."))

    def open_folder(value):
        nonlocal folder
        if busy:
            return
        folder = value
        refresh()

    def add_file():
        source = filedialog.askopenfilename()
        if source and connection:
            perform(lambda: connection.upload(source, folder), lambda _: refresh())

    def create_folder():
        name = simpledialog.askstring("New folder", "Folder name", parent=root)
        if name and "/" not in name and "\\" not in name:
            value = "/".join(piece for piece in (folder, name) if piece)
            perform(lambda: connection.document("folder", {"path": value}, "POST"), lambda _: refresh())

    ttk.Button(form, text="Connect", command=connect).grid(row=2, column=2, sticky="e", pady=(8, 0))
    ttk.Button(bar, text="Up", command=lambda: open_folder(folder.rpartition("/")[0])).pack(side="right", padx=4)
    ttk.Button(commands, text="Open / Download", command=activate).pack(side="left")
    ttk.Button(commands, text="Refresh", command=refresh).pack(side="left", padx=6)
    upload_button = ttk.Button(commands, text="Upload", state="disabled", command=add_file)
    upload_button.pack(side="left")
    create_button = ttk.Button(commands, text="New folder", state="disabled", command=create_folder)
    create_button.pack(side="left", padx=6)
    listing.bind("<Double-1>", activate)
    listing.bind("<Return>", activate)
    root.mainloop()


if __name__ == "__main__":
    main()

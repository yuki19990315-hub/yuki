#!/usr/bin/env python3
"""Small, private TOKIHA server. Run behind HTTPS with a persistent /data volume."""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import tarfile
import threading
import time
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent
DATA = Path(os.environ.get("TOKIHA_DATA_DIR", "./data")).resolve()
MAX_UPLOAD = int(os.environ.get("TOKIHA_MAX_UPLOAD_MB", "200")) * 1024 * 1024
SESSION_SECONDS = 30 * 24 * 3600
ALLOWED = {
    "image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp",
    "image/gif": ".gif", "image/heic": ".heic", "image/heif": ".heif",
    "video/mp4": ".mp4", "video/quicktime": ".mov", "video/webm": ".webm",
}
STATIC = {
    "/": ("index.html", "text/html; charset=utf-8"),
    "/index.html": ("index.html", "text/html; charset=utf-8"),
    "/guide.html": ("guide.html", "text/html; charset=utf-8"),
    "/app.js": ("app.js", "text/javascript; charset=utf-8"),
    "/styles.css": ("styles.css", "text/css; charset=utf-8"),
    "/manifest.webmanifest": ("manifest.webmanifest", "application/manifest+json"),
}
FAILED_LOGINS: dict[str, list[float]] = {}
LOGIN_LOCK = threading.Lock()


@contextmanager
def database():
    db = sqlite3.connect(DATA / "tokiha.sqlite3", timeout=30)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA busy_timeout=30000")
    db.execute("PRAGMA foreign_keys=ON")
    try:
        with db: yield db
    finally:
        db.close()


def initialize():
    DATA.mkdir(parents=True, exist_ok=True)
    (DATA / "media").mkdir(exist_ok=True)
    with database() as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript("""
            CREATE TABLE IF NOT EXISTS media (
                id TEXT PRIMARY KEY, filename TEXT NOT NULL, title TEXT NOT NULL,
                type TEXT NOT NULL, mime TEXT NOT NULL, captured_at TEXT NOT NULL,
                size INTEGER NOT NULL, sha256 TEXT NOT NULL, uploaded_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL
            );
        """)


def valid_media(head: bytes, mime: str) -> bool:
    if mime == "image/jpeg": return head.startswith(b"\xff\xd8\xff")
    if mime == "image/png": return head.startswith(b"\x89PNG\r\n\x1a\n")
    if mime == "image/gif": return head[:6] in (b"GIF87a", b"GIF89a")
    if mime == "image/webp": return head.startswith(b"RIFF") and head[8:12] == b"WEBP"
    if mime in ("image/heic", "image/heif"):
        return head[4:8] == b"ftyp" and head[8:12] in (b"heic", b"heix", b"hevc", b"hevx", b"mif1", b"msf1")
    if mime == "video/mp4": return head[4:8] == b"ftyp"
    if mime == "video/quicktime": return head[4:8] == b"ftyp" and head[8:12] == b"qt  "
    if mime == "video/webm": return head.startswith(b"\x1a\x45\xdf\xa3")
    return False


def iso_date(value: str) -> str:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None: raise ValueError("timezone required")
    return parsed.isoformat()


def clean_filename(value: str) -> str:
    name = value.replace("\\", "/").split("/")[-1]
    name = "".join(c if c.isprintable() and c not in '<>:"/\\|?*' else "_" for c in name).strip(" .")
    return name[:150] or "media"


class Handler(BaseHTTPRequestHandler):
    server_version = "TOKIHA"

    def setup(self):
        super().setup()
        self.connection.settimeout(120)

    def log_message(self, format, *args):
        # Standard access logs never include request bodies or cookies.
        super().log_message(format, *args)

    def headers_common(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", "default-src 'self'; img-src 'self' blob:; media-src 'self' blob:; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; script-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'")

    def respond(self, status, data, content_type="application/json; charset=utf-8", extra=None):
        body = json.dumps(data, ensure_ascii=False).encode() if isinstance(data, (dict, list)) else data
        self.send_response(status)
        self.headers_common()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for key, value in (extra or {}).items(): self.send_header(key, value)
        self.end_headers()
        if self.command != "HEAD": self.wfile.write(body)

    def error(self, status, message): self.respond(status, {"error": message})

    def auth(self):
        cookie = SimpleCookie()
        try: cookie.load(self.headers.get("Cookie", ""))
        except Exception: return False
        token = cookie.get("tokiha_session")
        if not token: return False
        digest = hashlib.sha256(token.value.encode()).hexdigest()
        with database() as db:
            return db.execute("SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?", (digest, int(time.time()))).fetchone() is not None

    def backup_auth(self):
        configured = os.environ.get("TOKIHA_BACKUP_TOKEN", "")
        bearer = self.headers.get("Authorization", "")
        return bool(configured and bearer.startswith("Bearer ") and hmac.compare_digest(bearer[7:], configured))

    def require_auth(self):
        if self.auth(): return True
        self.error(HTTPStatus.UNAUTHORIZED, "ログインしてください")
        return False

    def require_write(self):
        if not self.require_auth(): return False
        if self.headers.get("X-Tokiha-Request") != "1":
            self.error(HTTPStatus.FORBIDDEN, "不正な操作です")
            return False
        return True

    def read_json(self, limit=8192):
        if self.headers.get("Content-Type", "").split(";")[0] != "application/json": raise ValueError("JSON required")
        size = int(self.headers.get("Content-Length", "0"))
        if size < 1 or size > limit: raise ValueError("invalid request size")
        payload = json.loads(self.rfile.read(size))
        if not isinstance(payload, dict): raise ValueError("JSON object required")
        return payload

    def static(self, path):
        if path.startswith("/assets/") and path.count("/") == 2:
            file = ROOT / path.lstrip("/")
            if file.suffix != ".svg": return self.error(404, "Not found")
            mime = "image/svg+xml"
        elif path in STATIC:
            filename, mime = STATIC[path]
            file = ROOT / filename
        else: return self.error(404, "Not found")
        try: self.respond(200, file.read_bytes(), mime)
        except FileNotFoundError: self.error(404, "Not found")

    def media_row(self, identifier):
        try: identifier = str(uuid.UUID(identifier))
        except ValueError: return None
        with database() as db: return db.execute("SELECT * FROM media WHERE id=?", (identifier,)).fetchone()

    def send_file(self, file, mime, filename=None):
        if not file.is_file(): return self.error(404, "ファイルがありません")
        total = file.stat().st_size
        first, last = 0, total-1
        range_header = self.headers.get("Range", "") if not filename else ""
        if range_header:
            import re
            match = re.fullmatch(r"bytes=(\d+)-(\d*)", range_header)
            if not match: return self.error(416, "範囲が正しくありません")
            first = int(match.group(1)); last = int(match.group(2)) if match.group(2) else total-1
            if first > last or last >= total: return self.error(416, "範囲が正しくありません")
        self.send_response(206 if range_header else 200)
        self.headers_common()
        self.send_header("Content-Type", mime)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(last-first+1))
        if range_header: self.send_header("Content-Range", f"bytes {first}-{last}/{total}")
        if filename:
            from urllib.parse import quote
            self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{quote(filename)}")
        self.end_headers()
        if self.command != "HEAD":
            with file.open("rb") as source:
                source.seek(first)
                remaining = last-first+1
                while remaining:
                    chunk = source.read(min(1024 * 1024, remaining))
                    if not chunk: break
                    self.wfile.write(chunk); remaining -= len(chunk)

    def do_HEAD(self): self.do_GET()

    def do_GET(self):
        path = urlsplit(self.path).path
        if not path.startswith("/api/"): return self.static(path)
        if path == "/api/v1/session": return self.respond(200, {"authenticated": self.auth()})
        backup_route = path == "/api/v1/backup/manifest" or path.endswith("/download")
        if not (backup_route and self.backup_auth()) and not self.require_auth(): return
        if path == "/api/v1/media":
            with database() as db:
                rows = db.execute("SELECT * FROM media ORDER BY captured_at DESC, uploaded_at DESC").fetchall()
            return self.respond(200, {"files": [self.public_item(row) for row in rows]})
        if path == "/api/v1/backup/manifest":
            with database() as db: rows = db.execute("SELECT * FROM media ORDER BY uploaded_at").fetchall()
            return self.respond(200, {"files": [{"id": row["id"], "filename": row["filename"], "captured_at": row["captured_at"], "sha256": row["sha256"], "download_url": f"/api/v1/media/{row['id']}/download"} for row in rows]})
        if path == "/api/v1/backup/archive": return self.archive()
        parts = path.split("/")
        if len(parts) == 6 and parts[:4] == ["", "api", "v1", "media"] and parts[5] in ("file", "download"):
            row = self.media_row(parts[4])
            if not row: return self.error(404, "ファイルがありません")
            return self.send_file(DATA / "media" / (row["id"] + ALLOWED[row["mime"]]), row["mime"], row["filename"] if parts[5] == "download" else None)
        self.error(404, "Not found")

    @staticmethod
    def public_item(row):
        return {"id": row["id"], "title": row["title"], "name": row["filename"], "capturedAt": row["captured_at"],
                "type": row["type"], "mime": row["mime"], "size": row["size"], "src": f"/api/v1/media/{row['id']}/file",
                "download": f"/api/v1/media/{row['id']}/download"}

    def archive(self):
        with database() as db: rows = db.execute("SELECT * FROM media ORDER BY uploaded_at").fetchall()
        for row in rows:
            if not (DATA / "media" / (row["id"] + ALLOWED[row["mime"]])).is_file():
                return self.error(500, "原本が見つかりません")
        self.send_response(200)
        self.headers_common()
        self.send_header("Content-Type", "application/x-tar")
        self.send_header("Content-Disposition", 'attachment; filename="tokiha-backup.tar"')
        self.end_headers()
        self.close_connection = True
        if self.command == "HEAD": return
        with tarfile.open(fileobj=self.wfile, mode="w|") as archive:
            metadata = []
            for row in rows:
                source = DATA / "media" / (row["id"] + ALLOWED[row["mime"]])
                archive.add(source, arcname=f"media/{row['id']}{ALLOWED[row['mime']]}", recursive=False)
                metadata.append({"id": row["id"], "filename": row["filename"], "captured_at": row["captured_at"], "sha256": row["sha256"]})
            import io
            blob = json.dumps({"files": metadata}, ensure_ascii=False, indent=2).encode()
            info = tarfile.TarInfo("tokiha-backup.json"); info.size = len(blob)
            archive.addfile(info, io.BytesIO(blob))

    def do_POST(self):
        path = urlsplit(self.path).path
        if path == "/api/v1/login": return self.login()
        if path == "/api/v1/logout":
            if not self.require_write(): return
            cookie = SimpleCookie(); cookie.load(self.headers.get("Cookie", ""))
            token = cookie.get("tokiha_session")
            if token:
                with database() as db: db.execute("DELETE FROM sessions WHERE token_hash=?", (hashlib.sha256(token.value.encode()).hexdigest(),))
            return self.respond(200, {"ok": True}, extra={"Set-Cookie": "tokiha_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0"})
        if path == "/api/v1/media": return self.upload()
        self.error(404, "Not found")

    def login(self):
        address = self.client_address[0]
        with LOGIN_LOCK:
            recent = [t for t in FAILED_LOGINS.get(address, []) if t > time.time() - 600]
            FAILED_LOGINS[address] = recent
            if len(recent) >= 10: return self.error(429, "しばらく待ってから再試行してください")
        try: submitted = self.read_json().get("password", "")
        except (ValueError, json.JSONDecodeError): return self.error(400, "パスワードを入力してください")
        password = os.environ["TOKIHA_PASSWORD"]
        if not isinstance(submitted, str) or not hmac.compare_digest(submitted, password):
            with LOGIN_LOCK: FAILED_LOGINS[address].append(time.time())
            return self.error(401, "パスワードが違います")
        with LOGIN_LOCK: FAILED_LOGINS.pop(address, None)
        token = secrets.token_urlsafe(32)
        with database() as db:
            db.execute("DELETE FROM sessions WHERE expires_at<?", (int(time.time()),))
            db.execute("INSERT INTO sessions VALUES (?,?)", (hashlib.sha256(token.encode()).hexdigest(), int(time.time()) + SESSION_SECONDS))
        secure = "; Secure" if os.environ.get("TOKIHA_SECURE_COOKIE", "1") == "1" else ""
        self.respond(200, {"ok": True}, extra={"Set-Cookie": f"tokiha_session={token}; HttpOnly; SameSite=Strict; Path=/; Max-Age={SESSION_SECONDS}{secure}"})

    def upload(self):
        if not self.require_write(): return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= MAX_UPLOAD: return self.error(413, f"1ファイルの上限は{MAX_UPLOAD // 1024 // 1024}MBです")
            mime = self.headers.get("Content-Type", "").split(";")[0].lower()
            if mime not in ALLOWED: return self.error(415, "対応していない形式です")
            filename = clean_filename(unquote(self.headers.get("X-File-Name", "media")))
            captured_at = iso_date(self.headers.get("X-Captured-At", ""))
        except (ValueError, OverflowError): return self.error(400, "ファイル情報が正しくありません")
        identifier = str(uuid.uuid4())
        temporary = DATA / "media" / (identifier + ".part")
        destination = DATA / "media" / (identifier + ALLOWED[mime])
        digest = hashlib.sha256()
        try:
            remaining = size
            with temporary.open("xb") as target:
                first = self.rfile.read(min(4096, remaining)); remaining -= len(first)
                if not valid_media(first, mime): return self.error(415, "ファイルの形式が一致しません")
                target.write(first); digest.update(first)
                while remaining:
                    chunk = self.rfile.read(min(1024 * 1024, remaining))
                    if not chunk: return self.error(400, "アップロードが途中で終了しました")
                    target.write(chunk); digest.update(chunk); remaining -= len(chunk)
                target.flush(); os.fsync(target.fileno())
            temporary.replace(destination)
            with database() as db:
                db.execute("INSERT INTO media VALUES (?,?,?,?,?,?,?,?,?)", (identifier, filename, Path(filename).stem[:150],
                           "video" if mime.startswith("video/") else "image", mime, captured_at, size, digest.hexdigest(), datetime.now(timezone.utc).isoformat()))
            return self.respond(201, self.public_item(self.media_row(identifier)))
        except (OSError, sqlite3.Error):
            destination.unlink(missing_ok=True)
            return self.error(500, "保存できませんでした")
        finally: temporary.unlink(missing_ok=True)

    def do_PATCH(self):
        if not self.require_write(): return
        parts = urlsplit(self.path).path.split("/")
        if len(parts) != 5 or parts[:4] != ["", "api", "v1", "media"]: return self.error(404, "Not found")
        row = self.media_row(parts[4])
        if not row: return self.error(404, "ファイルがありません")
        try: date = iso_date(self.read_json().get("capturedAt", ""))
        except (ValueError, json.JSONDecodeError): return self.error(400, "日付が正しくありません")
        with database() as db: db.execute("UPDATE media SET captured_at=? WHERE id=?", (date, row["id"]))
        self.respond(200, self.public_item(self.media_row(row["id"])))


def main():
    password = os.environ.get("TOKIHA_PASSWORD", "")
    if len(password) < 16: raise SystemExit("TOKIHA_PASSWORD must be at least 16 characters")
    if os.environ.get("TOKIHA_SECURE_COOKIE", "1") != "1" and os.environ.get("TOKIHA_HOST", "127.0.0.1") not in ("127.0.0.1", "localhost"):
        raise SystemExit("Insecure cookies are only permitted on localhost")
    initialize()
    host = os.environ.get("TOKIHA_HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "8000"))
    print(f"TOKIHA listening on {host}:{port}", flush=True)
    ThreadingHTTPServer((host, port), Handler).serve_forever()


if __name__ == "__main__": main()

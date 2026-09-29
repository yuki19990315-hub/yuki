import hashlib
import io
import json
import os
import socket
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
PNG = bytes.fromhex("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c636000020000050001a5f645400000000049454e44ae426082")


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0)); self.port = sock.getsockname()[1]
        self.env = {**os.environ, "TOKIHA_DATA_DIR": self.temp.name, "TOKIHA_HOST": "127.0.0.1",
                    "TOKIHA_SECURE_COOKIE": "0", "TOKIHA_PASSWORD": "test-password-123456", "TOKIHA_BACKUP_TOKEN": "backup-token-test", "PORT": str(self.port)}
        self.start_server()

    def start_server(self):
        self.process = subprocess.Popen([sys.executable, str(ROOT / "server.py")], env=self.env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        for _ in range(100):
            try:
                self.request("/api/v1/session")
                return
            except URLError:
                if self.process.poll() is not None: self.fail(self.process.stderr.read().decode())
                time.sleep(.02)
        self.fail("server did not start")

    def tearDown(self):
        self.process.terminate()
        try: self.process.wait(timeout=3)
        except subprocess.TimeoutExpired: self.process.kill(); self.process.wait()
        self.process.stderr.close()
        self.temp.cleanup()

    def request(self, path, data=None, method=None, headers=None):
        return urlopen(Request(f"http://127.0.0.1:{self.port}{path}", data=data, method=method, headers=headers or {}), timeout=5)

    def test_private_persistent_upload_and_read_only_backup(self):
        with self.assertRaises(HTTPError) as denied:
            self.request("/api/v1/media")
        self.assertEqual(denied.exception.code, 401)
        with self.request("/api/v1/login", json.dumps({"password":"test-password-123456"}).encode(), headers={"Content-Type":"application/json"}) as response:
            cookie = response.headers["Set-Cookie"].split(";", 1)[0]
        auth = {"Cookie": cookie}
        headers = {**auth, "Content-Type":"image/png", "X-File-Name":"family.png", "X-Captured-At":"2026-09-29T12:00:00+09:00", "X-Tokiha-Request":"1"}
        with self.request("/api/v1/media", PNG, headers=headers) as response:
            item = json.load(response)
        self.assertEqual(item["size"], len(PNG))
        self.assertEqual(self.request(item["src"], headers=auth).read(), PNG)
        with self.request(item["src"], headers={**auth,"Range":"bytes=0-7"}) as response:
            self.assertEqual(response.status, 206)
            self.assertEqual(response.read(), PNG[:8])
        with self.request("/api/v1/backup/archive", headers=auth) as response:
            with tarfile.open(fileobj=io.BytesIO(response.read())) as archive:
                self.assertIn(PNG, [archive.extractfile(name).read() for name in archive.getnames() if name.startswith("media/")])
        with self.request("/api/v1/backup/manifest", headers={"Authorization":"Bearer backup-token-test"}) as response:
            manifest = json.load(response)
        self.assertEqual(manifest["files"][0]["sha256"], hashlib.sha256(PNG).hexdigest())
        with self.assertRaises(HTTPError) as denied:
            self.request("/api/v1/media", PNG, headers={**headers, "Cookie":"", "Authorization":"Bearer backup-token-test"})
        self.assertEqual(denied.exception.code, 401)
        with self.assertRaises(HTTPError) as denied:
            self.request("/api/v1/media", b"not-a-png", headers=headers)
        self.assertEqual(denied.exception.code, 415)
        self.process.terminate(); self.process.wait(timeout=3); self.process.stderr.close()
        self.start_server()
        with self.request("/api/v1/media", headers=auth) as response:
            self.assertEqual(json.load(response)["files"][0]["id"], item["id"])


if __name__ == "__main__": unittest.main()

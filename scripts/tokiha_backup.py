#!/usr/bin/env python3
"""Download new TOKIHA originals into capture-date folders."""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, sys
from datetime import datetime
from pathlib import Path
from urllib.parse import urljoin
from urllib.request import Request, urlopen

CONFIG = Path.home() / ".config" / "tokiha" / "backup.json"

def load_json(path: Path, default):
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError): return default

def api_json(url: str, token: str):
    with urlopen(Request(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"}), timeout=60) as response:
        return json.load(response)

def safe_name(value: str) -> str:
    return "".join("_" if c in '\\/:*?\"<>|\0' else c for c in value).strip() or "media"

def sync(config_path: Path = CONFIG) -> int:
    config = load_json(config_path, {})
    missing = [key for key in ("server_url", "token", "destination") if not config.get(key)]
    if missing: raise SystemExit(f"設定がありません: {', '.join(missing)}")
    root = Path(config["destination"]).expanduser(); root.mkdir(parents=True, exist_ok=True)
    state_path = root / ".tokiha-state.json"; state = load_json(state_path, {"files": {}})
    manifest = api_json(urljoin(config["server_url"].rstrip("/")+"/", "api/v1/backup/manifest"), config["token"])
    downloaded = 0
    for item in manifest.get("files", []):
        if state["files"].get(item["id"]) == item.get("sha256"): continue
        captured = datetime.fromisoformat(item["captured_at"].replace("Z", "+00:00"))
        folder = root / f"{captured.year:04d}" / f"{captured.month:02d}"; folder.mkdir(parents=True, exist_ok=True)
        target = folder / safe_name(item["filename"]); temporary = target.with_suffix(target.suffix+".part")
        request = Request(urljoin(config["server_url"], item["download_url"]), headers={"Authorization": f"Bearer {config['token']}"})
        digest = hashlib.sha256()
        with urlopen(request, timeout=300) as response, temporary.open("wb") as output:
            while chunk := response.read(1024*1024): output.write(chunk); digest.update(chunk)
        if item.get("sha256") and digest.hexdigest() != item["sha256"]: temporary.unlink(missing_ok=True); raise RuntimeError(f"整合性エラー: {item['filename']}")
        temporary.replace(target); state["files"][item["id"]] = item.get("sha256") or digest.hexdigest(); downloaded += 1
    state["last_synced_at"] = datetime.now().astimezone().isoformat()
    state_path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"TOKIHA: {downloaded}件をバックアップしました → {root}")
    return downloaded

def main():
    parser=argparse.ArgumentParser(description="TOKIHA PC自動バックアップ")
    parser.add_argument("--config", type=Path, default=CONFIG); args=parser.parse_args(); sync(args.config)
if __name__ == "__main__": main()

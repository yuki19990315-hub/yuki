#!/usr/bin/env python3
"""Install the TOKIHA backup client to run when this user signs in."""
from __future__ import annotations
import argparse, json, os, shutil, stat, sys
from pathlib import Path

APP_DIR = Path.home() / ".local" / "share" / "tokiha-backup"
CONFIG = Path.home() / ".config" / "tokiha" / "backup.json"

def install(server_url: str, token: str, destination: str):
    APP_DIR.mkdir(parents=True, exist_ok=True); CONFIG.parent.mkdir(parents=True, exist_ok=True)
    client=APP_DIR/"tokiha_backup.py"; shutil.copy2(Path(__file__).with_name("tokiha_backup.py"), client)
    CONFIG.write_text(json.dumps({"server_url":server_url,"token":token,"destination":str(Path(destination).expanduser())},ensure_ascii=False,indent=2),encoding="utf-8")
    try: CONFIG.chmod(stat.S_IRUSR|stat.S_IWUSR)
    except OSError: pass
    command=f'"{sys.executable}" "{client}"'
    if sys.platform == "win32":
        startup=Path(os.environ["APPDATA"])/"Microsoft"/"Windows"/"Start Menu"/"Programs"/"Startup"; startup.mkdir(parents=True,exist_ok=True)
        (startup/"TOKIHA Backup.cmd").write_text(f"@echo off\r\n{command}\r\n",encoding="utf-8")
    elif sys.platform == "darwin":
        launch=Path.home()/"Library"/"LaunchAgents";launch.mkdir(parents=True,exist_ok=True)
        (launch/"jp.tokiha.backup.plist").write_text(f'''<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>Label</key><string>jp.tokiha.backup</string><key>ProgramArguments</key><array><string>{sys.executable}</string><string>{client}</string></array><key>RunAtLoad</key><true/></dict></plist>''',encoding="utf-8")
    else:
        auto=Path.home()/".config"/"autostart";auto.mkdir(parents=True,exist_ok=True)
        (auto/"tokiha-backup.desktop").write_text(f"[Desktop Entry]\nType=Application\nName=TOKIHA Backup\nExec={command}\nX-GNOME-Autostart-enabled=true\n",encoding="utf-8")
    print("設定完了。次回PCログイン時から新しい原本を自動保存します。")

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--server-url",required=True);parser.add_argument("--token",required=True);parser.add_argument("--destination",required=True);args=parser.parse_args()
    install(args.server_url,args.token,args.destination)
if __name__ == "__main__": main()

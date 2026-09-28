# TOKIHA — 家族の写真・動画共有アプリ

Android・iPhone・PCのブラウザから同じアルバムを見られる、インストール不要の写真共有アプリのプロトタイプです。

## 起動

```bash
python3 -m http.server 4173
```

`http://localhost:4173` を開いてください。写真・動画の追加、撮影月ごとの閲覧、プレビュー、ダウンロードを試せます。追加したファイルはこのデモを開いている間だけ保持されます。「今すぐPCに保存」から、追加した原本と一覧情報を1つの `.tar` ファイルとしてPCへ保存できます。

`scripts/install_tokiha_backup.py` は、本番サーバー公開後にPCログイン時の自動バックアップを設定するクライアントです。詳しくは [`docs/architecture.md`](docs/architecture.md#pc自動バックアップ) を参照してください。

## 本番化のおすすめ構成

詳しい比較と実装ロードマップは [`docs/architecture.md`](docs/architecture.md) を参照してください。最短構成は次の通りです。

- **画面:** このPWAをNext.jsまたはFlutterへ移植
- **ログイン・DB・ストレージ:** Supabase
- **動画:** Cloudflare Stream（動画が増えてから追加）
- **公開:** Vercel / Cloudflare Pages

## ファイル構成

- `index.html` — アプリ画面
- `guide.html` — iPhone / Android / Windows / Mac別の使い方説明書
- `styles.css` — モバイルファーストのUI
- `app.js` — アップロード、閲覧、保存、ダウンロード
- `manifest.webmanifest` — ホーム画面追加用PWA設定
- `docs/architecture.md` — 本番構成案・費用感・安全設計
- `docs/usability-audit.md` — 仮想操作テストで見つけた問題と修正
- `docs/device-guide.md` — 配布・印刷用の端末別手順書
- `scripts/` — Windows / macOS / Linux用の自動バックアップクライアント

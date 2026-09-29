# TOKIHA — 家族の写真・動画共有アプリ

Android・iPhone・PCのブラウザから同じアルバムを見られる、家族向け写真・動画共有サーバーです。原本と一覧はサーバーの永続ボリュームに保存され、端末を変えたり再起動したりしても残ります。

## ローカルで試す

```bash
TOKIHA_PASSWORD='16文字以上のテスト用パスワード' TOKIHA_SECURE_COOKIE=0 python3 server.py
```

`http://127.0.0.1:8000` を開いてください。データは `./data` に保存されます。ブラウザに共有パスワードを入力し、写真・動画の追加、月別閲覧、ダウンロードを試せます。静的ファイルサーバーの `python -m http.server` ではAPIが動きません。

## ドメイン・VPSなしで公開する（Render）

`render.yaml` を使うと、RenderがHTTPSの `onrender.com` アドレスを発行し、10GBの永続ディスクを `/data` に接続します。写真とSQLiteはそこへ保存されます。これは**有料**構成です。2026年9月時点の公式価格では小型Webサービスが月$7、永続ディスクが月$0.25/GBのため、10GBなら基本部分は月約$9.50です。転送量などの追加料金と決済時の表示を確認してください。無料Webサービスには永続ディスクを付けられません。

1. [Render](https://render.com/)でアカウントを作り、このGitHubリポジトリへのアクセスを許可します。
2. **New → Blueprint**で `yuki19990315-hub/yuki` の **`codex-sigbbj` ブランチ**を選び、`render.yaml` を読み込みます。料金と10GBディスクの内容を確認します。
3. `TOKIHA_PASSWORD` と `TOKIHA_BACKUP_TOKEN` に、**異なる**長いランダム値を入力します。PCで `python3 -c 'import secrets; print(secrets.token_hex(32))'` を2回実行すれば作れます。値をチャットやGitHubに貼らないでください。
4. デプロイ後、Renderが表示する `https://...onrender.com` にアクセスし、ログインして写真を1枚追加します。別端末で見えることを確認します。
5. 日常のバックアップ先として、PC自動バックアップを設定します。Renderのディスクスナップショットだけに頼らず、別の場所へ原本を残してください。

Render設定は実サービスでの起動をまだ検証していません。設定が通らない場合は、Renderのデプロイログを確認して修正します。将来PRを `main` へマージしたら、Renderの連携ブランチも `main` に変更できます。

## 自分のサーバーへ公開する

Docker Composeを実行できる常時稼働のサーバー、独自ドメイン、DNSの設定が必要です。サーバーの80/443番ポートを開けてください。CaddyがHTTPS証明書を自動取得します。

1. このブランチをサーバーへ配置し、`cp .env.example .env` を実行します。
2. `TOKIHA_DOMAIN` にそのサーバーを指すドメインを指定します。
3. `TOKIHA_PASSWORD` と `TOKIHA_BACKUP_TOKEN` に、**それぞれ別の**長いランダム値を設定します。`python3 -c 'import secrets; print(secrets.token_hex(32))'` を2回実行すると生成できます。`.env` を共有・コミットしないでください。
4. `chmod 600 .env` の後、`docker compose up -d --build` を実行します。
5. `https://設定したドメイン/` を開き、ログイン・写真の追加・別端末での表示を確認します。

`tokiha_data` ボリュームにはSQLite DBと原本が入ります。コンテナ更新後も残りますが、`docker compose down -v` やサーバー自体の故障では失われる可能性があります。**家族の写真を入れる前に**、別のディスクやクラウドへこのボリューム全体の定期バックアップを設定し、復元を一度確認してください。Web画面の「今すぐPCに保存」では全原本とJSONを `.tar` で取得できます。実行中のSQLiteファイルを単純コピーせず、停止した状態でボリュームを保存するかSQLiteのオンラインバックアップ機能を使ってください。

ログインは現段階で家族共通のパスワードです。個人別アカウント、招待、削除、容量上限、動画変換、複数台への冗長化は未実装です。まず少人数の私的利用向けとして、これらが必要な場合は [`docs/architecture.md`](docs/architecture.md) の次段階を実装してください。1ファイルの上限は200MBです。JPEGのEXIF日時を読めない写真・動画はファイル更新日時で表示します。

## PCへ自動バックアップ

古いデスクトップを受け皿にする場合、メインPCが停止している間も古いPCがアップロードを受けます。メインPCにログインした時、読み取り専用トークンで新規・更新原本を撮影年/月別のフォルダへ取り込み、SHA-256を検証します。現時点の自動実行は**OS起動時ではなくユーザーのログイン時に1回**です。サーバーに接続できなかった場合は、次回ログイン時まで自動再試行しません。

同期後も古いPC側の原本は保持します。古いPCを家族が閲覧するための保存先、メインPCを別の保存先として使う方針です。メインPCへの同期は旧PCの原本を削除しません。メインPCにこのリポジトリの `scripts/` を配置し、次を一度実行します。

```bash
python3 scripts/install_tokiha_backup.py --server-url https://photos.example.com \
  --destination ~/Pictures/TOKIHA
```

プロンプトで `TOKIHA_BACKUP_TOKEN` の値を入力します。設定は利用者の `~/.config/tokiha/backup.json` に保存されます。トークンは写真の取得専用で、アップロード・日付変更には使用できません。

## ファイル構成

- `index.html` — アプリ画面
- `guide.html` — iPhone / Android / Windows / Mac別の使い方説明書
- `styles.css` — モバイルファーストのUI
- `app.js` — サーバーAPI経由のアップロード、閲覧、保存、ダウンロード
- `server.py` — ログイン、SQLite、原本の永続保存、バックアップAPI
- `Dockerfile` / `compose.yaml` / `Caddyfile` — HTTPS公開と永続ボリューム
- `render.yaml` — VPS・独自ドメインなしでRenderへ配置する構成
- `manifest.webmanifest` — ホーム画面追加用PWA設定
- `docs/architecture.md` — 将来的な複数アカウント・大規模運用の構成案
- `docs/usability-audit.md` — 仮想操作テストで見つけた問題と修正
- `docs/device-guide.md` — 配布・印刷用の端末別手順書
- `scripts/` — Windows / macOS / Linux用の自動バックアップクライアント

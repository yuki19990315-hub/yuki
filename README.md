# Yuki Glyph Toy

Nothing Phone (4a) Pro の背面 Glyph Matrix を触るための、最小構成の Android アプリ雛形です。実機が届く前でも Android Studio で画面や Manifest を確認でき、実機が届いたら公式 Glyph Matrix SDK の AAR を `app/libs/` に追加して試せる構成にしています。

## できること

- Phone (4a) Pro 向けの AOD 対応 Glyph Toy Service を登録します。
- AOD イベントを受けたら 13×13 の Glyph Matrix に時刻または保存済み画像を描画します。
- アプリ画面から画像を取り込み、13×13 の白黒ドットに変換して Glyph Matrix へ送信できます。
- GIF用に複数コマを保存し、現在のコマの後ろに前コマを薄く透かして確認できます。
- 取り込んだ画像は保存され、AOD Toy 側とホーム画面ウィジェット側の両方に表示できます。
- 背面表示はデフォルトでスリープ開始から 15 分で消灯します。アプリ画面から 1 / 5 / 10 / 15 / 30 / 60 / 120 分、または常時表示に変更できます。
- 夜間消灯時間を設定できます。デフォルトは 23:00〜07:00 消灯です。
- アプリ画面から Glyph Toys 管理画面へ誘導します。
- SDK のクラスはリフレクションで呼ぶため、AAR 未配置でもプロジェクトを開けます。

## APK にして直接入れる場合

できます。実機に毎回 Android Studio から Run しなくても、`app-debug.apk` を作って USB/adb/Files アプリ経由でそのままインストールできます。ただし、Glyph Matrix を実際に動かすには APK を作る前に公式 SDK AAR を `app/libs/` に入れておく必要があります。AAR なしでも APK 自体は作れる構成ですが、背面表示の SDK 呼び出しは失敗します。

### PC で APK を作る

```bash
gradle :app:assembleDebug
```

生成物は通常ここにできます。

```text
app/build/outputs/apk/debug/app-debug.apk
```

### adb でそのまま入れる

Phone の USB debugging をオンにして PC に接続したら、次で上書きインストールできます。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

端末が見えない場合は次を確認します。

```bash
adb devices
```

`unauthorized` と出る場合は、Phone 側の USB debugging 許可ダイアログを承認し直してください。

### APK ファイルを Phone にコピーして入れる

`app-debug.apk` を Phone にコピーして、Phone 側の Files アプリなどから開いてインストールすることもできます。その場合は、表示される案内に従って「この提供元のアプリを許可」などの不明なアプリのインストール許可をオンにします。

### 人に配る場合

`app-debug.apk` は開発用の debug 署名です。自分の端末で試すだけなら十分ですが、人に渡すなら release 署名した APK/AAB を作るのが安全です。

## Nothing Phone への入れ方

1. Nothing 公式の Glyph Matrix Developer Kit から `glyph-matrix-sdk-*.aar` を取得し、`app/libs/` にコピーします。
2. Android Studio でこのリポジトリを開き、Gradle Sync を実行します。
3. Phone 側で **Settings > About phone** からビルド番号を複数回タップして Developer options を有効化し、**USB debugging** をオンにします。
4. USB で PC と Phone を接続し、Phone 側に出る USB debugging の許可ダイアログを承認します。
5. AOD Toy の検証をしやすくするため、必要なら PC から次を実行します。

   ```bash
   adb shell settings put global nt_glyph_interface_debug_enable 1
   ```

6. Android Studio の Run ボタンで `app` を Phone にインストールします。CLI なら Android SDK が入っている環境で次を実行します。

   ```bash
   gradle :app:installDebug
   ```

7. Phone でアプリを開いて画像を取り込みます。背面へすぐ試すなら「選択中のコマを背面に表示」を押します。
8. 背面表示を消すまでの時間は「スリープ時間を短く」「スリープ時間を長く」で変更できます。今すぐ設定時間を数え直す場合は「今からスリープ時間を数え直す」を押します。
9. AOD Toy として使う場合は、Phone 側で **Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy** を開き、`Yuki Clock` を有効化します。
10. ホーム画面にも同じ画像を置きたい場合は、ホーム画面長押しからウィジェット一覧を開き、`Yuki Matrix` を追加します。

うまく入らないときは、まず `adb devices` で端末が `device` として見えているか確認してください。`unauthorized` の場合は Phone 側の USB debugging 許可をやり直します。

## 実機が届いたらやること

1. Nothing 公式の Glyph Matrix Developer Kit から `glyph-matrix-sdk-2.0.aar` など最新の AAR を取得します。
2. 取得した AAR を `app/libs/` にコピーします。
3. Android Studio で同期して、Phone (4a) Pro にインストールします。
4. 必要ならデバッグ用に次を実行します。

   ```bash
   adb shell settings put global nt_glyph_interface_debug_enable 1
   ```

5. 端末で **Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy** を開き、`Yuki Clock` を有効化します。
6. ホーム画面に `Yuki Matrix` ウィジェットを追加すると、取り込んだ 13×13 白黒画像をホーム画面にも表示できます。

## バッテリーについて

背面ディスプレイの常時点灯は、表示時間が長いほどバッテリーを消費します。実機での消費量は輝度・点灯ピクセル数・Nothing OS 側の制御に依存しますが、この雛形ではスリープ開始から 15 分で消灯する設定をデフォルトにしています。必要ならアプリ画面の「スリープ時間を短く」「スリープ時間を長く」で 1 分、5 分、10 分、15 分、30 分、60 分、120 分、常時表示へ変更できます。

AOD イベントを受けた時点で消灯期限を保存するため、サービスが再接続されても設定した残り時間を引き継ぎます。夜間消灯時間内はスリープ時間より優先して消灯します。

## 注意

Phone (4a) Pro は Phone (3) と違い、Glyph Touch がなく AOD Toy のみ対応する想定です。この雛形も `com.nothing.glyph.toy.aod_support=1` の AOD Toy として登録しています。画像は中央を正方形にクロップして 13×13 に縮小し、各セルの輝度から白黒二値化します。

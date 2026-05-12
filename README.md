# Yuki Glyph Toy

Nothing Phone (4a) Pro の背面 Glyph Matrix を触るための、最小構成の Android アプリ雛形です。実機が届く前でも Android Studio で画面や Manifest を確認でき、実機が届いたら公式 Glyph Matrix SDK の AAR を `app/libs/` に追加して試せる構成にしています。

## できること

- Phone (4a) Pro 向けの AOD 対応 Glyph Toy Service を登録します。
- AOD イベントを受けたら 13×13 の Glyph Matrix に時刻または保存済み画像を描画します。
- アプリ画面から画像を取り込み、13×13 の白黒ドットに変換して Glyph Matrix へ送信できます。
- 取り込んだ画像は保存され、AOD Toy 側とホーム画面ウィジェット側の両方に表示できます。
- 背面表示はバッテリー保護のため、デフォルトでスリープ後約 1 分だけ表示します。
- 夜間消灯時間を設定できます。デフォルトは 23:00〜07:00 消灯です。
- アプリ画面から Glyph Toys 管理画面へ誘導します。
- SDK のクラスはリフレクションで呼ぶため、AAR 未配置でもプロジェクトを開けます。


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

7. Phone でアプリを開いて画像を取り込みます。背面へすぐ試すなら「選んだ画像を背面に表示」を押します。
8. AOD Toy として使う場合は、Phone 側で **Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy** を開き、`Yuki Clock` を有効化します。
9. ホーム画面にも同じ画像を置きたい場合は、ホーム画面長押しからウィジェット一覧を開き、`Yuki Matrix` を追加します。

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

背面ディスプレイの常時点灯は、表示時間が長いほどバッテリーを消費します。実機での消費量は輝度・点灯ピクセル数・Nothing OS 側の制御に依存しますが、この雛形では安全側に倒して「スリープ後約 1 分」表示をデフォルトにしています。必要ならアプリ画面の「表示時間を切り替え」で 5 分、15 分、60 分、常時表示へ変更できます。

## 注意

Phone (4a) Pro は Phone (3) と違い、Glyph Touch がなく AOD Toy のみ対応する想定です。この雛形も `com.nothing.glyph.toy.aod_support=1` の AOD Toy として登録しています。画像は中央を正方形にクロップして 13×13 に縮小し、各セルの輝度から白黒二値化します。

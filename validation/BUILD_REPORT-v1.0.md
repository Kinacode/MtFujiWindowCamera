# ビルド検証結果

2026-11-07 / Linux x86_64 / JDK 17 / Gradle 8.11.1 / Android Gradle Plugin 8.9.1 / Android SDK 35

- `assembleDebug`: 成功
- `lintDebug`: 成功（エラー0、警告9。依存ライブラリの新バージョン通知7件、横向き固定1件、旧OS用バックアップ設定の補足1件。旧OSでもallowBackup=false。）
- `assembleDebugAndroidTest`: 成功
- `apksigner verify`: 成功（APK Signature Scheme v2）
- `zipalign -c -P 16 4 app-debug.apk`: 成功
- パッケージ: `jp.fujiwindow.camera`
- Android: 10以上（API 29以上）、target API 35
- APKはdebug署名済み。Android端末へ直接インストール可能。
- カメラ撮影と自然な仕上がりは、実際の窓・端末での確認が必要です。

APK生成先: `app/build/outputs/apk/debug/app-debug.apk`

APK SHA-256: `ae15338abfa33c5b1c56753879b31ff5771e4f9d4c236b0a889deb5a480b4b53`

GitHub側へは接続権限がなく変更していません。利用者の指定に従いソースZIPを提供しています。`.github/workflows/android.yml`を含むソースをリポジトリに配置すれば、pushまたは手動実行でAPKを再生成できます。

## Androidエミュレーターへのインストール

API 29 / x86_64 のAndroid 10仮想端末へ `adb install -r app-debug.apk` を実行し、`Success`を確認済みです。テストAPKもインストールに成功しました。

## Android計装テスト

API 29 / x86_64 上の `AndroidJUnitRunner` 実行結果: **OK (4 tests)**（テスト本体10.257秒）。

- マスク外のデコード済み画素と元Bitmapを完全に保持し、加工範囲でだけ画素が変わること
- 富士山の保護範囲と加工範囲が重なると処理を拒否すること
- 保護範囲未登録で処理を拒否すること
- GPS・機種名を持つJPEGを再エンコードした書き出しからGPS・機種名が消え、元JPEGは保持されること

テストログは配布フォルダの `instrumentation-tests.txt` を参照してください。

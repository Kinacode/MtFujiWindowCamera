# v1.1 検証結果

写真読み込み（Android OpenDocument）と、保存済み写真一覧「富士日記」を追加しました。
カメラ権限は撮影を選んだときに要求し、読み込み・日記閲覧には不要です。

今回の検証:

- JDK 17 / Gradle 8.11.1 / AGP 8.9.1 / Android SDK 35
- assembleDebug / lintDebug / assembleDebugAndroidTest：すべて成功（validation/build-v1.1.log参照）
- APK署名・zipalign検証：成功（旧版と証明書SHA-256一致）
- versionCode 2 / versionName 1.1

今回追加した写真選択画面と日記UIの実機操作テストは未実施です。
画像処理本体は変更していません。v1.0でのAndroid 10インストール・画像処理4テストの結果はvalidation/BUILD_REPORT-v1.0.mdに保存しています。これらをv1.1で再実行したという意味ではありません。

前回と同じdebug証明書で署名します。前回APKからの更新時に保存した写真や設定を引き継げます。
GitHubへは反映せず、更新APKとソースZIPとして提供します。

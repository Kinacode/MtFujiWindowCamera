# v1.2 検証結果

EXIF DateTimeOriginalを削除前に取得し、撮影日時をアプリ専用のdiary_datesに保存します。元画像・加工画像で日時を共有し、日記を撮影日時順に表示します。GPS等の公開JPEGメタデータ削除は維持します。過去版でEXIF削除済みの画像は保存日時であると明示して表示します。

- assembleDebug：成功
- lintDebug：成功（エラー0、警告9）
- testDebugUnitTest：成功（日時処理4テスト、失敗0）
- apksigner verify / zipalign：成功
- versionCode 3 / versionName 1.2
- 旧版と署名証明書SHA-256一致。上書き更新可能。

テストは日時の時差変換・元の現地日時表示・不正日付拒否・時差なし・保存日フォールバックの表示を検証しています。今回の実機UI操作およびAndroid計装テストは未実施です。過去版の結果はvalidationのバージョン別レポートを参照してください。

ソースはKinacode/MtFujiWindowCameraへ反映。ローカルで検証済みのv1.2 APKをGitHub Releasesへ添付します。

# v1.6 検証内容

標準モードをMI-GANによる本当のニューラルインペイントへ変更。ONNX Runtime Android 1.31.1を使用し、モデルはMITライセンスの公式配布物をSHA-256で固定しています。推論はCPU・端末内で、通信権限は除去しています。

- ローカル assembleDebug / lintDebug / testDebugUnitTest / assembleDebugAndroidTest：成功
- Linux上の公式モデル実推論：uint8 NCHW入出力を確認。黒線を青空で補完し、既知領域の完全一致を確認。
- APKの権限：CAMERAとアプリ固有の内部権限のみ。INTERNET／ACCESS_NETWORK_STATEなし。
- Androidで実モデルによる黒線除去・範囲外／元Bitmapの画素保持・中止動作を実行します。UIの標準加工経路も実際のモデルを呼び出します。
- 既存の固定窓・補完ON/OFF・日記UIテストも維持。

今回の実写添付ファイルでの品質確認は未実施です。ニューラル補完は推定であり、山の本来の模様・輪郭や参考生成AIと同じ結果を保証するものではありません。元写真は上書きせず保持します。
CIの署名鍵が旧版と異なる場合は更新できず、アンインストールはアプリ内部の日記情報を消去します。

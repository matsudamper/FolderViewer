# Paparazzi

## 共通ルール

### UI 変更時
- `@Preview` を追加/更新しスナップショットを撮影する
- 撮影結果はセッションのチャットに貼る
- 画像なしで UI 作業完了にしない

## リポジトリ固有ルール

### 画像の扱い
- Paparazzi の参照画像（`ui/src/test/snapshots/images/` と `textviewer/src/test/snapshots/images/`）は Git で管理する。UI 変更に伴って内容が変わった参照画像は更新してコミットする
- セッションのチャットへの共有用に撮影した画像は、参照画像とは区別し、コミットしない

### 実行
```shell
./gradlew :ui:recordPaparazziDebug
./gradlew :ui:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
./gradlew :textviewer:recordPaparazziDebug
./gradlew :textviewer:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
```

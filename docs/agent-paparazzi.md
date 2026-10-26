# Paparazzi

## 共通ルール

### UI 変更時
- `@Preview` を追加/更新しスナップショットを撮影する
- 撮影結果はセッションのチャットに貼る
- 画像なしで UI 作業完了にしない

## リポジトリ固有ルール

### 画像の扱い
- Paparazzi の出力先（`ui/src/test/snapshots/images/` と `textviewer/src/test/snapshots/images/`）の画像は Git 管理しない。撮影結果はセッションのチャットで共有する

### 実行
```shell
./gradlew :ui:recordPaparazziDebug
./gradlew :ui:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
./gradlew :textviewer:recordPaparazziDebug
./gradlew :textviewer:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
```

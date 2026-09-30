# Paparazzi

## UI 変更時
- `@Preview` を追加/更新しスナップショットを撮影する
- スナップショット画像はコミットしない。PR とチャットに貼る
- 画像なしで UI 作業完了にしない

## 実行
```shell
./gradlew :ui:recordPaparazziDebug
./gradlew :ui:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
```

スナップショットは `ui/src/test/snapshots/images/`。

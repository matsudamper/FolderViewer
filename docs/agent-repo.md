# FolderViewer 固有ルール

## このアプリについて
Androidのファイルビューアです。

## 構成
- `/app` — Androidのappモジュール。アプリのrootとNavigation周り
- `/ui` — UI Composable。各画面のUI実装
- `viewmodel` — ViewModel。UIとはUiStateやEventを通してやり取り
- `/repository` — 抽象化可能なデータの保存・取得
- `/common` — 共通コード
- `/navigation` — Navigation定義（Navigation3）
- `/dao/*` — 各サービスへのアクセス

## Compose / UI
- Composeの記述は `docs/compose-guidelines.md` に従う
- private/internalをできる限り使用し、最小限のアクセスに絞る
- UI操作をViewModelで受けるときはUiState内のCallbacksを経由する。ViewModelにpublicな関数を追加しない

## Paparazzi
```shell
./gradlew :ui:recordPaparazziDebug
./gradlew :ui:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
```
スナップショットは `ui/src/test/snapshots/images/`。
UI変更時は `@Preview` を追加/更新しスナップショットを撮影する。
1. 撮影した画像はコミットしない。PRに貼る
2. チャットにも貼り、画像なしでUI作業完了にしない

## ビルド
```shell
./gradlew assembleDebug ktlintCheck detekt
./gradlew ktlintFormat
```

## その他
- `@Suppress` の使用は禁止
- コードのコメントは禁止。複雑な関数のドキュメントコメントだけ許可

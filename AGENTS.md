# このアプリについて
Androidのファイルビューアです。

# 構成
- /app
  - Androidのappモジュール。アプリのrootとNavigation周りを行っている。
- /ui
  - UI Composableが入っているモジュール。各画面のUI実装がある。
- viewmodel
  - ViewModelが入っているモジュール。UIとはUiStateやEventを通してやり取りする
- /repository
  - 抽象化可能なデータの保存、取得が入っているモジュール
- /common
  - 共通で使用されるコードが入っているモジュール
- /navigation
  - Navigationの定義が入っている。NavigationにはNavigation3が使用されている
- /dao/*
  - 具体的な各サービスへのアクセスするためのコードが入っている

# 指示
コードの編集が完了した後にビルドを行い、ビルドが通るかを確認してください。

# コーディングルール
コメントやコミットメッセージ、PRはすべて日本語で記述する。

Composeの記述は以下のルールに従う。
@docs/compose-guidelines.md

private/internalをできる限り使用し、最小限のアクセスに絞ること。

@Suppressの使用は禁止

コードのコメントは禁止。複雑な関数のドキュメントコメントだけ許可する。

# Agent固有のルール
指示されていない部分は差分が少なくなるようにする。

ユーザーからの質問に対して、コードの実行や変更を行わずに回答のみを行うように振る舞うこと。

# 設計
UI操作をViewModelで受けるときはUiState内のCallbacksを経由して行う。ViewModelにpublicな関数を追加しない。  

# ビルドコマンド
編集後のチェックコマンド
```shell
./gradlew assembleDebug ktlintCheck detekt
```

フォーマット用コマンド
```shell
./gradlew ktlintFormat
```

# Paparazzi スクリーンショットテスト
```shell
# 全 Preview のスナップショット記録
./gradlew :ui:recordPaparazziDebug

# 特定 Preview のスナップショット記録
./gradlew :ui:recordPaparazziDebug -Dpaparazzi.filter="PreviewName"
```
スナップショットは `ui/src/test/snapshots/images/` に保存される。

## UI 変更時のルール
UI 変更を含む場合は `@Preview` を追加/更新し、Paparazzi スナップショットを撮影する。
1. 撮影した画像はコミットしない。PRに貼り付ける
2. 撮影した画像は必ずチャットにも貼り付けて（送付して）ユーザーが見た目を確認できるようにする。UI 変更の報告に画像添付がない状態で作業を完了しない

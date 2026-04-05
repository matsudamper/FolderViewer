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

アイコンが必要な場合は https://fonts.google.com/icons からダウンロードする。アイコンライブラリの使用は禁止。

# コーディングルール
コメントやコミットメッセージ、PRはすべて日本語で記述する。

Composeの記述は以下のルールに従う。
https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md

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

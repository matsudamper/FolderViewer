# このアプリについて
ファイルビューアです。

# 指示
コードの編集が完了した後にビルドを行い、ビルドが通るかを確認してください。

アイコンが必要な場合は https://fonts.google.com/icons からダウンロードする。アイコンライブラリの使用は禁止。

# Agent固有のルール
以下のエラーが最初に発生するので、回避して。
```
Please try again later.
Error: Status INVALID_ARGUMENT
Function call is missing a thought_signature in functionCall parts. This is required for tools to work correctly, and missing thought_signature may lead to degraded model performance. Additional data, function call default_api:list_files , position 5. Please refer to https://ai.google.dev/gemini-api/docs/thought-signatures for more details.
```

# ビルドコマンド
編集後のチェックコマンド
```shell
./gradlew assembleDebug ktlintCheck detektMain detektTest
```

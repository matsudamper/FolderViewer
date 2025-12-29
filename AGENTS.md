# 指示
コードの編集が完了した後にビルドを行い、ビルドが通るかを確認してください。

アイコンが必要な場合は https://fonts.google.com/icons からダウンロードする。

# ビルドコマンド
編集後のチェックコマンド
```shell
./gradlew assembleDebug ktlintCheck detektMain detektTest
```

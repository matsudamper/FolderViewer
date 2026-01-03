# GitHub Actions 変数権限調査

このドキュメントは、GitHub Actions実行エラーの調査結果をまとめたものです。

## 調査対象

- エラーURL: https://github.com/matsudamper/FolderViewer/actions/runs/20682553285/job/59378710410
- エラー内容: `gh variable set` コマンドで "Resource not accessible by integration" エラー

## 調査結果

詳細な調査結果は [github-actions-variable-permissions.md](./github-actions-variable-permissions.md) を参照してください。

## 結論

**問題**: デフォルトの `GITHUB_TOKEN` では Environment 変数を設定できません。

**解決策**:

1. **Fine-grained Personal Access Token (PAT)** を使用する
   - 必要な権限: `Actions variables: Read and write`
   - 実装が簡単だが、個人アカウントに依存

2. **GitHub App Token** を使用する（推奨）
   - 必要な権限: `Variables: Read and write`
   - より安全で長期的なソリューション
   - 初期設定がやや複雑

## 次のステップ

1. 上記の解決策のいずれかを選択
2. 必要な権限を持つトークンを作成
3. ワークフローを更新してトークンを使用
4. 動作確認

詳しい手順は [github-actions-variable-permissions.md](./github-actions-variable-permissions.md) を参照してください。

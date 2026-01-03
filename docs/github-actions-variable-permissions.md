# GitHub Actions 変数の権限調査結果

## 問題の概要

GitHub Actions実行でエラーが発生しました: https://github.com/matsudamper/FolderViewer/actions/runs/20682553285/job/59378710410

ワークフロー `.github/workflows/release.yml` の「Increment VERSION」ステップで以下のコマンドが失敗しました:

```bash
gh variable set VERSION -b "$NEW_VERSION" --env Release
```

エラーメッセージ: "Resource not accessible by integration"

## 原因

デフォルトの `GITHUB_TOKEN` には、リポジトリやEnvironmentの変数を設定する権限がありません。

### GITHUB_TOKENの制限

- `GITHUB_TOKEN` は自動的に各ワークフローで生成される一時的なトークンです
- セキュリティ上の理由から、管理者レベルの権限(`admin:repo`)を持ちません
- `permissions: write-all` を設定しても、リポジトリ変数やEnvironment変数の管理には不十分です
- これは意図的なセキュリティ設計で、ワークフローが安全でない可能性のある管理的変更を行うことを防ぎます

## 解決策

Environment変数を設定するには、以下のいずれかの方法が必要です:

### 解決策1: Fine-grained Personal Access Token (PAT) を使用

#### 必要な権限

Fine-grained PATを作成する際に、以下の権限を付与する必要があります:

- **Actions variables**: Read and write
  - これがEnvironment変数を設定するための主要な権限です

#### 設定手順

1. GitHub > Settings > Developer settings > Personal access tokens > Fine-grained tokens
2. 新しいトークンを作成
3. リポジトリのアクセスを選択:
   - このリポジトリ専用にスコープを絞ることができます（推奨）
4. 権限を設定:
   - Repository permissions > **Actions variables** > **Read and write**
5. トークンを生成してコピー
6. リポジトリの Settings > Secrets and variables > Actions > Secrets にシークレットとして追加
   - 例: `VARIABLES_PAT`

#### ワークフローでの使用

```yaml
- name: Increment VERSION
  env:
    GH_TOKEN: ${{ secrets.VARIABLES_PAT }}
    BEFORE_VERSION: ${{ vars.VERSION }}
  run: |
    # ... 既存のコード
    gh variable set VERSION -b "$NEW_VERSION" --env Release
```

#### メリット

- 実装が簡単
- リポジトリ専用にスコープを絞れる
- 細かい権限制御が可能

#### デメリット

- 個人アカウントに紐づく
- トークンの有効期限管理が必要
- トークンが漏洩するとセキュリティリスクがある

### 解決策2: GitHub App Token を使用（推奨）

#### 概要

GitHub Appを作成し、`actions/create-github-app-token` アクションを使用してトークンを生成します。

#### 必要な権限

GitHub Appを作成する際に、以下の権限を設定します:

- Repository permissions > **Variables** > **Read and write**

#### 設定手順

1. GitHub Settings > Developer settings > GitHub Apps > New GitHub App
2. アプリを作成:
   - App name: 例「FolderViewer Actions」
   - Homepage URL: リポジトリURL
   - Webhook: 無効化
3. 権限を設定:
   - Repository permissions > **Variables** > **Read and write**
4. アプリを作成後:
   - App IDをメモ
   - Private keyを生成してダウンロード
5. アプリをこのリポジトリにインストール
6. リポジトリに設定を追加:
   - Settings > Secrets and variables > Actions
   - Variables に追加: `APP_ID` (App ID)
   - Secrets に追加: `APP_PRIVATE_KEY` (Private keyの内容)

#### ワークフローでの使用

```yaml
- name: Generate GitHub App Token
  id: generate_token
  uses: actions/create-github-app-token@v1
  with:
    app-id: ${{ vars.APP_ID }}
    private-key: ${{ secrets.APP_PRIVATE_KEY }}

- name: Increment VERSION
  env:
    GH_TOKEN: ${{ steps.generate_token.outputs.token }}
    BEFORE_VERSION: ${{ vars.VERSION }}
  run: |
    # ... 既存のコード
    gh variable set VERSION -b "$NEW_VERSION" --env Release
```

#### メリット

- 個人アカウントに依存しない
- トークンは1時間で自動的に期限切れ（セキュリティ向上）
- 細かい権限制御が可能
- 組織やチームで管理しやすい
- より安全な長期的ソリューション

#### デメリット

- 初期設定がやや複雑
- GitHub Appの管理が必要

## 推奨事項

### 短期的な解決策

Fine-grained PATを使用して素早く問題を解決する。

### 長期的な解決策

GitHub App Tokenの使用を検討する。特に以下の場合に推奨:

- チームでリポジトリを管理している
- 複数のリポジトリで同じ仕組みを使う予定
- よりセキュアな運用を求める場合

## 参考資料

- [GitHub Docs: Variables in Actions](https://docs.github.com/en/actions/concepts/workflows-and-actions/variables)
- [GitHub Docs: Permissions required for fine-grained personal access tokens](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens)
- [GitHub Docs: Using GitHub CLI in workflows](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-github-cli)
- [actions/create-github-app-token](https://github.com/actions/create-github-app-token)
- [Stack Overflow: GitHub actions "Resource not accessible by integration"](https://stackoverflow.com/questions/77882485/github-actions-resource-not-accessible-by-integration)
- [GitHub Community: "gh variable list" leads to error "Resource not accessible by integration"](https://github.com/orgs/community/discussions/84700)

## まとめ

| 方法 | 必要な権限 | メリット | デメリット |
|------|-----------|----------|-----------|
| GITHUB_TOKEN | N/A | 追加設定不要 | **Environment変数の設定不可** |
| Fine-grained PAT | Actions variables: Read and write | 実装が簡単、リポジトリスコープ可能 | 個人アカウント依存、有効期限管理必要 |
| GitHub App Token | Variables: Read and write | アカウント非依存、自動期限切れ、セキュア | 初期設定が複雑 |

**結論**: Environment変数を設定するには、`GITHUB_TOKEN`では不十分です。Fine-grained PATまたはGitHub App Tokenが必要であり、両方とも「Variables」または「Actions variables」の Read and write 権限が必須です。

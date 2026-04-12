# アイコン追加手順

アイコンライブラリの使用は禁止。必ず以下の手順でGoogle FontsからAndroid Vector DrawableのXMLを取得する。

SVGはAndroidで直接使用できないため、XML形式で取得すること。

## 手順

1. https://fonts.google.com/icons?icon.query=<検索キーワード> でアイコンを検索する
2. 使用するアイコン名（例: `home`、`add_a_photo`）を確認する
3. 以下のURLパターンでGitHubから直接XMLをダウンロードする

```
https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/{icon_name}/materialsymbolsoutlined/{icon_name}_24px.xml
```

例（homeアイコンの場合）:
```
https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/home/materialsymbolsoutlined/home_24px.xml
```

4. ダウンロードしたXMLを `res/drawable/ic_<アイコン名>.xml` として配置する

## Composable での使用方法

```kotlin
Icon(
    painter = painterResource(id = R.drawable.ic_<アイコン名>),
    contentDescription = null,
)
```

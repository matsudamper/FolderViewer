# アイコン追加手順

アイコンライブラリの使用は禁止。必ず以下の手順でGoogle FontsからAndroid Vector DrawableのXMLを取得する。

## 手順

1. https://fonts.google.com/icons を開く
2. 使用したいアイコンを検索・選択する
3. 右ペインで **Android** タブを選択する
4. `res/drawable` に配置するXMLファイルをダウンロードする
5. ダウンロードしたXMLを `res/drawable/ic_<アイコン名>.xml` として配置する

## Composable での使用方法

```kotlin
Icon(
    painter = painterResource(id = R.drawable.ic_<アイコン名>),
    contentDescription = null,
)
```

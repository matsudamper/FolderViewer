---
name: icon
description: 指定したアイコン名でGoogle Fontsを検索し、Android Vector DrawableのXMLをres/drawableにダウンロードする
argument-hint: <アイコン名>
allowed-tools: Bash(curl *) Write
---

https://fonts.google.com/icons?icon.query=$ARGUMENTS でアイコンを検索し、アイコン名を確認する。

以下のURLパターンでXMLをダウンロードし、`res/drawable/ic_$ARGUMENTS.xml` に配置する：

```
https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/{icon_name}/materialsymbolsoutlined/{icon_name}_24px.xml
```

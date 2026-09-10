# protobuf-javalite は生成クラスのフィールドを "<フィールド名>_" の文字列で
# リフレクション参照するため、リネームされると起動時に RuntimeException になる
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# azure-core は HttpClient 実装を META-INF/services から ServiceLoader で解決する。
# インタフェース側を残さないと services エントリごと APK から落ちる
-keep class com.azure.core.http.HttpClientProvider
-keep class * implements com.azure.core.http.HttpClientProvider { *; }

# azure-core の ReflectionSerializable は fromJson/toJson をメソッド名で引く
-keepclassmembers class * implements com.azure.json.JsonSerializable {
    public static ** fromJson(com.azure.json.JsonReader);
    public ** toJson(com.azure.json.JsonWriter);
}

# SharePoint 連携の入口。ここを keep しないと R8 が SDK 呼び出しごと
# 到達不能とみなして削除し、release だけ SharePoint が機能しなくなる
-keep class com.azure.identity.ClientSecretCredentialBuilder { *; }
-keep class com.azure.identity.implementation.IdentityClient { *; }
-keep class com.microsoft.graph.serviceclient.GraphServiceClient { *; }
-keep class com.microsoft.graph.core.tasks.LargeFileUploadTask { *; }

# 以下は Android 上に存在しないサーバ向け依存への参照。R8 の Missing class 対策
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.jcraft.jzlib.**
-dontwarn com.nimbusds.jose.util.StandardCharset
-dontwarn com.sun.net.httpserver.**
-dontwarn io.micrometer.**
-dontwarn io.netty.incubator.**
-dontwarn java.awt.Desktop
-dontwarn java.awt.Desktop$Action
-dontwarn java.lang.management.**
-dontwarn java.rmi.UnmarshalException
-dontwarn javax.el.**
-dontwarn javax.naming.directory.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.ietf.jgss.**
-dontwarn reactor.blockhound.**

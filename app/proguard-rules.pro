# リフレクションでジェネリクス・アノテーションを解決するライブラリ(Jackson/Azure SDK)向け
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# protobuf-javalite は生成クラスのフィールドを "<フィールド名>_" の文字列で
# リフレクション参照するため、リネームされると起動時に RuntimeException になる
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# azure-core は HttpClient / JSON の実装を ServiceLoader で解決する。
# インタフェース側も残さないと META-INF/services が APK から落ちる
-keep class com.azure.core.http.HttpClientProvider
-keep class * implements com.azure.core.http.HttpClientProvider { *; }
-keep class com.azure.json.JsonProvider
-keep class * implements com.azure.json.JsonProvider { *; }
# azure-core のモデルは fromJson/toJson をリフレクションで呼び出す
-keepclassmembers class * implements com.azure.json.JsonSerializable {
    public static ** fromJson(com.azure.json.JsonReader);
}
# azure-identity はトークン取得を msal4j + Jackson のリフレクションに依存する
-keep class com.microsoft.aad.msal4j.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}

# SharePoint 連携の入口。ここを keep しないと R8 が SDK 呼び出しごと
# 到達不能とみなして削除し、release だけ SharePoint が機能しなくなる
-keep class com.azure.identity.ClientSecretCredentialBuilder { *; }
-keep class com.microsoft.graph.serviceclient.GraphServiceClient { *; }
-keep class com.microsoft.graph.core.tasks.LargeFileUploadTask { *; }

# Microsoft Graph SDK(Kiota)はシリアライザとバッキングストアを
# ServiceLoader 相当の登録機構で解決する
-keep class * implements com.microsoft.kiota.serialization.ParseNodeFactory { *; }
-keep class * implements com.microsoft.kiota.serialization.SerializationWriterFactory { *; }
-keep class * implements com.microsoft.kiota.store.BackingStoreFactory { *; }

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
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
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

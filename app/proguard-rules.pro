# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

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
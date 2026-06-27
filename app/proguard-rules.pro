# Jsoup
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization keeps generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# CineFlix by MJ — Google TV ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.cineflix.tv.data.model.** { *; }
-keepclassmembers class com.cineflix.tv.data.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

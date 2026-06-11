# EazyCmp ProGuard/R8 rules — add to host app if minification strips EazyCmp APIs:
#   proguardFiles("consumer-rules.pro")  (copy from eazyCmp AAR or this file)

-keep class com.aj.shared.EazyCmp { *; }
-keep class com.aj.shared.api.** { *; }
-keep class com.aj.shared.picker.** { *; }
-keep class com.aj.shared.permission.** { *; }

-keepattributes *Annotation*, InnerClasses
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.android.play.core.**
-dontwarn com.google.zxing.**

# Keep Go-mobile bridge classes (Xray core)
-keep class libv2ray.** { *; }
-keep class go.** { *; }

# Keep hev-socks5-tunnel JNI bridge
-keep class hev.** { *; }

# Keep native method bindings
-keepclasseswithmembernames class * { native <methods>; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.simorgh.vpn.**$$serializer { *; }
-keepclassmembers class com.simorgh.vpn.** { *** Companion; }
-keepclasseswithmembers class com.simorgh.vpn.** { kotlinx.serialization.KSerializer serializer(...); }

# Conspeak ProGuard rules
-keep class com.conspeak.protocol.** { *; }
-keepclassmembers class * extends com.conspeak.protocol.Message { *; }
-dontwarn sun.security.x509.**

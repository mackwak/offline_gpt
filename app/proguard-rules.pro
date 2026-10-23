# By default, the flags in this file are appended to flags specified
# in getDefaultProguardFile('proguard-android-optimize.txt').

-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.tensorflow.lite.InterpreterFactoryApi
-dontwarn org.tensorflow.lite.annotations.UsedByReflection

# Keep LiteRT-LM classes and methods from being obfuscated or removed
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** {
    *;
}

# Keep JNI callback classes and their methods (like onMessage and onDone) used by LiteRtLmJni
-keepclassmembers class com.google.ai.edge.litertlm.LiteRtLmJni* {
    native <methods>;
    <methods>;
}

-keepclassmembers class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    <methods>;
}

-keep class com.google.ai.edge.litertlm.LiteRtLmJni$* { *; }

-keep class com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    *;
}

# Alternatively, if Lyh is the obfuscated name or your specific callback wrapper class:
-keep class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    <methods>;
}
# By default, the flags in this file are appended to flags specified
# in getDefaultProguardFile('proguard-android-optimize.txt').

-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.tensorflow.lite.InterpreterFactoryApi
-dontwarn org.tensorflow.lite.annotations.UsedByReflection

# Keep Protobuf Lite generated message fields and classes accessed reflectively
-keep class com.google.protobuf.** { *; }
-keepclassmembers class com.google.protobuf.** { *; }

-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep MediaPipe classes and fields from obfuscation or removal
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

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
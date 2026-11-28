# By default, the flags in this file are appended to flags specified
# in getDefaultProguardFile('proguard-android-optimize.txt').

-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.tensorflow.lite.InterpreterFactoryApi
-dontwarn org.tensorflow.lite.annotations.UsedByReflection

# Keep Protobuf Lite generated messages and fields accessed reflectively
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep MediaPipe classes and members from stripping or renaming
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep Flogger classes for reflection / caller-finder stack inspection
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# Keep debug and reflection attributes needed for stack traces and signatures
-keepattributes LineNumberTable,SourceFile,EnclosingMethod,InnerClasses,Signature,*Annotation*

# Keep TensorFlow Lite classes and members
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep LiteRT / LiteRT-LM classes and JNI callback implementations
-keep class com.google.ai.edge.litert.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.LiteRtLmJni* {
    native <methods>;
    <methods>;
}
-keep class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    *;
}
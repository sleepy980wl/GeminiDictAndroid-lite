# Keep Gson models and Room entities
-keep class com.geminidict.app.data.remote.** { *; }
-keep class com.geminidict.app.data.local.entity.** { *; }

# Keep Sherpa-ONNX JNI classes and native methods
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.apache.commons.compress.**

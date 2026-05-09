# Add project specific ProGuard rules here.
# sherpa-onnx 和 ONNX Runtime 使用 JNI，需要保留
-keep class com.k2fsa.sherpa.** { *; }
-keep class ai.onnxruntime.** { *; }

# Room 数据库
-keep class com.livespeaker.app.data.** { *; }

# Kotlin 序列化
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }

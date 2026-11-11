-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aura.**$$serializer { *; }
-keepclassmembers class com.aura.** { *** Companion; }
-keepclasseswithmembers class com.aura.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# ONNX runtime reflectively referenced
-keep class ai.onnxruntime.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# Ktor/Netty ship optional transports, codecs and logging backends that Android does not use.
# Silence the missing-class warnings so R8 does not fail the release build.
-dontwarn io.netty.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**
-dontwarn reactor.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.lz4.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-keep class io.ktor.** { *; }

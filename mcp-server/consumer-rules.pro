-keep class com.aura.mcp.** { *; }
-keepclassmembers class com.aura.mcp.** { *; }
-keep @kotlinx.serialization.Serializable class com.aura.mcp.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
-dontwarn io.netty.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**
-dontwarn reactor.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.lz4.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.sun.nio.file.**
-dontwarn java.lang.management.**
-keep class io.ktor.** { *; }

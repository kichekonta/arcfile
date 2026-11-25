# Keep Room database/entity metadata and Kotlin metadata safe under R8
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# Optional codecs & desktop/SLF4J references inside Apache Commons Compress / XZ / Junrar / Zip4j
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**
-dontwarn net.lingala.zip4j.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**
-dontwarn org.osgi.**

# Keep Tukaani XZ classes referenced dynamically by Apache Commons Compress 7z LZMA2
-keep class org.tukaani.xz.** { *; }


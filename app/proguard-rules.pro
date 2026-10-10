# Daytrace R8 rules. Most libraries (Room, WorkManager, Compose, kotlinx.serialization,
# Supabase) ship their own consumer rules; these cover the gaps.

# Ktor references optional JVM classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**

# Keep @Serializable models' generated serializers (belt and braces for nested/private ones).
-keepclassmembers @kotlinx.serialization.Serializable class dev.holdthisthought.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.holdthisthought.**$$serializer { *; }

# ML Kit discovers its components by reflection on their no-arg constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

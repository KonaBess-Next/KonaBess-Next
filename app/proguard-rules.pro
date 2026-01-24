# ProGuard Rules for KonaBess Next (Optimized)

# Setup
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-allowaccessmodification
-repackageclasses ''

# Standard Android Keeps
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# Keep Kotlin Metadata (Valid for R8)
-keep class kotlin.Metadata { *; }

# Serialization (JSON) - Keep Data Models
# Necessary because field names are used in JSON
-keep class com.ireddragonicy.konabessnext.model.** { *; }

# Keep Native Interface (JNI)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# LibSU
-keep class com.topjohnwu.superuser.** { *; }

# Coroutines & Compose - R8 usually handles these, but adding specific safe-guards if needed
# (Removing broad androidx.** keep to allow shrinking)

# Remove Log calls in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

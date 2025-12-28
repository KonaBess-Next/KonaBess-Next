# Add project specific ProGuard rules here.
# Balanced optimization for minimal APK size while maintaining functionality

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-allowaccessmodification
-repackageclasses ''

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# CRITICAL: Keep all Activities (fixes "problem parsing the package" error)
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Application

# Keep all UI components from our package
-keep class com.ireddragonicy.konabessnext.ui.** { *; }
-keep class com.ireddragonicy.konabessnext.KonaBessApplication { *; }

# Keep core classes
-keep class com.ireddragonicy.konabessnext.core.** { *; }
-keep class com.ireddragonicy.konabessnext.model.** { *; }
-keep class com.ireddragonicy.konabessnext.data.** { *; }
-keep class com.ireddragonicy.konabessnext.repository.** { *; }
-keep class com.ireddragonicy.konabessnext.viewmodel.** { *; }
-keep class com.ireddragonicy.konabessnext.editor.** { *; }
-keep class com.ireddragonicy.konabessnext.utils.** { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Remove System.out/err
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# LibSU - Keep required classes
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# AndroidX - keep important components
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Keep native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep custom views
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable
-keepnames class * implements java.io.Serializable

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Remove Kotlin intrinsics checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

# ProGuard rules for HydroApp
# Chaquopy Python runtime – keep all Python interop classes
-keep class com.chaquo.python.** { *; }
-keep class com.chaquo.python.android.** { *; }

# OSMDroid
-keep class org.osmdroid.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Room – keep entity and DAO classes
-keep class com.timflow.hydroapp.data.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.timflow.hydroapp.**$$serializer { *; }
-keepclassmembers class com.timflow.hydroapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.timflow.hydroapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep location data classes
-keep class com.timflow.hydroapp.location.** { *; }

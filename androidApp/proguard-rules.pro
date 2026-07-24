# Add project specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# ---------------------------------------------------------------------------
# General
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep the Application class and any manifest-declared components' constructors.
-keep class com.via.himalaya.ViaHimalayaApplication { *; }

# ---------------------------------------------------------------------------
# kotlinx.serialization
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
# ---------------------------------------------------------------------------
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep generated $serializer classes and serializer() methods for our own
# @Serializable models (they are looked up by the serialization runtime).
-keep,includedescriptorclasses class com.via.himalaya.**$$serializer { *; }
-keepclassmembers class com.via.himalaya.** {
    *** Companion;
}
-keepclasseswithmembers class com.via.himalaya.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.via.himalaya.data.models.** { *; }
-keep class com.via.himalaya.domain.model.** { *; }

# ---------------------------------------------------------------------------
# Room (local database) - defensive, Room already ships consumer rules
# ---------------------------------------------------------------------------
-keep class com.via.himalaya.data.local.** { *; }
-dontwarn androidx.room.**

# ---------------------------------------------------------------------------
# Ktor
# ---------------------------------------------------------------------------
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Mapbox
# ---------------------------------------------------------------------------
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# ---------------------------------------------------------------------------
# Firebase (via GitLive KMP wrapper -> underlying Firebase Android SDK)
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---------------------------------------------------------------------------
# Credential Manager / Google Identity (Sign-In)
# ---------------------------------------------------------------------------
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ---------------------------------------------------------------------------
# Koin - fully static DI, no reflection, but keep modules defensively
# ---------------------------------------------------------------------------
-dontwarn org.koin.**

# ---------------------------------------------------------------------------
# Coil
# ---------------------------------------------------------------------------
-dontwarn coil.**

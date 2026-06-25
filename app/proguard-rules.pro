# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\bou\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep AIDL interfaces
-keep public interface com.ounben.amaradio.IPlayerService { *; }
-keep class com.ounben.amaradio.IPlayerService$Stub { *; }
-keep class com.ounben.amaradio.IPlayerService$Stub$Proxy { *; }

# Keep data classes that are serialized/deserialized (Gson/Room/Parcelable/JSON)
# These must be kept because they rely on field names for mapping
-keep class com.ounben.amaradio.station.DataRadioStation { *; }
-keep class com.ounben.amaradio.data.** { *; }
-keep class com.ounben.amaradio.history.TrackHistoryEntry { *; }
-keep class com.ounben.amaradio.proxy.ProxySettings { *; }
-keep class com.ounben.amaradio.CountryCodeDictionary$Country { *; }

# Keep Room DAOs and database
-keep class * extends androidx.room.RoomDatabase
-keep interface com.ounben.amaradio.history.TrackHistoryDao { *; }
-dontwarn androidx.room.paging.**

# Parcelable classes and creators
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp and dependencies
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Common Android components
-keep class androidx.lifecycle.** { *; }
-keep class androidx.preference.** { *; }

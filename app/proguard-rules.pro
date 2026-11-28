# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in Android SDK/tools/proguard/proguard-android.txt

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

# Media3 / ExoPlayer (Rely on library consumer rules)
-dontwarn androidx.media3.**

# Jetpack Glance (Specific keep rules instead of broad wildcard)
-dontwarn androidx.glance.**
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# AMARadio Widget Entry Points & Specific Classes
-keep class com.ounben.amaradio.widget.AMARadioFullWidget { *; }
-keep class com.ounben.amaradio.widget.AMARadioSmallWidget { *; }
-keep class com.ounben.amaradio.widget.AMARadioFullWidgetReceiver { *; }
-keep class com.ounben.amaradio.widget.AMARadioSmallWidgetReceiver { *; }
-keep class com.ounben.amaradio.widget.AMARadioWidgetActionReceiver { *; }
-keep class com.ounben.amaradio.widget.WidgetState { *; }
-keep class com.ounben.amaradio.widget.WidgetPlaylistSummary { *; }
-keep class com.ounben.amaradio.widget.WidgetUpdateHelper { *; }
-keep class com.ounben.amaradio.widget.WidgetImageLoader { *; }

# DataStore Preferences
-keepclassmembers class androidx.datastore.preferences.core.** { *; }

# OkHttp and dependencies
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Common Android components
-keep class androidx.lifecycle.** { *; }
-keep class androidx.preference.** { *; }

# Prevent R8 ServiceLoader optimization non-determinism (F-Droid compliance)
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory

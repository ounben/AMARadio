# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\bou\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Keep AIDL interfaces
-keep public interface com.ounben.amaradio.IPlayerService { *; }
-keep class com.ounben.amaradio.IPlayerService$Stub { *; }
-keep class com.ounben.amaradio.IPlayerService$Stub$Proxy { *; }

# Keep data classes that are serialized/deserialized (Gson/Room/Parcelable)
-keepclassmembers class com.ounben.amaradio.station.DataRadioStation { *; }
-keep class com.ounben.amaradio.station.DataRadioStation { *; }
-keep class com.ounben.amaradio.data.** { *; }

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Media3 classes
-keep class androidx.media3.** { *; }

# Keep OkHttp and Okio
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Keep Gson
-keep class com.google.gson.** { *; }

# Keep Room
-keep class androidx.room.** { *; }

# Keep lifecycle
-keep class androidx.lifecycle.** { *; }

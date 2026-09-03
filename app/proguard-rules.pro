# ProGuard & R8 Configuration for Release Builds

# Preserve Retrofit and OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Preserve Moshi and JSON data models
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-keep class com.example.data.model.SocketUpdate { *; }
-keep class com.example.data.model.DeviceCommand { *; }
-keep class com.example.data.model.TraccarGeofence { *; }
-keep class com.example.data.model.TraccarPermission { *; }
-keep class com.example.data.model.DailySummary { *; }
-keep class com.example.data.model.PeriodReport { *; }
-keep class com.example.data.model.PeriodType { *; }
-keep class com.example.data.model.SpeedViolationEvent { *; }
-keep class com.example.data.model.SpeedingViolationReport { *; }
-keep class com.example.data.model.GeofenceDwellRecord { *; }
-keep class com.example.data.model.GeofenceReport { *; }
-keep class com.example.data.model.Driver { *; }
-keep class com.example.data.model.Group { *; }
-keep class com.example.data.model.Server { *; }
-keep class com.example.data.model.CommandResult { *; }
-keep class com.example.data.db.** { *; }

# Preserve Room Database and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomOpenHelper
-dontwarn androidx.room.paging.**

# Preserve Google Maps and Play Services
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.gms.**

# Preserve Kotlin Coroutines and ViewModels
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class com.example.ui.viewmodel.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}



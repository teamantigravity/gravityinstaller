# Add project specific ProGuard rules here.

# Keep classes for AndroidX and Compose to prevent UI crashes
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep Coroutines to prevent flow/suspend crashes
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Keep Billing Client (In-App Purchases)
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }

# Keep Shizuku & Libsu classes since they rely on IPC/Reflection heavily
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }
-keep class com.topjohnwu.superuser.** { *; }
-keep interface com.topjohnwu.superuser.** { *; }

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

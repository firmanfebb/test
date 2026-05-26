# Add project specific ProGuard rules here.
-keepattributes JavascriptInterface
-keepclassmembers class cv.arthamasgraha.simpro.JSBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep native error pages
-keep class cv.arthamasgraha.simpro.** { *; }

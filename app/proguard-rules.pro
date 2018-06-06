# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Applications/ADT/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Remove logger statements
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
#    public static int v(...);
#    public static int i(...);
    public static int w(...);
#    public static int d(...);
    public static int e(...);
}

# This gets rid of outputs from System.out
# WARNING: if you're using this functions for other PrintStreams in your app, this can break things!
# Ref: https://xrubio.com/2015/10/disabling-logs-on-android-using-proguard/
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void printf(...);
    public void print(...);
}

-dontnote **

# FLIR SDK
# Ref: https://stackoverflow.com/questions/36249005/minifyenabled-true-leads-to-crash-on-app-start
-keep class com.flir.flironesdk.** { *; }
# Ref: https://developer.flir.com/forums/topic/sdk-3-0-1-not-building-with-proguard/
-dontwarn org.jetbrains.annotations.Contract


# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepattributes Signature,InnerClasses

# Keep POJO classes for gson
-keep class tw.cchi.medthimager.model.api.** { *; }


# Partial configs are referenced from: https://stackoverflow.com/questions/7464035/how-to-tell-proguard-to-keep-everything-in-a-particular-package
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.gson.**
-dontwarn com.google.firebase.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.slf4j.**
-dontwarn org.json.**
-dontwarn droidninja.filepicker.**
-dontwarn com.github.mikephil.charting.**
-dontwarn okhttp3.internal.**
-dontwarn okio.**

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService
-keep class javax.** { *; }
-keep class org.** { *; }
-keep class tw.cchi.medthimager.thermalproc.ThermalDumpProcessor { *; }

-keepclasseswithmembernames class * {
    native <methods>;
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

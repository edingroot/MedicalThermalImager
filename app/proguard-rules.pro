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
    public static *** d(...);
}

# Remove System.out statements
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
    public void printf(%);
    public void printf(**);
}

# DroidNinja/Android-FilePicker
-dontwarn droidninja.filepicker.**

# FLIR SDK
# Ref: https://stackoverflow.com/questions/36249005/minifyenabled-true-leads-to-crash-on-app-start
-keep class com.flir.flironesdk.** { *; }


# Configs referenced from: https://stackoverflow.com/questions/7464035/how-to-tell-proguard-to-keep-everything-in-a-particular-package
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.slf4j.**
-dontwarn org.json.**

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

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembernames class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembernames class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

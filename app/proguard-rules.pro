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

# Specify the decode mapping file
-printmapping mapping.txt

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

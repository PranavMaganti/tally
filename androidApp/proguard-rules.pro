# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ML Kit GenAI (Gemini Nano via AICore) finds parts of itself by reflection; without these the
# on-device model silently never becomes ready in minified builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_prompt.** { *; }
-dontwarn com.google.mlkit.**

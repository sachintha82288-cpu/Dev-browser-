# DevBrowser ProGuard/R8 rules.

# GeckoView
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.gecko.**

# Keep annotation-driven reflection happy in Compose
-dontwarn androidx.compose.**

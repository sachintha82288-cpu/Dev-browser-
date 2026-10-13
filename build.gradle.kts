// Top-level build file for DevBrowser.
//
// AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android is not
// applied anywhere: the Android plugin compiles the Kotlin sources itself.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Top-level build file. Plugin versions are declared in gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin-android intentionally omitted: AGP 9+ provides built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

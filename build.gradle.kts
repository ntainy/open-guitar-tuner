// Root build. Applying the Kotlin plugins here (apply false) puts KGP 2.3.21 on the build
// classpath, which raises AGP 9.4's built-in Kotlin (2.2.10) to the version the Compose
// compiler plugin is pinned to.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sqldelight) apply false
}

// Formatting is enforced rather than described, so review comments can be about behaviour.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        filter {
            // Generated: SQLDelight queries and the build-time ApiConfig.
            exclude { it.file.path.contains("/build/") }
        }
    }
}

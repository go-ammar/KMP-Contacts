import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

/**
 * The GoRest write endpoints need a personal access token. It is read from
 * `local.properties` (`gorest.token=...`) or the `GOREST_TOKEN` environment variable
 * so that it never has to be committed.
 */
val gorestToken: String =
    providers
        .provider {
            val localProperties = rootProject.file("local.properties")
            if (!localProperties.exists()) return@provider null
            val props = Properties()
            localProperties.inputStream().use { props.load(it) }
            props.getProperty("gorest.token")
        }.orElse(providers.environmentVariable("GOREST_TOKEN"))
        .getOrElse("")
        .orEmpty()
        .trim()

val generateApiConfig =
    tasks.register("generateApiConfig") {
        val outputDir = layout.buildDirectory.dir("generated/apiConfig")
        val token = gorestToken
        inputs.property("gorestToken", token)
        outputs.dir(outputDir)
        doLast {
            val pkgDir = outputDir.get().asFile.resolve("com/roster/data/remote")
            pkgDir.mkdirs()
            pkgDir.resolve("ApiConfig.kt").writeText(
                """
                package com.roster.data.remote

                /** Generated at build time - see shared/build.gradle.kts. */
                object ApiConfig {
                    const val BASE_URL: String = "https://gorest.co.in/public/v2"
                    const val TOKEN: String = "$token"
                    val hasToken: Boolean get() = TOKEN.isNotBlank()
                }

                """.trimIndent(),
            )
        }
    }

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateApiConfig)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)

                api(libs.koin.core)
                api(libs.androidx.lifecycle.viewmodel)
                // Declared explicitly so savedstate is unambiguously on the Apple compile
                // classpath. Kotlin/Native still logs a cosmetic "could not find" for it -
                // an upstream klib naming mismatch, not a missing artifact.
                implementation(libs.androidx.lifecycle.viewmodel.savedstate)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.turbine)
            }
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
        }

        // The JVM driver lets the cache and repository be tested against a real SQLite
        // database in memory, rather than against a hand-written fake of SQL semantics.
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.driver.jvm)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
    }
}

android {
    namespace = "com.roster.shared"
    compileSdk =
        libs.versions.androidCompileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // The shared HTTP logger calls android.util.Log, which throws in local unit
            // tests unless the android.* stubs return defaults instead of failing.
            isReturnDefaultValues = true
        }
    }
}

sqldelight {
    databases {
        create("RosterDatabase") {
            packageName.set("com.roster.data.local")
        }
    }
}

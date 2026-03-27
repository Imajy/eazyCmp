plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
}

kotlin {
    androidLibrary {
        namespace = "com.aj.shared"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }

        withHostTestBuilder {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // JVM Target for Desktop
    jvm("desktop")

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // ⚠️ Yahan se material-icons-extended HATA DIYA HAI (Ambiguity Fix)
                implementation(libs.kotlinx.datetime)
                implementation(libs.compose.components.resources)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.runtime)
                implementation(libs.foundation)
                implementation(libs.material3)

                implementation(libs.koin.compose)
                api(libs.koin.core)
                api(libs.koin.compose.viewmodel)
                implementation(libs.compose.lottie)
                implementation(libs.bundles.ktor)
                implementation(libs.bundles.coil)
                implementation(libs.multiplatform.settings.core)
                implementation(libs.multiplatform.settings.serialization)
            }
        }

        androidMain {
            dependencies {
                // Platform specific icon variant
                implementation("org.jetbrains.compose.material:material-icons-extended-android:1.7.3")
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                implementation(libs.koin.android)
            }
        }

        iosMain {
            dependencies {
                // iOS ke liye generic variant kaam karega
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation(libs.ktor.client.darwin)
            }
        }

        val desktopMain by getting {
            dependencies {
                // Platform specific icon variant for Desktop
                implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
                // ⚠️ currentOs HATA DIYA HAI (Library publishing fix)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

compose.resources {
    publicResClass = true
}

// Library module mein iski zaroorat nahi hoti, par error na de isliye rehne diya hai
compose.desktop {
    application {}
}

group = "com.github.Imajy"
version = "1.0.03-alpha-10" // 🚀 Naya version try karo

// JitPack build fail na ho isliye ye clean rakha hai
// Resolution strategy ki ab zaroorat nahi padni chahiye
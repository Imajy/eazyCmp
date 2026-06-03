plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
}

kotlin {
    androidLibrary {
        namespace = "com.aj.shared"
        compileSdk = 35
        minSdk = 24

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm()

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Core
            api(libs.kotlin.stdlib)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)

            // Compose
            api(libs.runtime)
            api(libs.foundation)
            api(libs.material3)
            api(libs.compose.components.resources)
            api(libs.material.icons.core)

            // DI
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)

            // Networking (exported so consumer doesn't need to add)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.bundles.ktor)

            // Media / UI utils
            api(libs.compose.lottie)
            api(libs.bundles.coil)

            // Settings
            api(libs.multiplatform.settings.core)
            api(libs.multiplatform.settings.serialization)

            // Lifecycle
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.cio)
            implementation(libs.javacv)
            implementation(libs.pdfbox)
            implementation(libs.opencv)
            // Desktop ko Instant class mil jaye isliye yahan specifically add kiya
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }

        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }
    }
}

compose.resources {
    publicResClass = true
}

compose.desktop {
    application {}
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
group = "com.github.Imajy"
version = "1.0.03-alpha-10"
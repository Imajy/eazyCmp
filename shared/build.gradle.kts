
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary) // Tera original plugin wapas
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
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
    }

    jvm()

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)

            implementation(libs.koin.compose)
            api(libs.koin.compose.viewmodel)
            implementation(libs.compose.lottie)
            implementation(libs.bundles.coil)
            implementation(libs.material.icons.core)
            implementation(libs.bundles.ktor)

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.multiplatform.settings.core)
            api(libs.multiplatform.settings.serialization)
            api(libs.runtime)
            api(libs.koin.core)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.runtime)
            api(libs.kotlinx.datetime) // Force export to other modules
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

// Baki niche ka Copy task aur metadata waisa hi rehne do

compose.desktop {
    application {}
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
group = "com.github.Imajy"
version = "1.0.03-alpha-10"
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
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm()

    // iOS targets (iosX64 removed — deprecated in CMP 1.11+)
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

            // Lifecycle (JetBrains KMP wrappers)
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.cio)
            implementation(libs.webcam.capture)
            implementation(libs.pdfbox)
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
        }

        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.security.crypto)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.imajy.shared.generated.resources"
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
group = "com.github.imajy"
version = "1.0.03-alpha-10"

configurations.configureEach {
    if (name.contains("jvm", ignoreCase = true) && (isCanBeResolved || isCanBeConsumed)) {
        attributes {
            attribute(
                org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
                org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
            )
        }
    }
}
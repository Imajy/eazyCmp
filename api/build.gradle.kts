plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    id("org.jetbrains.compose")
    id("maven-publish")
}

kotlin {
    androidLibrary {
        namespace = "com.aj.api"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            // coroutines + flow
            implementation(libs.kotlinx.coroutines.core)

            // ktor core
            implementation(libs.ktor.client.core)

            // serialization
            implementation(libs.kotlinx.serialization.json)

            // content negotiation
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // logging
            implementation(libs.ktor.client.logging)

            implementation(libs.multiplatform.settings.core)
            implementation(libs.multiplatform.settings.serialization)
        }
        androidMain.dependencies {

            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {

            implementation(libs.ktor.client.darwin)
        }

        jvmMain.dependencies {

            implementation(libs.ktor.client.cio)
        }

    }
}

compose.desktop {
    application {}
}

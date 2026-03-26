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

            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
            )

        }
        withHostTestBuilder {}

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    val xcfName = "sharedKit"

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.material.icons.extended)
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

//                implementation(libs.compose.uiToolingPreview)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.koin.android)
            }
        }

        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}


compose.resources {
    publicResClass = true
}

/**
 * Publishing config for new KMP structure
 */
publishing {

    publications {

        create<MavenPublication>("shared") {

            groupId = "com.aj"

            artifactId = "shared"

            version = "1.0.0"

            afterEvaluate {

                from(components["kotlin"])

            }

        }

    }

}
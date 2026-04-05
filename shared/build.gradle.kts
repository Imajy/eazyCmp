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
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // JVM Target for Desktop
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.datetime)
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
            api(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.cio)
            implementation("org.bytedeco:javacv:1.5.10")

            implementation("org.bytedeco:opencv:4.9.0-1.5.10")
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
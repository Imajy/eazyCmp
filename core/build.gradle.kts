plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
    alias(libs.plugins.sqldelight)
}

val skipIosTargets =
    project.findProperty("eazycmp.skip.ios") == "true" ||
        System.getenv("EAZYCMP_SKIP_IOS") == "true" ||
        System.getenv("JITPACK") == "true"

group = "com.github.Imajy.eazyCmp"

fun readEazyCmpVersion(): String {
    val fromProperty = findProperty("eazycmp.version")?.toString()
    if (!fromProperty.isNullOrBlank()) return fromProperty
    val fromEnv = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
    if (fromEnv != null) return fromEnv
    val versionFile = rootProject.file("version.properties")
    if (versionFile.exists()) {
        versionFile.readLines()
            .firstOrNull { it.trim().startsWith("version=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    return "1.0.0.1-rc-001"
}

version = readEazyCmpVersion()

kotlin {
    androidLibrary {
        namespace = "com.aj.shared.core"
        compileSdk = 36
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm()

    if (!skipIosTargets) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlin.stdlib)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.runtime)
            api(libs.foundation)
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.bundles.ktor)
            api(libs.multiplatform.settings.core)
            api(libs.multiplatform.settings.serialization)
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.runtime)
            api(libs.sqldelight.coroutines.extensions)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.sqlite.driver)
        }

        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
        }

        if (!skipIosTargets) {
            nativeMain.dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native.driver)
            }
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.security.crypto)
            implementation(libs.sqldelight.android.driver)
        }
    }
}

sqldelight {
    databases {
        create("EazyCmpDatabase") {
            packageName.set("com.aj.shared.db")
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("${rootProject.projectDir}/maven-repo")
        }
    }
    publications.withType<MavenPublication> {
        pom {
            name.set("EazyCmp Core")
            description.set("EazyCmp core — API, storage, networking, validation")
            url.set("https://github.com/Imajy/eazyCmp")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
        }
    }
}

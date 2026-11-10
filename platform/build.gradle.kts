plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
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
        namespace = "com.aj.shared.platform"
        compileSdk = 36
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
    }

    jvm()

    if (!skipIosTargets) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.runtime)
            api(libs.foundation)
            api(libs.material3)
            api(libs.material.icons.core)
            api(libs.compose.components.resources)
            api(libs.navigation.compose)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.webcam.capture)
            implementation(libs.pdfbox)
            implementation(libs.zxing.core)
        }

        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
            implementation(libs.play.app.update)
            implementation(libs.play.review)
            implementation(libs.play.services.auth)
            implementation(libs.play.services.auth.api.phone)
            implementation(libs.play.services.code.scanner)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.zxing.core)
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
            name.set("EazyCmp Platform")
            description.set("EazyCmp platform — permissions, media, location, auth, security")
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

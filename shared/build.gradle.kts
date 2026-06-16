plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
}

// JitPack runs on Linux — skip slow iOS native compile for fast publishes (~3-6 min).
// iOS host apps still compile iOS code locally via Kotlin/Native in their own project.
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
    return "1.0.0.001-rc-001"
}

version = readEazyCmpVersion()

val eazyCmpVersionDir = layout.buildDirectory.dir("generated/eazycmp/kotlin")

val generateEazyCmpVersion by tasks.registering {
    val libraryVersion = version.toString()
    outputs.dir(eazyCmpVersionDir)
    doLast {
        val outFile = eazyCmpVersionDir.get().asFile
            .resolve("com/aj/shared/internal/EazyCmpBuildInfo.kt")
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            package com.aj.shared.internal

            internal object EazyCmpBuildInfo {
                const val VERSION: String = "$libraryVersion"
            }
            """.trimIndent(),
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "com.aj.shared"
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

            // Navigation (JetBrains KMP wrappers)
            api(libs.navigation.compose)
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

        if (!skipIosTargets) {
            nativeMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.security.crypto)
            implementation(libs.androidx.biometric)
            implementation(libs.play.app.update)
            implementation(libs.play.review)
            implementation(libs.zxing.core)
        }
    }

    sourceSets.named("commonMain") {
        kotlin.srcDir(eazyCmpVersionDir)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.imajy.shared.generated.resources"
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.matching {
    it.name.contains("compile", ignoreCase = true) &&
        it.name.contains("Kotlin", ignoreCase = true)
}.configureEach {
    dependsOn(generateEazyCmpVersion)
}

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

publishing {
    repositories {
        // Free: artifacts committed to repo by GitHub Actions (see .github/workflows/publish.yml)
        maven {
            url = uri("${rootProject.projectDir}/maven-repo")
        }
    }
    publications.withType<MavenPublication> {
        pom {
            name.set("EazyCmp")
            description.set("Kotlin Multiplatform toolkit — API, UI, permissions, upload, security")
            url.set("https://github.com/Imajy/eazyCmp")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("imajy")
                    name.set("Imajy")
                    email.set("ajaykumarjaipur39@gmail.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/Imajy/eazyCmp.git")
                developerConnection.set("scm:git:ssh://github.com:Imajy/eazyCmp.git")
                url.set("https://github.com/Imajy/eazyCmp")
            }
        }
    }
}

// Skip Android Lint on JitPack only — use exact task names (never match *Metadata* tasks).
if (skipIosTargets) {
    val jitPackLintTasks = setOf(
        "lint",
        "lintAnalyzeAndroidMain",
        "lintReportAndroidMain",
        "prepareLintJarForPublish",
    )
    tasks.matching { it.project == project && it.name in jitPackLintTasks }.configureEach {
        enabled = false
    }
}
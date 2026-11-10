plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
}

// Umbrella artifact — api() re-exports core + ui + platform for one-line installs.
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
        namespace = "com.aj.shared"
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
            api(project(":core"))
            api(project(":platform"))
            api(project(":ui"))
            api(libs.kotlin.stdlib)
            api(libs.runtime)
            api(libs.foundation)
            api(libs.material3)
            api(libs.koin.core)
            api(libs.koin.compose)
        }

        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
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
            name.set("EazyCmp")
            description.set("Kotlin Multiplatform toolkit umbrella — core + ui + platform + plugin SPI")
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

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("maven-publish")
}

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
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlin.stdlib)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)

            api(libs.runtime)
            api(libs.foundation)
            api(libs.material3)
            api(libs.compose.components.resources)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
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
            name.set("EazyCmp Web")
            description.set("Kotlin Multiplatform Web toolkit for Compose Web, WasmJs, JS, and JVM")
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

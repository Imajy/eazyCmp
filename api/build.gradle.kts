plugins {

    alias(libs.plugins.kotlinMultiplatform)

    alias(libs.plugins.androidKotlinMultiplatformLibrary)

    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")

}

kotlin {

    androidLibrary {

        namespace = "com.aj.api"

        compileSdk = 36

        minSdk = 24

        compilerOptions {

            jvmTarget.set(

                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11

            )

        }

    }


    jvm()

    iosX64()

    iosArm64()

    iosSimulatorArm64()


    sourceSets {


        commonMain.dependencies {

            /**
             * kotlin base
             */
            implementation(libs.kotlin.stdlib)


            /**
             * coroutines exposed to app
             */
            api(libs.kotlinx.coroutines.core)


            /**
             * serialization exposed
             */
            api(libs.kotlinx.serialization.json)


            /**
             * ktor internal engine
             */
            implementation(libs.bundles.ktor)


            /**
             * storage exposed
             */
            api(libs.multiplatform.settings.core)

            api(libs.multiplatform.settings.serialization)

            api(libs.runtime)

            /**
             * dependency injection exposed
             */
            api(libs.koin.core)


            /**
             * lifecycle ViewModel exposed
             */
            api(libs.androidx.lifecycle.viewmodel)

            api(libs.androidx.lifecycle.runtime)


            /**
             * optional utilities
             */
            api(libs.kotlinx.datetime)

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
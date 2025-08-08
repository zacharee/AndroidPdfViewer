import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val libraryVersion by extra("4.0.3")
val javaVersion by extra(JvmTarget.JVM_21)

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.maven.publish)
}

val javaVersion: JvmTarget by rootProject.extra

android {
    compileSdk = 36

    defaultConfig {
        namespace = "com.github.barteksc.pdfviewer"
        minSdk = 24
        lint.targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion.target)
        targetCompatibility = JavaVersion.toVersion(javaVersion.target)
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(javaVersion)
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion.target)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.pdfiumandroid)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

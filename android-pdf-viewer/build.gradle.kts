import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

val javaVersion: JvmTarget by rootProject.extra

android {
    compileSdk = 36

    defaultConfig {
        namespace = "com.github.barteksc.pdfviewer"
        minSdk = 23
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion.target)
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            val libraryVersion: String by rootProject.extra

            groupId = "com.github.zacharee"
            artifactId = "AndroidPdfViewer"
            version = libraryVersion

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.pdfiumandroid)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

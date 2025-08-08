import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

val libraryVersion = "4.0.3"

android {
    compileSdk = 36

    defaultConfig {
        namespace = "com.github.barteksc.pdfviewer"
        minSdk = 23
        lint.targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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
        languageVersion = JavaLanguageVersion.of(21)
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
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

    api(libs.pdfiumandroid)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

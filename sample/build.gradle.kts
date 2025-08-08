import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
}

val javaVersion: JvmTarget by rootProject.extra

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pdfviewer"
        namespace = "com.github.barteksc.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
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
    implementation(project(":android-pdf-viewer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

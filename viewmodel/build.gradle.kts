plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "net.matsudamper.folderviewer.viewmodel"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":repository"))
    implementation(project(":navigation"))
    implementation(project(":ui"))
    implementation(project(":coil"))

    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleViewmodelKtx)
    implementation(libs.androidxDocumentfile)
    implementation(libs.hiltAndroid)
    implementation(libs.androidxWorkManagerRuntime)
    implementation(libs.androidxWorkManagerHilt)
    ksp(libs.hiltCompiler)
    ksp(libs.androidxHiltCompiler)

    implementation(libs.androidxNavigation3Runtime)

    implementation(libs.jakartaInject)
    implementation(libs.kotlinxSerializationJson)
}

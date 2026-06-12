plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "net.matsudamper.folderviewer.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    val hasPaparazziTask = gradle.startParameter.taskNames.any {
        it.lowercase().contains("paparazzi")
    }
    useJUnit {
        if (hasPaparazziTask) {
            includeCategories("net.matsudamper.folderviewer.ui.PaparazziTestCategory")
        } else {
            excludeCategories("net.matsudamper.folderviewer.ui.PaparazziTestCategory")
        }
    }
    systemProperty("paparazzi.filter", System.getProperty("paparazzi.filter", ""))
}

dependencies {
    implementation(project(":common"))
    implementation(project(":navigation"))
    implementation(project(":coil"))
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleViewmodelCompose)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeUiGraphics)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxComposeMaterial3)
    implementation(libs.androidxComposeFoundation)

    implementation(libs.androidxNavigation3Runtime)

    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)
    implementation(libs.coilCompose)
    implementation(libs.zoomable)
    debugImplementation(libs.androidxComposeUiTooling)
    debugImplementation(libs.androidxComposeUiTestManifest)
    testImplementation(libs.junit)
    testImplementation(libs.composablePreviewScanner)
}

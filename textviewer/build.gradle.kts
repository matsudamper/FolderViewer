plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "net.matsudamper.folderviewer.textviewer"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        compose = true
    }
}

tasks.withType<Test>().configureEach {
    val hasPaparazziTask = gradle.startParameter.taskNames.any {
        it.lowercase().contains("paparazzi")
    }
    useJUnit {
        if (hasPaparazziTask) {
            includeCategories("net.matsudamper.folderviewer.textviewer.PaparazziTestCategory")
        } else {
            excludeCategories("net.matsudamper.folderviewer.textviewer.PaparazziTestCategory")
        }
    }
    systemProperty("paparazzi.filter", System.getProperty("paparazzi.filter", ""))
}

dependencies {
    implementation(project(":ui"))

    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxLifecycleViewmodelKtx)
    implementation(libs.androidxLifecycleViewmodelCompose)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeUiGraphics)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxComposeMaterial3)
    implementation(libs.androidxComposeFoundation)
    implementation(libs.androidxDatastorePreferences)

    implementation(libs.hiltAndroid)
    implementation(libs.hiltLifecycleViewmodelCompose)
    ksp(libs.hiltCompiler)

    debugImplementation(libs.androidxComposeUiTooling)
    debugImplementation(libs.androidxComposeUiTestManifest)
    testImplementation(libs.junit)
    testImplementation(libs.composablePreviewScanner)
}

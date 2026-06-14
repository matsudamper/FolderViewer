plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
}

android {
    namespace = "net.matsudamper.folderviewer.coil"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(project(":repository"))
    implementation(project(":common"))

    implementation(libs.androidxCoreKtx)
    implementation(libs.coilCompose)
    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)
    implementation(libs.jakartaInject)
}

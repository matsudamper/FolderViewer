plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "net.matsudamper.folderviewer.navigation"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.androidxNavigation3Runtime)
}

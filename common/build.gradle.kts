plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "net.matsudamper.folderviewer.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(libs.kotlinxSerializationJson)
}

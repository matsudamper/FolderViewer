plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
}

android {
    namespace = "net.matsudamper.folderviewer"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.matsudamper.folderviewer"
        minSdk = 28
        targetSdk = 36
        val ciVersion = System.getenv("VERSION")?.toIntOrNull()
        versionCode = ciVersion ?: 1
        versionName = if (ciVersion != null) "Release-$ciVersion" else "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val isCI = System.getenv("CI") != null
        if (isCI) {
            create("ci") {
                val keystoreFile = System.getenv("DEBUG_KEYSTORE_FILE")
                requireNotNull(keystoreFile) { "DEBUG_KEYSTORE_FILE environment variable is required in CI" }
                storeFile = rootProject.file(keystoreFile)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            val isCI = System.getenv("CI") != null
            if (isCI) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.md"
            excludes += "META-INF/*.properties"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":navigation"))
    implementation(project(":ui"))
    implementation(project(":repository"))
    implementation(project(":viewmodel"))
    implementation(project(":coil"))

    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.androidxActivityCompose)
    implementation(platform(libs.androidxComposeBom))
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeUiGraphics)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxComposeMaterial3)

    implementation(libs.androidxNavigation3Runtime)
    implementation(libs.androidxNavigation3Ui)
    implementation(libs.androidxLifecycleViewmodelNavigation3)

    implementation(libs.hiltAndroid)
    implementation(libs.hiltLifecycleViewmodelCompose)
    implementation(libs.androidxWorkManagerRuntime)
    implementation(libs.androidxWorkManagerHilt)
    ksp(libs.hiltCompiler)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.coilCompose)
    implementation(libs.jakartaInject)
    implementation(libs.androidxDocumentfile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidxJunit)
    androidTestImplementation(libs.androidxEspressoCore)
    androidTestImplementation(platform(libs.androidxComposeBom))
    androidTestImplementation(libs.androidxComposeUiTestJunit4)
    debugImplementation(libs.androidxComposeUiTooling)
    debugImplementation(libs.androidxComposeUiTestManifest)
}

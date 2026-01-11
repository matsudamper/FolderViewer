plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
}

android {
    namespace = "net.matsudamper.folderviewer.repository"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.smbj)
    implementation(libs.smbjRpc)
    implementation(libs.androidxDatastore)
    implementation(libs.androidxSecurityCrypto)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.protobufKotlinLite)
    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)
    implementation(libs.jakartaInject)
    implementation(libs.microsoftGraph)
    implementation(libs.azureIdentity)
}

protobuf {
    protoc {
        val protoc = libs.protobufProtoc.get()
        artifact = "${protoc.group}:${protoc.name}:${protoc.version}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

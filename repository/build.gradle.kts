plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.ksp)
    alias(libs.plugins.daggerHilt)
    alias(libs.plugins.room)
}

android {
    namespace = "net.matsudamper.folderviewer.repository"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":dao:graph-api"))
    implementation(project(":common"))
    implementation(libs.androidxCoreKtx)
    implementation(libs.smbj)
    implementation(libs.smbjRpc)
    implementation(libs.androidxDatastore)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.protobufKotlinLite)
    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)
    implementation(libs.jakartaInject)
    implementation(libs.microsoftGraph)
    implementation(libs.azureIdentity)
    implementation(libs.androidxRoomRuntime)
    implementation(libs.androidxRoomKtx)
    ksp(libs.androidxRoomCompiler)
    testImplementation(libs.junit)
    testImplementation(libs.androidxJunit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidxRoomTesting)
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

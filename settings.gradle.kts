@file:Suppress("UnstableApiUsage")

import com.github.burrunan.s3cache.AwsS3BuildCache

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.github.burrunan.s3-build-cache") version "1.9.11"
}

val remoteBuildCacheAccessKeyId: String? = System.getenv("S3_BUILD_CACHE_ACCESS_KEY_ID")
buildCache {
    remote<AwsS3BuildCache> {
        isEnabled = !remoteBuildCacheAccessKeyId.isNullOrBlank()
        isPush = System.getenv("CI") == "true"
        endpoint = System.getenv("S3_BUILD_CACHE_ENDPOINT").orEmpty().ifBlank { "s3.matsudamper.net" }
        forcePathStyle = true
        region = "us-east-1"
        bucket = "gradle-remote-cache"
        prefix = "matsudamper/FolderViewer/"
        isReducedRedundancy = false
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FolderViewer"
include(":app")
include(":common")
include(":navigation")
include(":ui")
include(":repository")
include(":viewmodel")
include(":coil")
include(":textviewer")
include(":dao:graph-api")

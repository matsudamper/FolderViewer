import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.daggerHilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.paparazzi) apply false
}

subprojects {
    pluginManager.apply(DetektPlugin::class.java)
    pluginManager.apply(KtlintPlugin::class.java)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        verbose.set(true)
        version.set(rootProject.libs.versions.ktlint.get())
        outputToConsole.set(true)
    }
    configure<DetektExtension> {
        config.setFrom(rootProject.files("detekt.yml"))
        parallel = true
        buildUponDefaultConfig = true
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget.set("22")
        reports {
            html.required.set(false)
            markdown.required.set(true)
            markdown.outputLocation.set(file("build/reports/detekt.txt"))
            sarif.required.set(false)
        }
    }
    tasks.withType<Test> {
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
        }
    }
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        the<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension>().jvmToolchain(25)
    }
    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detektCompose)
    }
}

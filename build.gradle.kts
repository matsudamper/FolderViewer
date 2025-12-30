import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
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
        reports {
            html.required.set(false)
            txt.required.set(true)
            txt.outputLocation.set(file("build/reports/detekt.txt"))
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detekt.compose)
    }
}

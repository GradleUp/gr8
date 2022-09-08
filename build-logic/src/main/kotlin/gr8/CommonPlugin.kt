package gr8

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

class CommonPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      plugins.apply("org.jetbrains.kotlin.jvm")

      repositories.apply {
        //mavenLocal()
        mavenCentral()
        google()
      }

      extensions.getByType(JavaPluginExtension::class.java).toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
      }
      dependencies.apply {
        add("compileOnly", "org.jetbrains.kotlin:kotlin-stdlib") // let the Gradle plugin decide the version
      }

      rootProject.tasks.named("ci") {
        dependsOn(tasks.named("build"))
      }
    }
  }
}

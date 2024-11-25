import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-gradle-plugin")
}

dependencies {
  implementation(project(":gr8-plugin-common")).excludeKotlinStdlib()
}

fun Dependency?.excludeKotlinStdlib() {
  (this as? ExternalModuleDependency)?.apply {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
  }
}

Librarian.module(project)

gradlePlugin {
  plugins {
    create("gr8") {
      id = "com.gradleup.gr8.external"
      implementationClass = "com.gradleup.gr8.Gr8Plugin"
      // This is required by the Gradle publish plugin
      displayName = "Gr8 Plugin External"
      description = "The Gr8 Plugin packaged with external dependencies"
    }
  }
}


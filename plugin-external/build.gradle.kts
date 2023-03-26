plugins {
  id("gr8.build.common")
  id("gr8.build.publishing")
  id("java-gradle-plugin")
}

dependencies {
  implementation(project(":plugin-common")).excludeKotlinStdlib()
}

fun Dependency?.excludeKotlinStdlib() {
  (this as? ExternalModuleDependency)?.apply {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
  }
}

val name = "Gr8 Plugin External"
val gr8Description = "The Gr8 Plugin packaged with external dependencies"

gr8Publishing {
  configurePublications(name, gr8Description)
}

gradlePlugin {
  plugins {
    create("gr8") {
      id = "com.gradleup.gr8.external"
      implementationClass = "com.gradleup.gr8.Gr8Plugin"
      // This is required by the Gradle publish plugin
      displayName = name
      description = gr8Description
    }
  }
}


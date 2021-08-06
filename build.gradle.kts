plugins {
  id("maven-publish")
  id("java-gradle-plugin")
  id("org.jetbrains.kotlin.jvm").version("1.5.21")
  id("com.gradle.plugin-publish").version("0.15.0")
}

repositories {
  mavenCentral()
  google()
}

group = "com.gradleup.gr8"
version = "0.1"

dependencies {
  compileOnly(gradleApi())
  implementation("net.mbonnin.r8:r8:3.1.9-dev2")
}

pluginBundle {
  website = "https://github.com/GradleUp/gr8"
  vcsUrl = "https://github.com/GradleUp/gr8"
  tags = listOf("gradle", "r8", "gr8")
}

gradlePlugin {
  plugins {
    create("gr8") {
      id = "com.gradleup.gr8"
      displayName = "Gr8 Gradle plugin"
      description = "Use R8 to relocate/minimize your jar files"
      implementationClass = "com.gradleup.gr8.Gr8Plugin"
    }
  }
}

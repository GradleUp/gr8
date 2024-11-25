import com.gradleup.librarian.gradle.Librarian

buildscript {
  dependencies {
    classpath("build-logic:build-logic")
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    maven("https://storage.googleapis.com/gradleup/m2")
  }
}

Librarian.root(project)
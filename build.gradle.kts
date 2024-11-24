import com.gradleup.librarian.gradle.librarianRoot

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

librarianRoot()
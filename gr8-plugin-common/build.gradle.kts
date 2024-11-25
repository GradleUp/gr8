import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
}

dependencies {
  compileOnly(libs.gradle.api)
}

Librarian.module(project)


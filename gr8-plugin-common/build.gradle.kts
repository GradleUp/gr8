import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
}

dependencies {
  compileOnly(libs.gradle.api)
  implementation(libs.r8)
}

Librarian.module(project)


import com.gradleup.librarian.gradle.librarianModule

plugins {
  id("org.jetbrains.kotlin.jvm")
}

dependencies {
  compileOnly(libs.gradle.api)
  implementation(libs.r8)
}

librarianModule()


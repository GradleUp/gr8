plugins {
  id("java")
}

group = "build-logic"

dependencies {
  implementation(libs.vespene)
  implementation(libs.kgp)
  implementation(libs.gradle.publish)
  implementation(libs.gr8.published)
  implementation(libs.librarian)
}

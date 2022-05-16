pluginManagement {
  repositories {
    gradlePluginPortal()
  }
  includeBuild("build-logic")
}

include(":plugin")
include(":plugin-external")
include(":plugin-common")


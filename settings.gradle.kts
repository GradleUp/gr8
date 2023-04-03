pluginManagement {
  includeBuild("build-logic")
}

apply(from = "./gradle/repositories.gradle.kts")

include(":plugin")
include(":plugin-external")
include(":plugin-common")


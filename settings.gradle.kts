includeBuild("build-logic")

apply(from = "./gradle/repositories.gradle.kts")

include(":gr8-plugin")
include(":gr8-plugin-external")
include(":gr8-plugin-common")


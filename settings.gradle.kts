includeBuild("build-logic")

apply(from = "./gradle/repositories.gradle.kts")

include(":gr8-plugin")
include(":gr8-plugin-external")
include(":gr8-plugin-common")

/**
 * We need Java <= 17 until we update the embedded gr8
 * See https://issuetracker.google.com/u/2/issues/365578411
 */
check(JavaVersion.current() <= JavaVersion.VERSION_17) {
  "This project needs to be run with Java 17 or higher (found: ${JavaVersion.current()})."
}
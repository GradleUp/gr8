import com.gradleup.gr8.StripGradleApiTask.Companion.isGradleApi

plugins {
  id("org.jetbrains.kotlin.jvm").version("1.5.21")
  id("maven-publish")
  id("com.gradleup.gr8.external")
}

repositories {
  mavenCentral()
  google()
}

group = "com.gradleup.gr8"
version = "0.1"

// Test for https://github.com/GradleUp/gr8/issues/10
val agpTest = configurations.detachedConfiguration(dependencies.create("com.android.tools.build:gradle-api:8.1.0-alpha10") {
  isTransitive = false
})
val gradleTest = configurations.detachedConfiguration(dependencies.create("dev.gradleplugins:gradle-api:6.9") {
  isTransitive = false
})

check(!agpTest.files.single().isGradleApi())
check(gradleTest.files.single().isGradleApi())

dependencies {
  // Do not use gradleApi() as it forces Kotlin 1.4 on the classpath
  compileOnly("dev.gradleplugins:gradle-api:6.9")

  testImplementation("dev.gradleplugins:gradle-test-kit:6.9")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
}

configure<com.gradleup.gr8.Gr8Extension> {
  removeGradleApiFromApi()

  val shadowedJar = create("gr8") {
    proguardFile("rules.pro")
    configuration("runtimeClasspath")
  }

  addShadowedVariant(shadowedJar)
}

tasks.named("test") {
  dependsOn("publishAllPublicationsToPluginTestRepository")
}

publishing {
  repositories {
    maven {
      name = "pluginTest"
      url = uri("file://${rootProject.buildDir}/localMaven")
    }
  }
}


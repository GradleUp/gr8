buildscript {
  dependencies {
    classpath("com.gradleup.gr8:gr8")
  }
}

plugins {
  id("org.jetbrains.kotlin.jvm").version("1.5.21")
  id("java-gradle-plugin")
  id("maven-publish")
  id("com.gradle.plugin-publish").version("0.15.0")
}

apply(plugin = "com.gradleup.gr8")

repositories {
  mavenCentral()
}

gradlePlugin {
  plugins {
    create("testPlugin") {
      id = "com.gradleup.test"
      displayName = "A test plugin"
      description = "A test plugin"
      implementationClass = "com.gradleup.test.TestPlugin"
    }
  }
}

group = "com.gradleup.gr8"
version = "0.1"

dependencies {
  // Do not use gradleApi() as it forces Kotlin 1.4 on the classpath
  compileOnly("dev.gradleplugins:gradle-api:6.9")

  testImplementation("org.jetbrains.kotlin:kotlin-test")
}


configure<com.gradleup.gr8.Gr8Extension> {
  removeGradleApiFromApi()

  val shadowedJar = create("gr8") {
    proguardFile("rules.pro")
    configuration("runtimeClasspath")
    workaroundDefaultConstructorMarker(true)
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
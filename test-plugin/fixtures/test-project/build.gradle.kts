buildscript {
  repositories {
    mavenCentral()
    maven("../../build/localMaven")
  }
//  configurations.named("classpath").configure {
//    attributes {
//      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EMBEDDED))
//    }
//  }
  dependencies {
    classpath("com.gradleup.gr8:test-plugin:0.1")
  }
}

apply(plugin = "com.gradleup.test")
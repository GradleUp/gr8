buildscript {
  repositories {
    mavenCentral()
    maven("../../build/localMaven")
  }
//  configurations.named("classpath").configure {
//    attributes {
//      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
//    }
//  }
  dependencies {
    classpath("com.gradleup.gr8.test:test-plugin:0.1")
  }
}

apply(plugin = "com.gradleup.test")
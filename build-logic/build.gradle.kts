plugins {
  `embedded-kotlin`
  id("java-gradle-plugin")
}

plugins.apply(org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverGradleSubplugin::class.java)
extensions.configure(org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension::class.java) {
  annotations(HasImplicitReceiver::class.qualifiedName!!)
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("net.mbonnin.vespene:vespene-lib:0.5")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
  implementation("com.gradle.publish:plugin-publish-plugin:0.15.0")
  implementation("com.gradleup:gr8-plugin:0.6")
}

gradlePlugin {
  plugins {
    register("gr8.build.common") {
      id = "gr8.build.common"
      implementationClass = "gr8.CommonPlugin"
    }

    register("gr8.build.publishing") {
      id = "gr8.build.publishing"
      implementationClass = "gr8.PublishingPlugin"
    }
  }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))
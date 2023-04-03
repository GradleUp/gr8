import com.gradleup.gr8.StripGradleApiTask.Companion.isGradleApi
import org.gradle.api.attributes.Usage.JAVA_API
import org.w3c.dom.Element

plugins {
  id("org.jetbrains.kotlin.jvm").version("1.5.21")
  id("maven-publish")
  id("com.gradleup.gr8.external")
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
  compileOnly("dev.gradleplugins:gradle-api:7.6")
  
  testImplementation("dev.gradleplugins:gradle-test-kit:6.9")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
}

configurations.create("gr8ClassPath") {
  extendsFrom(configurations.getByName("compileOnly"))
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(JAVA_API))
  }
}

configure<com.gradleup.gr8.Gr8Extension> {
  removeGradleApiFromApi()

  val shadowedJar = create("gr8") {
    proguardFile("rules.pro")
    configuration("runtimeClasspath")
    classPathConfiguration("gr8ClassPath")
    stripGradleApi(true)
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

  publications.create("default", MavenPublication::class.java) {
    from(components.getByName("java"))
  }
  publications.create("marker", MavenPublication::class.java) {
    this.groupId = "com.gradleup.test"
    this.artifactId = "com.gradleup.test.gradle.plugin"

    /**
     * From https://github.com/gradle/gradle/blob/38930bc7f5891f3d2ca00d20ab0af22013c17f00/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/MavenPluginPublishPlugin.java#L85
     *
     */
    this.pom.withXml {
      val root: Element = asElement()
      val document = root.ownerDocument
      val dependencies = root.appendChild(document.createElement("dependencies"))
      val dependency = dependencies.appendChild(document.createElement("dependency"))
      val groupId = dependency.appendChild(document.createElement("groupId"))
      groupId.textContent = "com.gradleup.test"
      val artifactId = dependency.appendChild(document.createElement("artifactId"))
      artifactId.textContent = "com.gradleup.test.gradle.plugin"
      val version = dependency.appendChild(document.createElement("version"))
      version.textContent = project.version.toString()
    }
  }
}



import com.gradleup.gr8.FilterTransform
import org.w3c.dom.Element

plugins {
  id("org.jetbrains.kotlin.jvm").version("2.0.21")
  id("maven-publish")
  id("com.gradleup.gr8")
}

group = "com.gradleup.gr8.test"
version = "0.1"

dependencies {
  compileOnly("dev.gradleplugins:gradle-api:7.6")
  implementation("com.apollographql.apollo:apollo-runtime:4.1.0")

  testImplementation("dev.gradleplugins:gradle-test-kit:6.9")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
}

val compileOnlyDependenciesForGr8: Configuration = configurations.create("compileOnlyDependenciesForGr8") {
  attributes {
    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, FilterTransform.artifactType)
    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
  }
}

compileOnlyDependenciesForGr8.extendsFrom(configurations.getByName("compileOnly"))

gr8 {
  val shadowedJar = create("default") {
    addProgramJarsFrom(configurations.getByName("runtimeClasspath"))
    addProgramJarsFrom(tasks.getByName("jar"))
    addClassPathJarsFrom(compileOnlyDependenciesForGr8)

    proguardFile("rules.pro")

    r8Version("887704078a06fc0090e7772c921a30602bf1a49f")
    systemClassesToolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
  }
  registerFilterTransform(listOf(".*/impldep/META-INF/versions/.*"))
  addShadowedVariant(shadowedJar)
}

tasks.named("test") {
  dependsOn("publishAllPublicationsToPluginTestRepository")
}

java {
  toolchain {
    // Tests use a pretty old version of Gradle so let's stick with an old version of Java as well
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

publishing {
  repositories {
    maven {
      name = "pluginTest"
      url = uri("file://${rootProject.layout.buildDirectory.asFile.get()}/localMaven")
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



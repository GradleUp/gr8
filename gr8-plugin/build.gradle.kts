@file:Suppress("UnstableApiUsage")

import com.gradleup.gr8.FilterTransform
import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-gradle-plugin")
  id("com.gradleup.gr8")
}

val filteredClasspathDependencies: Configuration = configurations.create("filteredClasspathDependencies") {
  attributes {
    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, FilterTransform.artifactType)
    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
  }
}

filteredClasspathDependencies.extendsFrom(configurations.getByName("compileOnly"))

dependencies {
  implementation(project(":gr8-plugin-common"))
  compileOnly("dev.gradleplugins:gradle-api:6.7")
}

if (true) {
  gr8 {
    val shadowedJar = create("default") {
      addProgramJarsFrom(configurations.getByName("runtimeClasspath"))
      addProgramJarsFrom(tasks.getByName("jar"))
      addClassPathJarsFrom(filteredClasspathDependencies)

      proguardFile("rules.pro")

      r8Version("887704078a06fc0090e7772c921a30602bf1a49f")
      systemClassesToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
      }
      registerFilterTransform(listOf(".*/impldep/META-INF/versions/.*"))
    }

    removeGradleApiFromApi()
    replaceOutgoingJar(shadowedJar)
  }
}

gradlePlugin {
  plugins {
    create("gr8") {
      id = "com.gradleup.gr8"
      implementationClass = "com.gradleup.gr8.Gr8Plugin"
      // This is required by the Gradle publish plugin
      displayName = "Gr8 Plugin"
      description = "The Gr8 Plugin packaged with all dependencies relocated"
    }
  }
}

Librarian.module(project)

// See https://github.com/GradleUp/librarian/issues/50
afterEvaluate {
  extensions.getByType<PublishingExtension>().publications.configureEach {
    this as MavenPublication
    if (name.lowercase().contains("marker")) {
      this.groupId = "com.gradleup.gr8"
    }
  }
}

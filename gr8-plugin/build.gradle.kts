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
  }
}

filteredClasspathDependencies.extendsFrom(configurations.getByName("compileOnly"))


dependencies {
  implementation(project(":gr8-plugin-common"))
  compileOnly("dev.gradleplugins:gradle-api:6.7") {
    /**
     * Classpath type already present: org.apache.tools.ant.IntrospectionHelper$4
     */
    exclude("org.apache.ant")
  }

  registerTransform(FilterTransform::class) {
    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, FilterTransform.artifactType)

    parameters.excludes = listOf(".*/impldep/META-INF/versions/.*")
  }
}



if (true) {
  gr8 {
    removeGradleApiFromApi()

    val shadowedJar = create("default") {
      addProgramJarsFrom(configurations.getByName("runtimeClasspath"))
      addProgramJarsFrom(tasks.getByName("jar"))
      addClassPathJarsFrom(filteredClasspathDependencies)

      proguardFile("rules.pro")

      r8Version("887704078a06fc0090e7772c921a30602bf1a49f")
      systemClassesToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
      }
    }

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


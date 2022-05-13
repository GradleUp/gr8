package com.gradleup.gr8

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

open class Gr8Extension(
  private val project: Project,
) {

  private val configurators = mutableSetOf<String>()

  /**
   * @return a provider that returns the new fat-minimized jar
   */
  fun create(name: String, action: Action<Gr8Configurator> = Action<Gr8Configurator> {}): Provider<RegularFile> {
    check(configurators.contains(name).not()) {
      "Gr8: $name is already created"
    }

    val configurator = Gr8Configurator(name, project)
    configurators.add(name)

    action.execute(configurator)

    val provider = configurator.registerTasks()

    project.plugins.withId("maven-publish") {
      project.extensions.findByType(PublishingExtension::class.java)?.apply {
        publications.withType(MavenPublication::class.java).configureEach {
          if (!name.lowercase().contains("marker")) {
            it.artifact(provider.flatMap { it.mapping }) {
              it.classifier = "mapping"
            }
          }
        }
      }
    }
    return provider.flatMap { it.outputJar() }
  }

  /**
   * Replaces the default jar in outgoingVariants with [newJar]
   * Because it replaces the existing jar, the variant will keep the dependencies and attributes
   * of the java component. In particular, "org.gradle.dependency.bundling" will be "external" despite
   * the newJar most likely shading some dependencies.
   *
   * In order to not propagate dependencies, create a separate "shade" configuration and make "compileOnly"
   * extend it
   *
   * @param newJar the new jar to use
   * See [org.gradle.api.artifacts.dsl.ArtifactHandler] for details of the supported notations.
   */
  fun replaceOutgoingJar(newJar: Any) {
    project.configurations.configureEach { configuration ->
      configuration.outgoing { publications ->
        val removed = publications.artifacts.removeIf { it.classifier.isNullOrEmpty() }
        if (removed) {
          publications.artifact(newJar) { artifact ->
            // Pom and maven consumers do not like the `-all` or `-shadowed` classifiers
            artifact.classifier = ""
          }
        }
      }
    }
  }

  /**
   * Adds a new variant with "org.gradle.dependency.bundling": "shadowed"
   */
  fun addShadowedVariant(shadowedJar: Any) {
    val producerConfiguration = project.configurations.create("gr8") {
      it.isCanBeResolved = false
      it.isCanBeConsumed = true
      it.attributes {
        it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.SHADOWED))
        it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
        it.attribute(
          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(
            LibraryElements::class.java,
            LibraryElements.JAR
          ))
      }
    }

    val components = project.components
    val javaComponent = components.findByName("java") as AdhocComponentWithVariants
    javaComponent.addVariantsFromConfiguration(producerConfiguration) {}

    project.artifacts.add("gr8", shadowedJar)
  }

  fun removeGradleApiFromApi() {
    // The java-gradle-plugin adds `gradleApi()` to the `api` implementation but it contains some JDK15 bytecode at
    // org/gradle/internal/impldep/META-INF/versions/15/org/bouncycastle/jcajce/provider/asymmetric/edec/SignatureSpi$EdDSA.class:
    //java.lang.IllegalArgumentException: Unsupported class file major version 59
    // So remove it
    val apiDependencies = project.configurations.getByName("api").dependencies
    val gradleApi = apiDependencies.firstOrNull {
      val targetComponentId = (it as? DefaultSelfResolvingDependency)?.targetComponentId?.toString()
      targetComponentId == "Gradle API"
    }

    if (gradleApi != null) {
      apiDependencies.remove(gradleApi)
    }
  }
}
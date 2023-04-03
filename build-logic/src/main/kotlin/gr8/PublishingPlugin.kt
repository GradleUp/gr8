package gr8

import com.gradle.publish.PluginBundleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import java.util.*

class PublishingPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      plugins.apply("signing")
      plugins.apply("maven-publish")


      group = "com.gradleup"
      version = "0.8"

      val projectVersion = version

      project.extensions.extraProperties.set("gradle.publish.key", System.getenv("GRADLE_KEY"))
      project.extensions.extraProperties.set("gradle.publish.secret", System.getenv("GRADLE_SECRET"))

      extensions.create("gr8Publishing", PublishingOptions::class.java)

      fun Project.getOssStagingUrl(): String {
        val url = try {
          this.rootProject.extensions.extraProperties["ossStagingUrl"] as String?
        } catch (e: ExtraPropertiesExtension.UnknownPropertyException) {
          null
        }
        if (url != null) {
          return url
        }
        val client = net.mbonnin.vespene.lib.NexusStagingClient(
          username = System.getenv("OSSRH_USER"),
          password = System.getenv("OSSRH_PASSWORD"),
        )
        val repositoryId = kotlinx.coroutines.runBlocking {
          client.createRepository(
            profileId = System.getenv("COM_GRADLEUP_PROFILE_ID"),
            description = "$group:$name $projectVersion"
          )
        }
        println("publishing to '$repositoryId")
        return "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/${repositoryId}/".also {
          this.rootProject.extensions.extraProperties["ossStagingUrl"] = it
        }
      }

      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.apply {
        repositories {
          maven {
            name = "ossSnapshots"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            credentials {
              username = System.getenv("OSSRH_USER")
              password = System.getenv("OSSRH_PASSWORD")
            }
          }

          maven {
            name = "ossStaging"
            setUrl {
              uri(rootProject.getOssStagingUrl())
            }
            credentials {
              username = System.getenv("OSSRH_USER")
              password = System.getenv("OSSRH_PASSWORD")
            }
          }
        }
      }

      extensions.getByType(SigningExtension::class.java).apply {
        // GPG_PRIVATE_KEY should contain the armoured private key that starts with -----BEGIN PGP PRIVATE KEY BLOCK-----
        // It can be obtained with gpg --armour --export-secret-keys KEY_ID
        useInMemoryPgpKeys(System.getenv("GPG_KEY"), System.getenv("GPG_KEY_PASSWORD"))
        sign(publishing.publications)
      }

      tasks.withType(Sign::class.java).configureEach {
        isEnabled = !System.getenv("GPG_KEY").isNullOrBlank()
      }

      plugins.withId("java-gradle-plugin") {
        plugins.apply("com.gradle.plugin-publish")
        extensions.findByType(PluginBundleExtension::class.java)?.apply {
          website = "https://github.com/GradleUp/gr8"
          vcsUrl = "https://github.com/GradleUp/gr8"
          tags = listOf("gradle", "r8", "gr8")
        }
      }

      fun isTag(): Boolean {
        val ref = System.getenv("GITHUB_REF")

        return ref?.startsWith("refs/tags/") == true
      }

      if (isTag()) {
        rootProject.tasks.named("ci") {
          dependsOn(tasks.named("publishAllPublicationsToOssStagingRepository"))
          plugins.withId("com.gradle.plugin-publish") {
            dependsOn(tasks.named("publishPlugins"))
          }
        }
      }
    }
  }


  open class PublishingOptions {
    open fun Project.configurePublications(pomName: String, pomDescription: String) {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.apply {
        publications.withType(MavenPublication::class.java) {
          if (!this.name.lowercase(Locale.ROOT).contains("marker")) {
            artifact(createJavaSourcesTask())
            artifact(emptyJavadocJarTaskProvider())
          }
          setDefaultPomFields(this, pomName, pomDescription)
        }
      }
    }
  }

  companion object {
    fun Project.setDefaultPomFields(
      mavenPublication: MavenPublication,
      pomName: String,
      pomDescription: String
    ) {
      mavenPublication.groupId = group.toString()
      mavenPublication.version = version.toString()
      mavenPublication.artifactId = "gr8-$name"

      mavenPublication.pom {
        name.set(pomName)
        description.set(pomDescription)

        val githubUrl = "https://github.com/gradleup/gr8"

        url.set(githubUrl)

        scm {
          url.set(githubUrl)
          connection.set(githubUrl)
          developerConnection.set(githubUrl)
        }

        licenses {
          license {
            name.set("MIT License")
            url.set("https://github.com/GradleUp/gr8/blob/main/LICENSE")
          }
        }

        developers {
          developer {
            id.set("GradleUp authors")
            name.set("GradleUp authors")
          }
        }
      }
    }

    private fun Project.createJavaSourcesTask(): TaskProvider<Jar> {
      try {
        @Suppress("UNCHECKED_CAST")
        return tasks.named("javaSourcesJar") as TaskProvider<Jar>
      } catch (e: Throwable) {
        // Ignored
      }
      return tasks.register("javaSourcesJar", Jar::class.java) {
        /**
         * Add a dependency on the compileKotlin task to make sure the generated sources like
         * antlr or SQLDelight get included
         * See also https://youtrack.jetbrains.com/issue/KT-47936
         */
        dependsOn("compileKotlin")

        archiveClassifier.set("sources")
        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        from(sourceSets.getByName("main").allSource)
      }
    }

    private fun Project.emptyJavadocJarTaskProvider(): TaskProvider<org.gradle.jvm.tasks.Jar> {
      return tasks.register("emptyJavadocJar", org.gradle.jvm.tasks.Jar::class.java) {
        archiveClassifier.set("javadoc")
      }
    }
  }
}

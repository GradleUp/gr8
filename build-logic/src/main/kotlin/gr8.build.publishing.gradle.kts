import com.gradle.publish.PluginBundleExtension
import java.util.*

plugins {
    id("signing")
    id("maven-publish")
}

group = "com.gradleup"
version = "0.5"

val projectVersion = version

project.extra.set("gradle.publish.key", System.getenv("GRADLE_KEY"))
project.extra.set("gradle.publish.secret", System.getenv("GRADLE_SECRET"))

open class PublishingOptions {
    open fun configurePublications(pomName: String, pomDescription: String) {
        publishing {
            publications.withType(MavenPublication::class.java) {
                if (!this.name.toLowerCase(Locale.ROOT).contains("marker")) {
                    artifact(createJavaSourcesTask())
                    artifact(emptyJavadocJarTaskProvider())
                }
                setDefaultPomFields(this, pomName, pomDescription)
            }
        }
    }
}

val extension = extensions.create("gr8Publishing", PublishingOptions::class, this)

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

fun Project.createJavaSourcesTask(): TaskProvider<Jar> {
    try {
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

fun Project.emptyJavadocJarTaskProvider(): TaskProvider<org.gradle.jvm.tasks.Jar> {
    return tasks.register("emptyJavadocJar", org.gradle.jvm.tasks.Jar::class.java) {
        archiveClassifier.set("javadoc")
    }
}

publishing {
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

signing {
    // GPG_PRIVATE_KEY should contain the armoured private key that starts with -----BEGIN PGP PRIVATE KEY BLOCK-----
    // It can be obtained with gpg --armour --export-secret-keys KEY_ID
    useInMemoryPgpKeys(System.getenv("GPG_KEY"), System.getenv("GPG_KEY_PASSWORD"))
    sign(publishing.publications)
}

tasks.withType(Sign::class.java).configureEach {
    isEnabled = !System.getenv("GPG_KEY").isNullOrBlank()
}

plugins.withId("java-gradle-plugin") {
    apply(plugin = "com.gradle.plugin-publish")
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
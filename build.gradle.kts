import java.util.Locale

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("net.mbonnin.vespene:vespene-lib:0.5")
  }
}
plugins {
  id("maven-publish")
  id("java-gradle-plugin")
  id("signing")
  id("org.jetbrains.kotlin.jvm").version("1.5.21")
  id("com.gradle.plugin-publish").version("0.15.0")
  id("com.gradleup.gr8").version("0.1")
}

repositories {
  mavenCentral()
  google()
}

group = "com.gradleup"
version = "0.2"

val shadeConfiguration = configurations.create("shade")

dependencies {
  compileOnly(gradleApi())
  add("shade", "net.mbonnin.r8:r8:3.0.65")
}

configurations.getByName("compileOnly").extendsFrom(shadeConfiguration)

gr8 {
  val shadowedJar = create("plugin") {
    configuration("shade")
    proguardFile("rules.pro")
  }

  removeGradleApiFromApi()
  replaceOutgoingJar(shadowedJar)
}

pluginBundle {
  website = "https://github.com/GradleUp/gr8"
  vcsUrl = "https://github.com/GradleUp/gr8"
  tags = listOf("gradle", "r8", "gr8")
}

gradlePlugin {
  plugins {
    create("gr8") {
      id = "com.gradleup.gr8"
      displayName = "Gr8 Gradle plugin"
      description = "Use R8 to relocate/minimize your jar files"
      implementationClass = "com.gradleup.gr8.Gr8Plugin"
    }
  }
}

fun Project.getOssStagingUrl(): String {
  val url = try {
    this.extensions.extraProperties["ossStagingUrl"] as String?
  } catch (e: ExtraPropertiesExtension.UnknownPropertyException) {
    null
  }
  if (url != null) {
    return url
  }
  val client = net.mbonnin.vespene.lib.NexusStagingClient(
      username = System.getenv("SONATYPE_NEXUS_USERNAME"),
      password = System.getenv("SONATYPE_NEXUS_PASSWORD"),
  )
  val repositoryId = kotlinx.coroutines.runBlocking {
    client.createRepository(
        profileId = System.getenv("COM_GRADLEUP_PROFILE_ID"),
        description = "$group:$name $version"
    )
  }
  println("publishing to '$repositoryId")
  return "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/${repositoryId}/".also {
    this.extensions.extraProperties["ossStagingUrl"] = it
  }
}

fun Project.createJavaSourcesTask(): TaskProvider<Jar> {
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

val emptyJavadocJarTaskProvider = tasks.register("emptyJavadocJar", org.gradle.jvm.tasks.Jar::class.java) {
  archiveClassifier.set("javadoc")
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

  publications.withType(MavenPublication::class.java) {
    if (!name.toLowerCase(Locale.ROOT).contains("marker")) {
      artifact(createJavaSourcesTask())
      artifact(emptyJavadocJarTaskProvider)
    }
    setDefaultPomFields(this)
  }
}

fun Project.setDefaultPomFields(mavenPublication: MavenPublication) {
  mavenPublication.groupId = group.toString()
  mavenPublication.version = version.toString()
  mavenPublication.artifactId = "gr8-plugin"

  mavenPublication.pom {
    name.set("Gr8 plugin")

    val githubUrl = "https://github.com/gradleup/gr8"

    description.set("A Gradle plugin for R8")
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

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

fun isTag(): Boolean {
  val ref = System.getenv("GITHUB_REF")

  return ref?.startsWith("refs/tags/") == true
}

tasks.register("ci") {
  dependsOn("build")
  if (isTag()) {
    dependsOn("publishAllPublicationsToOssStagingRepository")
    dependsOn("publishPlugins")
  }
}
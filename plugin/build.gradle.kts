import com.gradleup.librarian.gradle.librarianModule

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-gradle-plugin")
  id("com.gradleup.gr8")
}

val shadeConfiguration: Configuration = configurations.create("shade")
val classpathConfiguration: Configuration = configurations.create("gr8Classpath")

dependencies {
  add("shade", project(":plugin-common")) {
    // Because we only allow stripping the gradleApi from the classpath, we remove
    exclude("dev.gradleplugins", "gradle-api")
  }
  compileOnly("dev.gradleplugins:gradle-api:6.7")
  add("gr8Classpath", "dev.gradleplugins:gradle-api:6.7") {
    exclude("org.apache.ant")
  }
}

configurations.getByName("compileOnly").extendsFrom(shadeConfiguration)

if (true) {
  gr8 {
    val shadowedJar = create("plugin") {
      configuration("shade")
      proguardFile("rules.pro")
      classPathConfiguration("gr8Classpath")
      stripGradleApi(true)
    }

    // The java-gradle-plugin adds `gradleApi()` to the `api` implementation but it contains some JDK15 bytecode at
    // org/gradle/internal/impldep/META-INF/versions/15/org/bouncycastle/jcajce/provider/asymmetric/edec/SignatureSpi$EdDSA.class:
    // java.lang.IllegalArgumentException: Unsupported class file major version 59
    // So remove it
    val apiDependencies = project.configurations.getByName("api").dependencies
    apiDependencies.firstOrNull {
      it is FileCollectionDependency
    }.let {
      apiDependencies.remove(it)
    }

    replaceOutgoingJar(shadowedJar)
  }
} else {
  configurations.named("implementation").configure {
    extendsFrom(shadeConfiguration)
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

librarianModule()

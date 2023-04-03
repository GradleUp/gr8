plugins {
  id("gr8.build.common")
  id("gr8.build.publishing")
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

    removeGradleApiFromApi()
    replaceOutgoingJar(shadowedJar)
  }
} else {
  configurations.named("implementation").configure {
    extendsFrom(shadeConfiguration)
  }
}

val name = "Gr8 Plugin"
val gr8description = "The Gr8 Plugin packaged with all dependencies relocated"

gr8Publishing {
  configurePublications(name, gr8description)
}
gradlePlugin {
  plugins {
    create("gr8") {
      id = "com.gradleup.gr8"
      implementationClass = "com.gradleup.gr8.Gr8Plugin"
      // This is required by the Gradle publish plugin
      displayName = name
      description = gr8description
    }
  }
}


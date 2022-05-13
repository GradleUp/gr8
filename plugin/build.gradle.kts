plugins {
    id("gr8.build.common")
    id("gr8.build.publishing")
    id("java-gradle-plugin")
    id("com.gradleup.gr8")
}

val shadeConfiguration: Configuration = configurations.create("shade")
val classpathConfiguration: Configuration = configurations.create("gr8Classpath")

dependencies {
    add("shade", project(":plugin-common"))
    add("gr8Classpath", "dev.gradleplugins:gradle-api:6.0") {
        exclude("org.apache.ant", )
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
val description = "The Gr8 Plugin packaged with all dependencies relocated"

gr8Publishing {
    configurePublications(name, description)
}
gradlePlugin {
    plugins {
        create("gr8") {
            id = "com.gradleup.gr8"
            implementationClass = "com.gradleup.gr8.Gr8Plugin"
        }
    }
}


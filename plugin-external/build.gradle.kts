plugins {
    id("gr8.build.common")
    id("gr8.build.publishing")
    id("java-gradle-plugin")
}

dependencies {
    implementation(project(":plugin-common")).excludeKotlinStdlib()
}

fun Dependency?.excludeKotlinStdlib() {
    (this as? ExternalModuleDependency)?.apply {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

val name = "Gr8 Plugin External"
val description = "The Gr8 Plugin packaged with external dependencies"

gr8Publishing {
    configurePublications(name, description)
}
gradlePlugin {
    plugins {
        create("gr8") {
            id = "com.gradleup.gr8.external"
            implementationClass = "com.gradleup.gr8.Gr8Plugin"
        }
    }
}


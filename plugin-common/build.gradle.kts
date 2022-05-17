plugins {
    id("gr8.build.common")
    id("gr8.build.publishing")
}

dependencies {
    api("dev.gradleplugins:gradle-api:6.0")
    implementation("net.mbonnin.r8:r8:3.2.49")
}

val name = "Gr8 Plugin Common"
val description = "The Gr8 Plugin bytecode with no plugin declaration"

gr8Publishing {
    configurePublications(name, description)
}

publishing.publications.create("default", MavenPublication::class.java) {
    from(components.getByName("java"))
}
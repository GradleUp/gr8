plugins {
    id("gr8.build.common").apply(false)
}
repositories {
    mavenCentral()
}

tasks.register("ci")
plugins {
    id("gr8.build.common").apply(false)
}
repositories {
    mavenCentral()
}

fun isTag(): Boolean {
    val ref = System.getenv("GITHUB_REF")

    return ref?.startsWith("refs/tags/") == true
}

//tasks.register("ci") {
//    dependsOn("build")
//    if (isTag()) {
//        dependsOn("publishAllPublicationsToOssStagingRepository")
//        dependsOn("publishPlugins")
//    }
//}
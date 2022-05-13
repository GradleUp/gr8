plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("net.mbonnin.vespene:vespene-lib:0.5")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
    implementation("com.gradle.publish:plugin-publish-plugin:0.15.0")
    implementation("com.gradleup:gr8-plugin:0.4")
}
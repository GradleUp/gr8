dependencyResolutionManagement {
  this.versionCatalogs {
    this.create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

apply(from = "../gradle/repositories.gradle.kts")

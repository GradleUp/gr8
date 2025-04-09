# Next version (unreleased)

# Version 0.11.1
_2024-11-25_

# Version 0.11.0
_2024-11-25_


## Configurable R8 version

Gr8 0.11.0 uses R8 8.5.35 by default.

You can now override this version. 

Using the `https://storage.googleapis.com/r8-releases/raw` repository ([doc](https://r8.googlesource.com/r8)):

```kotlin
repositories {
  maven("https://storage.googleapis.com/r8-releases/raw")
}

gr8 {
  create("default") {
    r8Version("8.8.19")
    //...
  }
}
```

Gr8 can also download a R8 jar from a git sha1:

```kotlin
gr8 {
  create("default") {
    r8Version("887704078a06fc0090e7772c921a30602bf1a49f")
    //...
  }
}
```

## [BREAKING] Artifact transform

Gr8 now uses an [artifact transform](https://docs.gradle.org/current/userguide/artifact_transforms.html) to filter the input jars:

```kotlin
gr8 {
  registerFilterTransform(listOf(".*/impldep/META-INF/versions/.*"))
}
```

As a consequence, `Gr8Configurator.configuration(String)` and `Gr8Configurator.classPathConfiguration(String)` are removed and replaced by equivalent APIs accepting files:

```kotlin
gr8 {
  create("default") {
    // Replace
    configuration("shadowedDependencies")
    // With
    addProgramJarsFrom(configurations.getByName("shadowedDependencies"))
    
    // Replace
    stripGradleApi(true)
    classPathConfiguration("compileOnlyDependenciesForGr8")
    
    // With
    val compileOnlyDependenciesForGr8 = configurations.create("compileOnlyDependenciesForGr8") {
      attributes {
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, FilterTransform.artifactType)
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
      }
    }
    registerFilterTransform(listOf(".*/impldep/META-INF/versions/.*"))
    addClassPathJarsFrom(compileOnlyDependenciesForGr8)
  }
}
```

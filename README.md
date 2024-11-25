# Gr8 [![Maven Central](https://img.shields.io/maven-central/v/com.gradleup/gr8-plugin?style=flat-square)](https://central.sonatype.com/namespace/com.gradleup)

Gr8 is [Gradle](https://gradle.org/) + [R8](https://r8.googlesource.com/r8). 

Gr8 makes it easy to shadow, shrink, and minimize your jars. 

## Motivation

Gradle has a [very powerful plugin system](https://r8.googlesource.com/r8). Unfortunately, [Gradle handling of classpath/Classloaders](https://dev.to/autonomousapps/build-compile-run-a-crash-course-in-classpaths-f4g) for plugins has some serious limitations. For an example:

* Gradle always [forces its bundled version of the Kotlin stdlib in the classpath](https://github.com/gradle/gradle/issues/16345). This makes it impossible to use Kotlin 1.5 APIs with Gradle 7.1 for an example because Gradle 7.1 uses Kotlin 1.4 (See [compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html) for other versions).
* [`buildSrc` dependencies leak in the classpath](https://github.com/gradle/gradle/issues/8301). This causes [very weird bugs](https://github.com/apollographql/apollo-android/issues/2939) during execution because a conflicting dependency might be forced in the classpath. This happens especially with popular libraries such as `okio` or `antlr` that are likely to be used with conflicting versions by different plugins in your build.

By shadowing (embedding and relocating) the plugin dependencies, it is possible to ship a plugin and all its dependencies without having to worry about what other dependencies are on the classpath, including the Kotlin stdlib.

To learn more, read the ["Use latest Kotlin in your Gradle plugins"](https://mbonnin.net/2021-11-12_use-latest-kotlin-in-your-gradle-plugins/) blog post.

## Usage

```kotlin
plugins {
  id("org.jetbrains.kotlin.jvm").version("$latestKotlinVersion")
  id("com.gradleup.gr8").version("$gr8Version")
}

dependencies {
  implementation("com.squareup.okhttp3:okhttp:4.9.0")
  // More dependencies here
}

/**
 * Create a separate configuration to resolve compileOnly dependencies.
 * You can skip this if you have no compileOnly dependencies. 
 */
val compileOnlyDependenciesForGr8: Configuration = configurations.create("compileOnlyDependenciesForGr8") {
  /**
   * Only get the jars needed for compilation, no need to resolve implementations
   */
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
  }
}
compileOnlyDependenciesForGr8.extendsFrom(configurations.getByName("compileOnly"))

gr8 {
  val shadowedJar = create("gr8") {
    // program jars are included in the final shadowed jar
    addProgramJarsFrom(configurations.getByName("runtimeClasspath"))
    addProgramJarsFrom(tasks.getByName("jar"))
    // classpath jars are only used by R8 for analysis but are not included in the
    // final shadowed jar.
    addClassPathJarsFrom(compileOnlyDependenciesForGr8)
    proguardFile("rules.pro")

    // Use a version from https://storage.googleapis.com/r8-releases/raw
    // Requires a maven("https://storage.googleapis.com/r8-releases/raw") repository
    r8Version("8.8.19")
    // Or use a commit
    // The jar is downloaded on demand
    r8Version("887704078a06fc0090e7772c921a30602bf1a49f")
    // Or leave it to the default version 
  }
  
  // Optional: replace the regular jar with the shadowed one in the publication
  replaceOutgoingJar(shadowedJar)

  // Or if you prefer the shadowed jar to be a separate variant in the default publication
  // The variant will have `org.gradle.dependency.bundling = shadowed`
  addShadowedVariant(shadowedJar)
}
```

Then customize your proguard rules. The below is a non-exhaustive example. If you're using reflection, you might need more rules 

```
# Keep your public API so that it's callable from scripts
-keep class com.example.** { *; }

# Repackage other classes
-repackageclasses com.example.relocated

# Allows more aggressive repackaging 
-allowaccessmodification

# We need to keep type arguments for Gradle to be able to instantiate abstract models like `Property`
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable
```

## Using Gr8 for Gradle plugins


The `java-gradle-plugin` automatically adds `api(gradleApi())` to your dependencies, exposing `gradleApi()` in your runtime classpath by default.

`gradleApi()` must not be embedded in your shadowed plugin as those classes are provided by the Gradle instance running your plugin.

What's more, contains `gradleApi()` newer symbols that might not be available in older versions of Gradle your plugin needs to support. 

To workaround this, you can use the [Nokee relocated artifacts](https://docs.nokee.dev/manual/gradle-plugin-development-plugin.html) and `removeGradleApiFromApi()`:

```kotlin
dependencies {
  compileOnly("dev.gradleplugins:gradle-api:7.6")
}
val compileOnlyDependenciesForGr8: Configuration = configurations.create("compileOnlyDependenciesForGr8") {
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
  }
}

compileOnlyDependenciesForGr8.extendsFrom(configurations.getByName("compileOnly"))

gr8 {
  create("default") {
    addClassPathJarsFrom(compileOnlyDependenciesForGr8)
    // ...
  }
  removeGradleApiFromApi()
}
```

At the time of writing [R8 doesn't support multi-release jars](https://issuetracker.google.com/u/1/issues/380805015) and you might bump into this issue:

```
Caused by: com.android.tools.r8.errors.CompilationError: Class content provided for type descriptor 
org.gradle.internal.impldep.META-INF.versions.15.org.bouncycastle.jcajce.provider.asymmetric.edec.SignatureSpi 
actually defines class org.gradle.internal.impldep.org.bouncycastle.jcajce.provider.asymmetric.edec.SignatureSpi
```

To workaround, Gr8 provides an artifact transform that can remove the multi-release classes. To do so, use `registerFilterTransform`:

```kotlin
val compileOnlyDependenciesForGr8: Configuration = configurations.create("compileOnlyDependenciesForGr8") {
  attributes {
    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, FilterTransform.artifactType)
  }
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_API))
  }
}

gr8 {
  registerFilterTransform(listOf(".*/impldep/META-INF/versions/.*"))
}
```

## Kotlin interop

By default, R8 removes `kotlin.Metadata` from the shadowed jar. This means the Kotlin compiler only sees plain Java classes and symbols and Kotlin-only features such as parameters default values, extension function, etc... are lost.

If you want to keep them, you need to keep `kotlin.Metadata` and `kotlin.Unit`:

```
# Keep kotlin metadata so that the Kotlin compiler knows about top level functions
-keep class kotlin.Metadata { *; }
# Keep Unit as it's in the signature of public methods:
-keep class kotlin.Unit { *; }
```

> [!NOTE]
> Stripping kotlin.Metadata acts as a compile-time verification that your API is usable in Groovy as it is in Kotlin and might be beneficial.

## Java runtime version

You can specify the version of the java runtime to use with `systemClassesToolchain`:

```
gr8 {
  val shadowedJar = create("gr8") {
    proguardFile("rules.pro")
    addProgramJarsFrom(configurations.getByName("runtimeClasspath"))
    systemClassesToolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
  }
}
```

## FAQ

**Could I use the Shadow plugin instead?**

The [Gradle Shadow Plugin](https://imperceptiblethoughts.com/shadow/) has been [helping plugin authors](https://www.alecstrong.com/posts/shading/) for years and is a very stable solution. Unfortunately, it doesn't allow very granular configuration and [might relocate constant strings that shouldn't be](https://github.com/johnrengelman/shadow/issues/232). In practice, any plugin that tries to read the `"kotlin"` extension is subject to having its behaviour changed:

```kotlin
project.extensions.getByName("kotlin")
```

will be transformed to:

```kotlin
project.extensions.getByName("com.relocated.kotlin")
```

For plugins that generate source code and contain a lot of package names, this might be even more unpredictable and require weird [workarounds](https://github.com/apollographql/apollo-android/blob/f72c3afd17655591aca90a6a118dbb7be9c50920/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/codegen/kotlin/OkioJavaTypeName.kt#L19).

By using `R8` and [proguard rules](https://www.guardsquare.com/manual/configuration/usage), `Gr8` makes relocation more predictable and configurable.

**Could I use the Gradle Worker API instead?** 

The [Gradle Worker API](https://docs.gradle.org/current/userguide/worker_api.html) has a [classLoaderIsolation mode](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.workers/-worker-executor/class-loader-isolation.html) that can be used to achieve a similar result with some limitations:
* `gradle-api` and `kotlin-stdlib` are still in the worker classpath meaning you need to make sure your Kotlin version is compatible.
* [classLoaderIsolation leaks memory](https://github.com/gradle/gradle/issues/18313)
* Workers require serializing parameters and writing more boilerplate code.

**Are there any drawbacks?**

Yes. Because every plugin now relocates its own version of `kotlin-stdlib`, `okio` and other dependencies, it means more work for the Classloaders and more Metaspace being used. There's a risk that builds will use more memory, although it hasn't been a big issue so far.


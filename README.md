# Gr8 ![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.gradleup.gr8)

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
  id("java-gradle-plugin")
  id("com.gradleup.gr8").version("$gr8Version")
}

dependencies {
  // Use latest Koltin stdlib version
  // Also set kotlin.stdlib.default.dependency=false in gradle.properties to avoid the 
  // plugin to add it to the "api" configuration
  implementation("org.jetbrains.kotlin:kotlin-stdlib")
  implementation("com.squareup.okhttp3:okhttp:4.9.0")
}

gr8 {
  val shadowedJar = create("gr8") {
    addProgramJarsFrom(configurations.getByName("runtimeClasspath"))
    addProgramJarsFrom(tasks.getByName("jar"))
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

# Allow to make some classes public so that we can repackage them without breaking package-private members
-allowaccessmodification

# We need to keep type arguments for Gradle to be able to instantiate abstract models like `Property`
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable
```

##  


## FAQ

**Could I use the Shadow plugin instead?**

The [Gradle Shadow Plugin](https://imperceptiblethoughts.com/shadow/) has been [helping plugin authors](https://www.alecstrong.com/posts/shading/) for years and is a very stable solution. Unfortunately, it doesn't allow very granular configuration and [might relocate constant strings that shouldn't be](https://github.com/johnrengelman/shadow/issues/232). In practice, any plugin that tries to read the `"kotlin"` extension is subject to having its behaviour changed:

```kotlin
project.extensions.getByName("kotlin")
}
```

will be transformed to:

```kotlin
project.extensions.getByName("com.relocated.kotlin")
```

For plugins that generate source code and contain a lot of package names, this might be even more unpredictable and require weird [workarounds](https://github.com/apollographql/apollo-android/blob/f72c3afd17655591aca90a6a118dbb7be9c50920/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/codegen/kotlin/OkioJavaTypeName.kt#L19).

By using `R8` and [proguard rules](https://www.guardsquare.com/manual/configuration/usage), `Gr8` makes relocation more predictable and configurable.

**Can I override the system classes used by `R8`, like target JDK 11 with my plugin while building on Java 17?**

If you set your Java toolchain then R8 will also use the same toolchain to discover system classes:

```
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}
```

If for some reason you want to override this explicitly:

```
gr8 {
  val shadowedJar = create("gr8") {
    proguardFile("rules.pro")
    configuration("shade")
    systemClassesToolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
  }
}
```

**Could I use the Gradle Worker API instead?** 

The [Gradle Worker API](https://docs.gradle.org/current/userguide/worker_api.html) has a [classLoaderIsolation mode](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.workers/-worker-executor/class-loader-isolation.html) that can be used to achieve a similar result with some limitations:
* `gradle-api` and `kotlin-stdlib` are still in the worker classpath meaning you need to make sure your Kotlin version is compatible.
* [classLoaderIsolation leaks memory](https://github.com/gradle/gradle/issues/18313)
* Workers require serializing parameters and writing more boilerplate code.

**Are there any drawbacks?**

Yes. Because every plugin now relocates its own version of `kotlin-stdlib`, `okio` and other dependencies, it means more work for the Classloaders and more Metaspace being used. There's a risk that builds will use more memory, although it hasn't been a big issue so far.


**Can I use Kotlin features in my plugin? (parameter names, extension functions, top level properties, etc...)

It depends. If your plugin public API exposes Kotlin-only helper functions/symbols, you will need to keep `kotlin.Metadata` and `kotlin.Unit` and make sure your language version is compatible with the Gradle embedded version. This is so that Gradle doesn't error when trying to compile Kotlin build scripts.

In general, I would recommend avoiding Kotlin-only features in your plugin API for better groovy/Java interoperability but if you really want to, it is possible. 
# Gr8

Gr8 is [Gradle](https://gradle.org/) + [R8](https://r8.googlesource.com/r8). 

Gr8 makes it easy to shadow, shrink, and minimize your jars. 

## Motivation

Gradle has a [very powerful plugin system](https://r8.googlesource.com/r8). Unfortunately, [Gradle handling of classpath/Classloaders](https://dev.to/autonomousapps/build-compile-run-a-crash-course-in-classpaths-f4g) for plugins has some serious limitations. For an example:

* Gradle will always [force its bundled version of the Kotlin stdlib in the classpath](https://github.com/gradle/gradle/issues/16345). This makes it impossible to use Kotlin 1.5 APIs with Gradle 7.1 for an example because Gradle 7.1 uses Kotlin 1.4.
* [`buildSrc` dependencies leak in the classpath](https://github.com/gradle/gradle/issues/8301). This causes [very weird bugs](https://github.com/apollographql/apollo-android/issues/2939) during execution because a conflicting dependency might be forced in the classpath. This happens espectially with popular libraries such as `okio` or `antlr`.

By shadowing and relocating the plugin dependencies, it is possible to ship a plugin and all its dependencies without having to worry about what Gradle is going to put on the classpath. 

As a nice bonus, it makes plugins standalone so consumers of your plugin don't need to declare additional repositories. The `gr8` plugin for an example, uses `R8` from the [Google repo](https://maven.google.com/web/index.html) although it makes it available directly from the preconfigured [Gradle plugin portal](https://plugins.gradle.org/).

## Usage

To make a shadowed Gradle plugin:

```kotlin
plugins {
  id("org.jetbrains.kotlin.jvm").version("$kotlinVersion")
  id("java-gradle-plugin")
  id("com.gradleup.gr8").version("$gr8Version")
}

// Configuration dependencies that will be shadowed
val shadeConfiguration = configurations.create("shade")

dependencies {
  // Using a redistributed version of Gradle instead of `gradleApi` provides more flexibility
  // See https://github.com/gradle/gradle/issues/1835
  compileOnly("dev.gradleplugins:gradle-api:7.1.1")

  // Also set kotlin.stdlib.default.dependency=false in gradle.properties to avoid the 
  // plugin to add it to the "api" configuration
  add("shade", "org.jetbrains.kotlin:kotlin-stdlib")
  add("shade", "com.squareup.okhttp3:okhttp:4.9.0")
}

gr8 {
  val shadowedJar = create("gr8") {
    proguardFile("rules.pro")
    configuration("shade")
  }
  // Replace the regular jar with the shadowed one in the publication
  replaceOutgoingJar(shadowedJar)

  // Make the shadowed dependencies available during compilation/tests
  configurations.named("compileOnly").configure {
    extendsFrom(shadeConfiguration)
  }
  configurations.named("testImplementation").configure {
    extendsFrom(shadeConfiguration)
  }
}
```

Then customize your proguard rules. The below is the bare minimum. If you're using reflection, you might need more rules 

```
# The Gradle API jar isn't added to the classpath, ignore the missing symbols
-ignorewarnings
# Allow to make some classes public so that we can repackage them without breaking package-private members
-allowaccessmodification

# Keep kotlin metadata so that the Kotlin compiler knows about top level functions and other things
-keep class kotlin.Metadata { *; }

# We need to keep type arguments (Signature) for Gradle to be able to instantiate abstract models like `Property`
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable

# Keep your public API so that it's callable from scripts
-keep class com.example.** { *; }

-repackageclasses com.example.relocated

```

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


**Could I use the Gradle Worker API instead?** 

Yes, the [Gradle Worker API](https://docs.gradle.org/current/userguide/worker_api.html) ensures proper plugin isolation. It only works for task actions and requires some setup so shadowing/relocating is a more universal solution.

**Are there any drawbacks?**

Yes. Because every plugin now relocates its own version of `kotlin-stdlib`, `okio` and other dependendancies, it means more work for the Classloaders and more Metaspace being used. There's a risk that builds will use more memory although it hasn't been a big issue so far.


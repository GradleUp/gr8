package com.gradleup.gr8

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec

open class Gr8Configurator(
  private val name: String,
  private val project: Project,
  private val javaToolchainService: JavaToolchainService,
) {
  private val programJars = project.objects.fileCollection()

  private val excludes: ListProperty<String> = project.objects.listProperty(String::class.java)
  private val classPathExcludes: ListProperty<String> = project.objects.listProperty(String::class.java)
  private val javaCompiler: Property<JavaCompiler> = project.objects.property(JavaCompiler::class.java)
  private val proguardFiles = project.objects.fileCollection()
  private var classPathJars = project.objects.fileCollection()

  val defaultR8Version = "8.7.18"

  private var r8Version_: String? = null

  private val buildDir = project.layout.buildDirectory.dir("gr8/$name").get().asFile

  init {
    val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
    if (javaExtension != null) {
      javaCompiler.convention(javaToolchainService.compilerFor(javaExtension.toolchain))
    }
    excludes.convention(null as List<String>?)
    classPathExcludes.convention(null as List<String>?)
  }

  fun r8Version(r8Version: String) {
    r8Version_ = r8Version
  }

  /**
   * The configuration to shadow in the resulting output jar.
   */
  @Deprecated("use addProgramJarsFrom(configurations.getByName(\"name\") instead", replaceWith = ReplaceWith("addProgramJarsFrom(configurations.getByName(\"name\")"), level = DeprecationLevel.ERROR)
  fun configuration(name: String): Unit = TODO()

  /**
   * The jar file to include in the resulting output jar.
   *
   * Default: the jar produced by the "jar" task
   *
   * @param file a file that will be evaluated like [Project.file]. If this file is created by another task, use a provider
   * so that the dependency between the task can be set up
   */
  fun addProgramJarsFrom(file: Any) {
    programJars.from(file)
  }

  /**
   * Adds additional jars on the classpath (but not in the output jar).
   */
  @Deprecated("use addClassPathJarsFrom(configurations.getByName(\"name\") instead", replaceWith = ReplaceWith("addClassPathJarsFrom(configurations.getByName(\"name\")"), level = DeprecationLevel.ERROR)
  fun classPathConfiguration(name: String): Unit = TODO()

  /**
   * Adds additional jars on the classpath (but not in the output jar).
   *
   * @param files files to add, evaluated as in [Project.file].
   */
  fun addClassPathJarsFrom(files: Any) {
    classPathJars.from(files)
  }

  /**
   * Adds the given file as a proguard-like configuration file
   *
   * @param file a file that will be evaluated like [Project.file]
   */
  fun proguardFile(file: Any) {
    proguardFiles.from(file)
  }

  @Deprecated("exclude is not supported anymore, use addProgramJarsFrom(fileCollection) and filter your fileCollection. See also FilterTransform")
  fun exclude(exclude: String) {
    this.excludes.add(exclude)
  }

  /**
   * The java compiler toolchain to use for discovering system classes when running R8
   *
   * The system classes from this Java toolchain will be passed to R8 when invoking it, enabling you
   * to target any version of Java toolchain while building on newer JDKs. Defaults to the toolchain used to compile.
   *
   * Usage:
   *
   * ```
   * systemClassesToolchain(javaToolchains.compilerFor {
   *   languageVersion.set(JavaLanguageVersion.of(11))
   * })
   * ```
   */
  fun systemClassesToolchain(compiler: JavaCompiler) {
    this.javaCompiler.set(compiler)
  }

  /**
   * The java compiler toolchain to use for discovering system classes when running R8, defaults to the Java extension
   * toolchain (also used for compiling your classes)
   *
   * The system classes from this Java toolchain will be passed to R8 when invoking it, enabling you
   * to target any version of Java toolchain while building on newer JDKs. Defaults to the toolchain used to compile.
   *
   * Usage:
   *
   * ```
   * systemClassesToolchain {
   *   languageVersion.set(JavaLanguageVersion.of(11))
   * }
   * ```
   */
  fun systemClassesToolchain(spec: Action<JavaToolchainSpec>) {
    this.javaCompiler.set(javaToolchainService.compilerFor(spec))
  }

  internal fun registerTasks(): TaskProvider<Gr8Task> {
    val upperCaseName = name.replaceFirstChar { it.uppercase() }

    val r8Version = r8Version_ ?: defaultR8Version
    val classpath = if (r8Version.contains('.')) {
      /**
       * If the version contains a '.', we're going to assume it is present in the Google maven repo and
       * use proper Gradle dependency management.
       */
      val gr8Configuration = project.configurations.create("gr8$upperCaseName") {
        it.isCanBeResolved = true
      }
      gr8Configuration.dependencies.add(project.dependencies.create("com.android.tools:r8:$r8Version"))
      gr8Configuration
    } else {
      /**
       * If the version doesn't contain a '.', it's probably a git sha that is not present in the Google maven
       * repo. In that case, we download it from the GCP bucket directly.
       */
      project.tasks.register("gr8${upperCaseName}Download", DownloadR8Task::class.java) {
        it.sha1.set(r8Version)
        it.outputFile.set(buildDir.resolve("r8/$r8Version.jar"))
      }
    }


    val r8TaskProvider = project.tasks.register("gr8${upperCaseName}ShadowedJar", Gr8Task::class.java) { task ->
      task.classpath(classpath)

      task.mainClass.set("com.android.tools.r8.R8")

      task.programFiles.from(programJars)
      task.mapping.set(buildDir.resolve("mapping.txt"))
      task.classPathFiles.from(classPathJars)
      task.proguardConfigurationFiles.from(proguardFiles)

      task.outputJar.set(buildDir.resolve("${project.name}-${project.version}-shadowed.jar"))

      task.javaCompiler.set(javaCompiler)
    }

    return r8TaskProvider
  }
}

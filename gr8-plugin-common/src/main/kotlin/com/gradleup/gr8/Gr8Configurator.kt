package com.gradleup.gr8

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import javax.inject.Inject

open class Gr8Configurator(
    private val name: String,
    private val project: Project,
    private val javaToolchainService: JavaToolchainService,
) {
  private val programJar: RegularFileProperty = project.objects.fileProperty()

  private val excludes: ListProperty<String> = project.objects.listProperty(String::class.java)
  private val javaCompiler: Property<JavaCompiler> = project.objects.property(JavaCompiler::class.java)
  private val proguardFiles = project.objects.fileCollection()

  val defaultR8Version = "8.5.35"
  private var r8Version_: String = defaultR8Version
  private var configuration: String? = null
  private var classPathConfiguration: String? = null

  private val buildDir = project.layout.buildDirectory.dir("gr8/$name").get().asFile


  init {
    val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
    if (javaExtension != null) {
      javaCompiler.convention(javaToolchainService.compilerFor(javaExtension.toolchain))
    }
    excludes.convention(null as List<String>?)
  }

  fun r8Version(r8Version: String) {
    r8Version_ = r8Version
  }

  /**
   * The configuration to shadow in the resulting output jar.
   */
  fun configuration(name: String) {
    configuration = name
  }


  @Deprecated("Use `programJar(Provider<RegularFile>)` or `programJar(TaskProvider<Task>)`", level = DeprecationLevel.ERROR)
  fun programJar(file: Any) {
    TODO()
  }

  /**
   * The jar file to include in the resulting output jar.
   *
   * Default: the jar produced by the "jar" task
   *
   * @param file a file that will be evaluated like [Project.file]. If this file is created by another task, use a provider
   * so that the dependency between the task can be set up
   */
  fun programJar(file: Provider<RegularFile>) {
    this.programJar.set(file)
  }

  /**
   * The jar file to include in the resulting output jar.
   *
   * Default: the jar produced by the "jar" task
   *
   * @param taskProvider a file that will be evaluated like [Project.file]. If this file is created by another task, use a provider
   * so that the dependency between the task can be set up
   */
  fun programJar(taskProvider: TaskProvider<Task>) {
    programJar(
        taskProvider.flatMap { task ->
          check(task is AbstractArchiveTask) {
            "only AbstractArchiveTasks like Jar or Zip are supported"
          }
          task.archiveFile
        }
    )
  }


  /**
   * Adds additional jars on the classpath (but not in the output jar).
   */
  fun classPathConfiguration(name: String) {
    classPathConfiguration = name
  }

  /**
   * Adds the given file as a proguard-like configuration file
   *
   * @param file a file that will be evaluated like [Project.file]
   */
  fun proguardFile(file: Any) {
    proguardFiles.from(file)
  }

  /**
   * Adds the given ANT pattern as an exclusion in the resulting jar.
   *
   * By default, MANIFEST.MF, proguard files and module-info.class are excluded
   */
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

  private fun defaultProgramJar(): Provider<RegularFile> {
    return project.tasks.named("jar").flatMap {
      (it as Jar).archiveFile
    }
  }

  internal fun registerTasks(): TaskProvider<Gr8Task> {
    /**
     * The pipeline is:
     * - Patch the Kotlin stdlib to make DefaultConstructorMarker not-public again. This is to prevent R8 to rewrite
     * Class.forName("kotlin.jvm.internal.DefaultConstructorMarker") to a constant pool reference that will make a runtime
     * exception if used with Kotlin 1.4 at runtime
     * - Take all jars and build a big embedded Jar, keeping all META-INF files and only the MANIFEST from the main jar
     * - Strip some Java9 files from gradle-api because it triggers
     * com.android.tools.r8.errors.CompilationError: Class content provided for type descriptor org.gradle.internal.impldep.META-INF.versions.9.org.junit.platform.commons.util.ModuleUtils actually defines class org.gradle.internal.impldep.org.junit.platform.commons.util.ModuleUtils
     * - Call R8 to generate the final jar
     */

    val configuration = project.configurations.getByName(configuration ?: error("Calling gr8 { configuration() } is required"))

    val embeddedJarProvider = project.tasks.register("embedJar", Zip::class.java) {
      val archiveOperations = project.archiveOperations()
      val objects = project.objects

      it.from(configuration.elements.map { fileSystemLocations ->
        objects.fileCollection().apply {
          fileSystemLocations.forEach {
            from(archiveOperations.zipTree(it.asFile))
          }
        }
      })

      // The jar is mostly empty but this is needed for the plugin descriptor + module-info
      it.from(programJar.orElse(defaultProgramJar()).map { archiveOperations.zipTree(it.asFile) })

      /*
       * Exclude libraries R8 rules, we'll add them ourselves
       */
      it.exclude(excludes.getOrElse(listOf("META-INF/MANIFEST.MF", "META-INF/**/*.pro", "module-info.class", "META-INF/versions/*/module-info.class")))

      it.duplicatesStrategy = DuplicatesStrategy.WARN

      it.destinationDirectory.set(buildDir)
      it.archiveFileName.set("embedded.jar")
    }

    val gr8Configuration = project.configurations.create("gr8") {
      it.isCanBeResolved = true
    }
    gr8Configuration.dependencies.add(project.dependencies.create("com.android.tools:r8:$defaultR8Version"))

    val downloadR8 = project.tasks.register("downloadR8", DownloadR8Task::class.java) {
      it.sha1.set(r8Version_)
      it.outputFile.set(buildDir.resolve("r8/$r8Version_.jar"))
    }

    val r8TaskProvider = project.tasks.register("${name}R8Jar", Gr8Task::class.java) { task ->
      if (r8Version_.contains('.')) {
        task.classpath(gr8Configuration)
      } else {
        task.classpath(downloadR8)
      }

      task.mainClass.set("com.android.tools.r8.R8")

      task.programFiles.from(embeddedJarProvider)

      task.mapping.set(buildDir.resolve("mapping.txt"))
      if (classPathConfiguration != null) {
        task.classPathFiles.from(project.configurations.getByName(classPathConfiguration!!))
      }

      task.outputJar.set(buildDir.resolve("${project.name}-${project.version}-shadowed.jar"))
      task.proguardConfigurationFiles.from(proguardFiles)

      task.javaCompiler.set(javaCompiler)
    }

    return r8TaskProvider
  }
}

internal abstract class Holder2 @Inject constructor(val operations: ArchiveOperations)

private fun Project.archiveOperations(): ArchiveOperations {
  return objects.newInstance(Holder2::class.java).operations
}

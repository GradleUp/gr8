package com.gradleup.gr8

import com.gradleup.gr8.StripGradleApiTask.Companion.isGradleApi
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import java.io.File

open class Gr8Configurator(
    private val name: String,
    private val project: Project,
) {
  private var programJar: Property<Any> = project.objects.property(Any::class.java)
  private var configuration: Property<String> = project.objects.property(String::class.java)
  private var archiveName: Property<String> = project.objects.property(String::class.java)
  private var classPathConfiguration: Property<String> = project.objects.property(String::class.java)
  private var proguardFiles = mutableListOf<Any>()
  private var stripGradleApi: Property<Boolean> = project.objects.property(Boolean::class.java)
  private var excludes: ListProperty<String> = project.objects.listProperty(String::class.java)

  private val buildDir = project.layout.buildDirectory.dir("gr8/$name").get().asFile

  /**
   * The configuration to include in the resulting output jar.
   */
  fun configuration(name: String) {
    configuration.set(name)
    configuration.disallowChanges()
  }

  /**
   * The configuration to include in the resulting output jar.
   */
  fun archiveName(name: String) {
    archiveName.set(name)
    archiveName.disallowChanges()
  }

  /**
   * The jar file to include in the resulting output jar.
   *
   * Default: the jar produced by the "jar" task
   *
   * @param file a file that will be evaluated like [Project.file]. If this file is created by another task, use a provider
   * so that the dependency between the task can be set up
   */
  fun programJar(file: Any) {
    programJar.set(file)
    programJar.disallowChanges()
  }

  /**
   * See [programJar]
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
   * See [programJar]
   */
  fun programJar(task: Task) {
    check(task is AbstractArchiveTask) {
      "only AbstractArchiveTasks like Jar or Zip are supported"
    }

    programJar(task.archiveFile)
  }

  /**
   * Adds additional jars on the classpath (but not in the output jar).
   *
   * Default: "compileOnly"
   */
  fun classPathConfiguration(name: String) {
    classPathConfiguration.set(name)
  }

  /**
   * Adds the given file as a proguard-like configuration file
   *
   * @param file a file that will be evaluated like [Project.file]
   */
  fun proguardFile(file: Any) {
    proguardFiles.add(file)
  }

  /**
   * Adds the given file as a proguard-like configuration file
   *
   * @param file a file that will be evaluated like [Project.file]
   */
  fun proguardFiles(vararg file: Any) {
    proguardFiles.addAll(file)
  }

  /**
   * The gradle-api jar triggers errors in R8
   *
   * Class content provided for type descriptor org.gradle.internal.impldep.META-INF.versions.9.org.junit.platform.commons.util.ModuleUtils$ModuleReferenceScanner actually defines class org.gradle.internal.impldep.org.junit.platform.commons.util.ModuleUtils$ModuleReferenceScanner
   *
   * Setting stripGradleApi(true) will strip these classes
   */
  fun stripGradleApi(strip: Boolean) {
    stripGradleApi.set(strip)
  }

  fun exclude(exclude: String) {
    this.excludes.add(exclude)
  }

  private fun defaultProgramJar(): Provider<File> {
    return project.tasks.named("jar").flatMap {
      (it as Jar).archiveFile
    }.map {
      it.asFile
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

    val configuration = project.configurations.getByName(configuration.orNull
        ?: error("shadeConfiguration is mandatory"))

    val embeddedJarProvider = project.tasks.register("${name}EmbeddedJar", EmbeddedJarTask::class.java) { task ->
      task.excludes.set(excludes)
      task.mainJar(programJar.map { project.file(it) }.orElse(defaultProgramJar()))
      task.otherJars(configuration)
      task.outputJar(buildDir.resolve("embedded.jar"))
    }

    val classPathFiles = project.files()
    if (classPathConfiguration.isPresent) {
      val stripGradleApi = stripGradleApi.getOrElse(false)

      val classPathConfiguration = project.configurations.getByName(classPathConfiguration.get())
      classPathFiles.from(classPathConfiguration.filter {
        !stripGradleApi || !it.isGradleApi()
      })

      if (stripGradleApi) {
        val stripGradleApiTask = project.tasks.register("${name}StripGradleApi", StripGradleApiTask::class.java) {
          it.gradleApiJar(classPathConfiguration)
          it.strippedGradleApiJar(buildDir.resolve("gradle-api-stripped.jar"))
        }
        classPathFiles.from(stripGradleApiTask.flatMap { it.strippedGradleApiJar() })
      } else {
        classPathFiles.from(classPathConfiguration.filter { it.isGradleApi() })
      }
    }

    val r8TaskProvider = project.tasks.register("${name}R8Jar", Gr8Task::class.java) { task ->
      task.programFiles(embeddedJarProvider.flatMap { it.outputJar() })

      task.mapping(File(buildDir, "mapping.txt"))
      task.classPathFiles(classPathFiles)

      val archiveName = archiveName.getOrElse("${project.name}-${project.version}-shadowed.jar")
      task.outputJar(File(buildDir, archiveName))
      task.proguardConfigurationFiles.from(proguardFiles.toTypedArray())
    }

    return r8TaskProvider
  }
}
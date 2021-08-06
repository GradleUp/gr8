package com.gradleup.gr8

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFile
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
  private var classPathConfiguration: Property<String> = project.objects.property(String::class.java)
  private var proguardFiles = mutableListOf<Any>()
  private var classifier: Property<String> = project.objects.property(String::class.java)

  /**
   * The configuration to include in the resulting output jar.
   */
  fun configuration(name: String) {
    configuration.set(name)
    configuration.disallowChanges()
  }

  /**
   * The classifier to use for the output jar.
   *
   * Default: "shadowed"
   */
  fun classifier(name: String) {
    classifier.set(name)
    classifier.disallowChanges()
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

  private fun defaultProgramJar(): Provider<File> {
    return project.tasks.named("jar").flatMap {
      (it as Jar).archiveFile
    }.map {
      it.asFile
    }
  }

  private fun Jar.fromJarProvider(jarProvider: Provider<File>) {
    from(jarProvider.map { project.zipTree(it) })
  }

  private fun Jar.fromConfiguration(configuration: Configuration) {
    from(
        project.provider {
          configuration.map { project.zipTree(it) }
        }
    ) {
      // This creates duplicates
      it.excludes.add("META-INF/versions/9/module-info.class")
    }
  }

  internal fun registerTask(): Provider<RegularFile> {
    val buildDir = project.layout.buildDirectory.dir("gr8/$name").get().asFile

    val fatJarTaskProvider = project.tasks.register("${name}EmbeddedJar", Jar::class.java) { task ->
      val jarProvider = programJar.map { project.file(it) }.orElse(defaultProgramJar())
      task.fromJarProvider(jarProvider)
      task.fromConfiguration(project.configurations.getByName(configuration.orNull ?: error("shadeConfiguration is mandatory")))

      task.manifest {
        it.from(jarProvider.map { project.zipTree(it).first { it.name == "MANIFEST.MF" } })
      }

      task.destinationDirectory.set(buildDir)
      task.archiveClassifier.set("embedded")
    }

    val gr8TaskProvider = project.tasks.register("${name}Gr8Jar", Gr8Task::class.java) { task ->
      task.programFiles.from(fatJarTaskProvider.flatMap { it.archiveFile })

      task.mapping.set(File(buildDir, "mapping.txt"))
      if (classPathConfiguration.isPresent) {
        task.classPathFiles.from(project.configurations.getByName(classPathConfiguration.get()))
      }
      task.output.set(File(buildDir, "r8-output.jar"))
      task.proguardConfigurationFiles.from(proguardFiles.toTypedArray())
    }

    val shadowed = project.tasks.register("${name}ShadowedJar", Jar::class.java) { task ->
      task.destinationDirectory.set(buildDir)
      task.archiveClassifier.set(classifier.getOrElse("shadowed"))
      task.from(gr8TaskProvider.flatMap { it.output.asFile.map { project.zipTree(it) } })
    }

    return shadowed.flatMap { it.archiveFile }
  }
}
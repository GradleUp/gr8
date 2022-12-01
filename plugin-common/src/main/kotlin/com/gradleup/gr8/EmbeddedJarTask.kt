package com.gradleup.gr8

import com.gradleup.gr8.ZipHelper.buildZip
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File

/**
 * A task that generates an embedded jar from a list of jars
 */
@CacheableTask
abstract class EmbeddedJarTask : DefaultTask() {
  @get:Classpath
  internal abstract val mainJar: RegularFileProperty

  @get:Classpath
  internal abstract val otherJars: ConfigurableFileCollection

  @get:Input
  internal abstract val excludes: ListProperty<String>

  @get:OutputFile
  internal abstract val outputJar: RegularFileProperty

  fun mainJar(fileProvider: Provider<File>) {
    mainJar.fileProvider(fileProvider)
    mainJar.disallowChanges()
  }

  fun otherJars(any: Any) {
    otherJars.setFrom(any)
    otherJars.disallowChanges()
  }

  fun outputJar(file: File) {
    outputJar.set(file)
    outputJar.disallowChanges()
  }

  fun outputJar(): Provider<RegularFile> = outputJar

  @TaskAction
  fun taskAction() {
    val regexes = excludes.getOrElse(emptyList()).map { Regex(it) }

    buildZip(outputJar.asFile.get()) {
      addZipFile(mainJar.asFile.get())

      otherJars.files.forEach {
        addZipFile(it) {
          if (regexes.any { it.matches(entry.name) }) {
            skip()
          }
        }
      }
    }
  }
}
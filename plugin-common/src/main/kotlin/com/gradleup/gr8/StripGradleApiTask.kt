package com.gradleup.gr8

import com.gradleup.gr8.ZipHelper.buildZip
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipInputStream

abstract class StripGradleApiTask : DefaultTask() {
  @get:InputFile
  @get:Optional
  internal abstract val gradleApiJar: RegularFileProperty

  @get:OutputFile
  internal abstract val strippedGradleApiJar: RegularFileProperty

  fun gradleApiJar(fileCollection: FileCollection) {
    gradleApiJar.set(
        project.layout.file(
            project.provider {
              fileCollection.files.single {
                //println(it.name)
                it.isGradleApi()
              }
            }
        )
    )
    gradleApiJar.disallowChanges()
  }

  fun gradleApiJar(file: File) {
    gradleApiJar.set(file)
    gradleApiJar.disallowChanges()
  }

  fun gradleApiJar(regularFileProperty: RegularFileProperty) {
    gradleApiJar.set(regularFileProperty)
    gradleApiJar.disallowChanges()
  }

  fun strippedGradleApiJar(): Provider<RegularFile> = strippedGradleApiJar

  fun strippedGradleApiJar(file: File) {
    strippedGradleApiJar.set(file)
    strippedGradleApiJar.disallowChanges()
  }

  fun strippedGradleApiJar(regularFileProperty: RegularFileProperty) {
    strippedGradleApiJar.set(regularFileProperty)
    strippedGradleApiJar.disallowChanges()
  }

  @TaskAction
  fun taskAction() {
    buildZip(strippedGradleApiJar.asFile.get()) {
      addZipFile(gradleApiJar.asFile.get()) {
        if (entry.name.startsWith("org/gradle/internal/impldep/META-INF/")) {
          skip()
        }
      }
    }
  }

  companion object {
    fun File.isGradleApi(): Boolean {
      // See https://github.com/GradleUp/gr8/issues/10, using the jar name only is not enough
      val isMaybeGradleApi = Regex("gradle-api-+[0-9.]*.jar").matches(name)
      if (!isMaybeGradleApi) {
        return false
      }

      return ZipInputStream(inputStream()).use {
        while (true) {
          val entry = it.nextEntry
          if (entry == null) {
            break
          }

          if (entry.name.contains("org/gradle/internal/impldep/META-INF/")) {
            return@use true
          }
        }
        false
      }
    }
  }
}
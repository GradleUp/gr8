package com.gradleup.gr8

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI

@CacheableTask // We want to keep the downloaded jar in local build cache if any
abstract class DownloadR8Task : DefaultTask() {
  @get:Input
  abstract val sha1: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun taskAction() {
    if (outputFile.get().asFile.exists()) {
      return
    }
    val url = "https://storage.googleapis.com/r8-releases/raw/main/${sha1.get()}/r8.jar"

    URI(url).toURL().openStream().buffered().use { inputStream ->
      outputFile.get().asFile.outputStream().use { outputStream ->
        inputStream.copyTo(outputStream)
      }
    }
  }
}

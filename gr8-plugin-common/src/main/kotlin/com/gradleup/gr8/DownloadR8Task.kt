package com.gradleup.gr8

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI

@CacheableTask // We want to keep the downloaded jar in local build cache if any
abstract class DownloadR8Task : DefaultTask() {
  @get:Input
  abstract val sha1: Property<String>

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun taskAction() {
    val outputFile = outputDir.get().asFile.resolve("${sha1.get()}.jar")
    outputDir.get().asFile.listFiles()!!.forEach {
      /**
       * Delete stale files
       */
      if (it != outputFile) {
        it.delete()
      }
    }
    if (outputFile.exists()) {
      // Already present, save some work
      return
    }
    val url = "https://storage.googleapis.com/r8-releases/raw/main/${sha1.get()}/r8.jar"

    URI(url).toURL().openStream().buffered().use { inputStream ->
      outputFile.outputStream().use { outputStream ->
        inputStream.copyTo(outputStream)
      }
    }
  }
}

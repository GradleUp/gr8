package com.gradleup.gr8

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class FilterTransform: TransformAction<FilterTransform.Parameters> {
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  @get:InputArtifact
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val file = inputArtifact.get().asFile
    val regexes = parameters.excludes.map { Regex(it) }
    ZipInputStream(file.inputStream()).use { inputStream ->
      ZipOutputStream(outputs.file("${file.nameWithoutExtension}-excluded.${file.extension}").outputStream()).use { outputStream ->
        var entry: ZipEntry? = inputStream.nextEntry
        while (entry != null) {
          if (regexes.none { it.matches(entry!!.name) }) {
            outputStream.putNextEntry(entry)
            inputStream.copyTo(outputStream)
          }
          entry = inputStream.nextEntry
        }
      }
    }
  }

  interface Parameters : TransformParameters {
    /**
     * A list of Regex patterns
     */
    @get:Input
    var excludes: List<String>
  }

  companion object {
    val artifactType = "filtered-jar"
  }
}

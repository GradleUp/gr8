package com.gradleup.gr8

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaCompiler

@CacheableTask
abstract class Gr8Task : JavaExec() {
  @get:Classpath
  internal abstract val programFiles: ConfigurableFileCollection

  @get:Classpath
  internal abstract val classPathFiles: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val proguardConfigurationFiles: ConfigurableFileCollection

  @get:OutputFile
  internal abstract val outputJar: RegularFileProperty

  @get:OutputFile
  internal abstract val mapping: RegularFileProperty

  @get:Nested
  @get:Optional
  internal abstract val javaCompiler: Property<JavaCompiler>

  override fun exec() {
    val javaHome = javaCompiler.get().metadata.installationPath.asFile.absolutePath

    args("--release")
    args("--classfile")
    args("--output")
    args(outputJar.get().asFile.absolutePath)
    args("--pg-map-output")
    args(mapping.get().asFile.absolutePath)

    classPathFiles.forEach { file ->
      args("--classpath")
      args(file.absolutePath)
    }

    proguardConfigurationFiles.forEach { file ->
      args("--pg-conf")
      args(file.absolutePath)
    }

    args("--lib")
    args(javaHome)

    programFiles.forEach {
      args(it.absolutePath)
    }

    super.exec()
  }
}
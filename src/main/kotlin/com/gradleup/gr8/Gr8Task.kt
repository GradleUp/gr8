package com.gradleup.gr8

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import kotlinx.metadata.jvm.jvmInternalName
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import java.io.File

abstract class Gr8Task : DefaultTask() {
  @get:InputFiles
  internal abstract val programFiles: ConfigurableFileCollection

  @get:InputFiles
  internal abstract val classPathFiles: ConfigurableFileCollection

  @get:OutputFile
  internal abstract val outputJar: RegularFileProperty

  @get:OutputFile
  internal abstract val mapping: RegularFileProperty

  @get:InputFiles
  internal abstract val proguardConfigurationFiles: ConfigurableFileCollection

  fun programFiles(any: Any) {
    programFiles.from(any)
    programFiles.disallowChanges()
  }

  fun classPathFiles(any: Any) {
    classPathFiles.from(any)
    classPathFiles.disallowChanges()
  }

  fun outputJar(file: File) {
    outputJar.set(file)
    outputJar.disallowChanges()
  }

  fun mapping(file: File) {
    mapping.set(file)
    mapping.disallowChanges()
  }

  fun outputJar(): Provider<RegularFile> = outputJar

  fun proguardConfigurationFiles(any: Any) {
    proguardConfigurationFiles.from(any)
    proguardConfigurationFiles.disallowChanges()
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun FileTree.paths(): List<String> {
    return buildList {
      visit(object : FileVisitor {
        override fun visitDir(dirDetails: FileVisitDetails) {
        }

        override fun visitFile(fileDetails: FileVisitDetails) {
          add(fileDetails.path)
        }
      })
    }
  }

  @TaskAction
  fun taskAction() {

    val r8command = R8Command.builder()
        .addProgramFiles(programFiles.files.map { it.toPath() })
        .addClasspathFiles(classPathFiles.files.map { it.toPath() })
        .setMode(CompilationMode.RELEASE)
        .apply {
          if (mapping.isPresent) {
            setProguardMapOutputPath(mapping.get().asFile.toPath())
          }
        }
      /**
       * We might need an option to override that as if you're running newer versions of Java to run the task
       * R8/asm might fail reading this while the target classes might very well be readable
       */
      .addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(Jvm.current().javaHome.toPath()))
        .setOutput(outputJar.asFile.get().toPath(), OutputMode.ClassFile)
        .addProguardConfigurationFiles(proguardConfigurationFiles.files.map { it.toPath() })
        .build()
    R8.run(r8command)
  }
}
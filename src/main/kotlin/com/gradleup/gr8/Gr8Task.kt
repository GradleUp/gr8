package com.gradleup.gr8

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import kotlinx.metadata.jvm.jvmInternalName
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm

abstract class Gr8Task : DefaultTask() {
  @get:InputFiles
  abstract val programFiles: ConfigurableFileCollection

  @get:InputFiles
  abstract val classPathFiles: ConfigurableFileCollection

  @get:OutputFile
  abstract val output: RegularFileProperty

  @get:OutputFile
  abstract val mapping: RegularFileProperty

  @get:InputFiles
  abstract val proguardConfigurationFiles: ConfigurableFileCollection

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
        .addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(Jvm.current().javaHome.toPath()))
        .setOutput(output.asFile.get().toPath(), OutputMode.ClassFile)
        .addProguardConfigurationFiles(proguardConfigurationFiles.files.map { it.toPath() })
        .build()
    R8.run(r8command)
  }
}
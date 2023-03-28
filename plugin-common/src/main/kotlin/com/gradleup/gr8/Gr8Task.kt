package com.gradleup.gr8

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class Gr8Task : DefaultTask() {
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
  internal abstract val javaLauncher: Property<JavaLauncher>

  @get:Inject
  protected abstract val javaToolchainService: JavaToolchainService

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

  fun javaLauncher(launcher: JavaLauncher) {
    javaLauncher.set(launcher)
    javaLauncher.disallowChanges()
  }

  fun toolchain(toolchain: JavaToolchainSpec) {
    javaLauncher.set(javaToolchainService.launcherFor(toolchain))
    javaLauncher.disallowChanges()
  }

  fun outputJar(): Provider<RegularFile> = outputJar

  fun proguardConfigurationFiles(any: Any) {
    proguardConfigurationFiles.from(any)
    proguardConfigurationFiles.disallowChanges()
  }

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
        .apply {
          if (javaLauncher.isPresent) {
            val javaHome = javaLauncher.get().metadata.installationPath.asFile.toPath()
            addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(javaHome))
          } else {
            addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(Jvm.current().javaHome.toPath()))
          }
        }
        .setOutput(outputJar.asFile.get().toPath(), OutputMode.ClassFile)
        .addProguardConfigurationFiles(proguardConfigurationFiles.files.map { it.toPath() })
        .build()
    R8.run(r8command)
  }
}
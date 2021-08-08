package com.gradleup.gr8

import com.gradleup.gr8.ZipHelper.buildZip
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import javax.inject.Inject

abstract class PatchStdlibTask : DefaultTask() {
  @get:InputFile
  internal abstract val stdlibJar: RegularFileProperty

  @get:OutputFile
  internal abstract val patchedStdlibJar: RegularFileProperty

  fun stdlibJar(fileCollection: FileCollection) {
    stdlibJar.set(
        project.layout.file(
            project.provider {

              fileCollection.files.single {
                isKotlinStdlib(it.name)
              }
            }
        )
    )
    stdlibJar.disallowChanges()
  }

  fun stdlibJar(file: File) {
    stdlibJar.set(file)
    stdlibJar.disallowChanges()
  }

  fun stdlibJar(regularFileProperty: RegularFileProperty) {
    stdlibJar.set(regularFileProperty)
    stdlibJar.disallowChanges()
  }

  fun patchedStdlibJar(): Provider<RegularFile> = patchedStdlibJar

  fun patchedStdlibJar(file: File) {
    patchedStdlibJar.set(file)
    patchedStdlibJar.disallowChanges()
  }

  fun patchedStdlibJar(regularFileProperty: RegularFileProperty) {
    patchedStdlibJar.set(regularFileProperty)
    patchedStdlibJar.disallowChanges()
  }

  private fun patch(input: ByteArray): ByteArray {
    val classReader = ClassReader(input)
    val classWriter = ClassWriter(classReader, 0)

    val visitor = object : ClassVisitor(Opcodes.ASM4, classWriter) {
      override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
        // Remove the public access
        super.visit(version, access.and(Opcodes.ACC_PUBLIC.inv()), name, signature, superName, interfaces)
      }
    }

    classReader.accept(visitor, 0)

    return classWriter.toByteArray()
  }

  @TaskAction
  fun taskAction() {
    buildZip(patchedStdlibJar.asFile.get()) {
      addZipFile(stdlibJar.asFile.get()) {
        if (entry.name.contains("DefaultConstructorMarker")) {
          changeContents(patch(inputStream().readAllBytes()).inputStream())
        } else {
          // keep unchanged
        }
      }
    }
  }

  companion object {
    fun isKotlinStdlib(name: String) = Regex("kotlin-stdlib-+[0-9.]*.jar").matches(name)
  }
}
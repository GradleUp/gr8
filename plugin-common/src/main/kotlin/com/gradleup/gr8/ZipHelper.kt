package com.gradleup.gr8

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


object ZipHelper {
  fun zipFolder(srcFolder: File, destZipFile: File) {
    FileOutputStream(destZipFile).use { fileWriter ->
      ZipOutputStream(fileWriter).use { zip ->
        addFolderToZip(srcFolder, srcFolder, zip)
      }
    }
  }

  private fun addFileToZip(rootPath: File, srcFile: File, zip: ZipOutputStream) {
    if (srcFile.isDirectory) {
      addFolderToZip(rootPath, srcFile, zip)
    } else {
      var name = srcFile.path
      name = name.replace(rootPath.path, "")

      zip.putNextEntry(ZipEntry(name))

      srcFile.inputStream().copyTo(zip)
    }
  }

  private fun addFolderToZip(rootPath: File, srcFolder: File, zip: ZipOutputStream) {
    for (fileName in srcFolder.listFiles()!!) {
      addFileToZip(rootPath, fileName, zip)
    }
  }

  fun unzipFile(zipFile: File, destFolder: File) {
    val rootPath = destFolder.absolutePath
    ZipFile(zipFile).use { file ->
      val zipEntries = file.entries()
      while (zipEntries.hasMoreElements()) {
        val zipEntry = zipEntries.nextElement()
        val newFile = File(destFolder, zipEntry.name)

        check(newFile.normalize().absolutePath.startsWith(rootPath)) {
          "Bad path:\n$newFile - $rootPath"
        }

        newFile.parentFile.mkdirs()

        if (!zipEntry.isDirectory) {
          file.getInputStream(zipEntry).copyTo(newFile.outputStream())
        }
      }
    }
  }

  fun transformJar(inputJar: File, outputJar: File, transform: (ZipEntry, InputStream) -> Pair<ZipEntry, InputStream>?) {
    ZipFile(inputJar).use { input ->
      ZipOutputStream(outputJar.outputStream()).use { output ->
        for (entry in input.entries()) {
          if (entry.isDirectory) {
            output.putNextEntry(entry)
          } else {
            val stream = input.getInputStream(entry)
            val pair = transform(entry, stream)
            if (pair == null) {
              output.closeEntry()
              continue
            }

            val (transformedEntry, transformedInputStream) = pair

            output.putNextEntry(transformedEntry)
            transformedInputStream.copyTo(output)
          }
          output.closeEntry()
        }
      }
    }
  }

  private fun addEntry(outputStream: ZipOutputStream, excludes: List<Regex>, names: MutableSet<String>, entry: ZipEntry, inputStream: InputStream?, skip: Boolean) {
    if (skip) {
      return
    }
    if (excludes.any { it.matches(entry.name) }) {
      return
    }
    if (names.contains(entry.name)) {
      if (!entry.name.endsWith("/")) {
        println("Skipping duplicate entry ${entry.name}")
      } else {
        // This is a directory, no need to add it again
      }
      return
    }
    names.add(entry.name)

    outputStream.putNextEntry(entry)
    inputStream?.copyTo(outputStream)
    outputStream.closeEntry()
  }

  fun buildZip(outputZip: File, block: ZipConfiguration.() -> Unit) {
    val names = mutableSetOf<String>()
    ZipOutputStream(outputZip.outputStream()).use { output ->
      val configuration = DefaultZipConfiguration()

      configuration.block()

      val excludeRegexes = configuration.excludes.map { Regex(it) }

      for (zipInput in configuration.zipInputs) {
        ZipFile(zipInput.zipFile).use { input ->
          for (entry in input.entries()) {
            val eachEntryConfiguration = DefaultEachEntryConfiguration(
                entry = entry,
            ) {
              input.getInputStream(entry)
            }

            zipInput.eachEntry(eachEntryConfiguration)

            addEntry(
                outputStream = output,
                excludes = excludeRegexes,
                names = names,
                entry = eachEntryConfiguration.newEntry,
                inputStream = eachEntryConfiguration.newContents ?: input.getInputStream(entry),
                skip = eachEntryConfiguration.skip
            )
          }
        }
      }

      for (directoryInput in configuration.directoryInputs) {
        directoryInput.directory.walk().forEach {
          val eachFileConfiguration = DefaultEachFileConfiguration(
              baseDir = directoryInput.directory,
              file = it,
          )

          directoryInput.eachFile(eachFileConfiguration)

          val contents = if (eachFileConfiguration.newContents != null) {
            eachFileConfiguration.newContents
          } else if (it.isDirectory) {
            null
          } else {
            it.inputStream()
          }

          addEntry(
              outputStream = output,
              excludes = excludeRegexes,
              names = names,
              entry = ZipEntry(eachFileConfiguration.newName),
              inputStream = contents,
              skip = eachFileConfiguration.skip
          )
        }
      }

      for (fileInput in configuration.fileInputs) {
        check(excludeRegexes.none { it.matches(fileInput.entryName) }) {
          "file ${fileInput.entryName} conflicts with excludes. Either remove it or change excludes"
        }
        addEntry(
            outputStream = output,
            excludes = excludeRegexes,
            names = names,
            entry = ZipEntry(fileInput.entryName),
            inputStream = fileInput.file.inputStream(),
            skip = false
        )
      }
    }
  }
}

internal class FileInput(val file: File, val entryName: String)
internal class DirectoryInput(val directory: File, val eachFile: (EachFileConfiguration) -> Unit)
internal class ZipInput(val zipFile: File, val eachEntry: (EachEntryConfiguration) -> Unit)

internal class DefaultZipConfiguration : ZipConfiguration {
  val zipInputs = mutableListOf<ZipInput>()
  val directoryInputs = mutableListOf<DirectoryInput>()
  val fileInputs = mutableListOf<FileInput>()
  val excludes = mutableListOf<String>()

  override fun addZipFile(zipFile: File, eachEntry: EachEntryConfiguration.() -> Unit) {
    zipInputs.add(ZipInput(zipFile, eachEntry))
  }

  override fun addDirectory(directory: File, eachFile: EachFileConfiguration.() -> Unit) {
    directoryInputs.add(DirectoryInput(directory, eachFile))
  }

  override fun addFile(file: File, entryName: String) {
    fileInputs.add(FileInput(file, entryName))
  }

  override fun exclude(pattern: String) {
    excludes.add(pattern)
  }
}

interface ZipConfiguration {
  fun addZipFile(zipFile: File, eachEntry: EachEntryConfiguration.() -> Unit = {})
  fun addDirectory(directory: File, eachFile: EachFileConfiguration.() -> Unit = {})
  fun addFile(file: File, entryName: String = file.name)
  fun exclude(pattern: String)
}

interface EachEntryConfiguration {
  /**
   * The original entry
   */
  val entry: ZipEntry

  /**
   * The inputStream of the entry.
   * You must close the returned inputStream
   */
  fun inputStream(): InputStream

  fun skip(skip: Boolean = true)
  fun rename(newName: String)
  fun changeContents(newContents: InputStream)
}

internal class DefaultEachEntryConfiguration(override val entry: ZipEntry, private val inputStreamProvider: () -> InputStream) : EachEntryConfiguration {
  var skip: Boolean = false

  var newEntry: ZipEntry = ZipEntry(entry.name).apply {
    // Only keep extra and comment from the origin
    // newEntry will use DEFLATE compression and set its size automatically
    extra = entry.extra
    comment = entry.comment
  }
  var newContents: InputStream? = null

  override fun inputStream(): InputStream {
    return inputStreamProvider()
  }

  override fun skip(skip: Boolean) {
    this.skip = skip
  }

  override fun rename(newName: String) {
    this.newEntry = newEntry.copy(name = newName)
  }

  override fun changeContents(newContents: InputStream) {
    this.newContents = newContents
  }
}

interface EachFileConfiguration {
  /**
   * The original File. Can be a regular file or directory
   */
  val file: File

  fun skip(skip: Boolean)
  fun rename(newName: String)
  fun changeContents(newContents: InputStream)
}

internal class DefaultEachFileConfiguration(val baseDir: File, override val file: File) : EachFileConfiguration {
  var skip: Boolean = false
  var newName: String
  var newContents: InputStream? = null

  init {
    newName = file.relativeTo(baseDir).path.let {
      if (file.isDirectory) {
        // Ensure a leading slash, not sure how well zip works with directories that do not end with "/"
        "${it.removeSuffix(File.separator)}/"
      } else {
        it
      }
    }
  }

  override fun skip(skip: Boolean) {
    this.skip = skip
  }

  override fun rename(newName: String) {
    this.newName = newName
  }

  override fun changeContents(newContents: InputStream) {
    this.newContents = newContents
  }
}

fun ZipEntry.copy(
    name: String = this.name,
    mtime: FileTime? = this.lastModifiedTime,
    atime: FileTime? = this.lastAccessTime,
    ctime: FileTime? = this.creationTime,
    crc: Long = this.crc,
    size: Long = this.size,
    csize: Long = this.compressedSize,
    method: Int = this.method,
    extra: ByteArray = this.extra,
    comment: String? = this.comment
): ZipEntry {
  return ZipEntry(name).apply {
    if (mtime != null) {
      lastModifiedTime = mtime
    }
    if (atime != null) {
      lastAccessTime = atime
    }
    if (ctime != null) {
      creationTime = ctime
    }
    this.crc = crc
    this.size = size
    this.compressedSize = csize
    this.method = method
    this.extra = extra
    this.comment = comment
  }
}
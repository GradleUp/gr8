package com.gradleup.gr8

import org.gradle.api.file.FileCollection
import java.io.File
import java.util.zip.ZipInputStream

fun detectDuplicates(jars: FileCollection, filter: (String) -> Boolean = { true }) {
    val pairs = mutableListOf<Pair<String, String>>()
    jars.files.forEach { jar ->
        pairs.addAll(jar.getAllClasses().map { it to jar.path })
    }

    val map = pairs.groupBy(
        keySelector = { it.first },
        valueTransform = { it.second }
    ).filter { it.value.size > 1 }

    map.filter { filter(it.key) }.forEach {
        println("${it.key} in files:")
        it.value.forEach {
            println("  - $it")
        }
    }
}

private fun File.getAllClasses(): List<String> {
    return buildList {
        ZipInputStream(inputStream()).use {
            var entry = it.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    add(entry.name)
                }
                entry = it.nextEntry
            }
        }
    }
}
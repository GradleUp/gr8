package com.gradleup.test

import org.gradle.api.Plugin
import org.gradle.api.Project

class TestPlugin: Plugin<Project> {
  override fun apply(target: Project) {
    /**
     * A lot of the newer kotlin API like lowercase() are actually inlined. CharSequence.contentEquals is one that I found
     * that is not inlined
     */
    val a: CharSequence = ""
    println("equals?" + a.contentEquals(""))
  }
}
package com.gradleup.gr8

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import javax.inject.Inject

open class Gr8Plugin: Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.create("gr8", Gr8Extension::class.java, target)
  }
}


// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.collections.immutable.persistentListOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RuntimeDependencyTraversalTest {
  @Test
  fun `preserves first discovered chain with sorted frontier traversal`() {
    val result = collectTransitiveRuntimeDependencies(
      roots = listOf("alpha" to persistentListOf(), "beta" to persistentListOf()),
      blockedOrSeen = hashSetOf(),
      omitFromResult = emptySet(),
      dependencyResolver = dependencyResolver(
        mapOf(
          "alpha" to listOf("x"),
          "beta" to listOf("a"),
          "x" to listOf("shared"),
          "a" to listOf("shared"),
        )
      ),
    )

    assertThat(result.map { it.first }).containsExactly("x", "a", "shared")
    assertThat(result.single { it.first == "shared" }.second).isEqualTo(persistentListOf("beta", "a"))
  }

  @Test
  fun `blocks transitive modules already present in seen set`() {
    val result = collectTransitiveRuntimeDependencies(
      roots = listOf("root" to persistentListOf()),
      blockedOrSeen = hashSetOf("blocked"),
      omitFromResult = emptySet(),
      dependencyResolver = dependencyResolver(
        mapOf(
          "root" to listOf("blocked", "allowed"),
          "blocked" to listOf("hidden"),
          "allowed" to listOf("leaf"),
        )
      ),
    )

    assertThat(result.map { it.first }).containsExactly("allowed", "leaf")
  }

  @Test
  fun `omitted modules are traversed but not returned`() {
    val result = collectTransitiveRuntimeDependencies(
      roots = listOf("root" to persistentListOf()),
      blockedOrSeen = hashSetOf(),
      omitFromResult = setOf("plugin"),
      dependencyResolver = dependencyResolver(
        mapOf(
          "root" to listOf("plugin"),
          "plugin" to listOf("dependency"),
        )
      ),
    )

    assertThat(result.map { it.first }).containsExactly("dependency")
    assertThat(result.single().second).isEqualTo(persistentListOf("root", "plugin"))
  }

  @Test
  fun `roots are expanded even if they are already blocked`() {
    val result = collectTransitiveRuntimeDependencies(
      roots = listOf("root" to persistentListOf()),
      blockedOrSeen = hashSetOf("root"),
      omitFromResult = emptySet(),
      dependencyResolver = dependencyResolver(
        mapOf(
          "root" to listOf("dependency"),
        )
      ),
    )

    assertThat(result.map { it.first }).containsExactly("dependency")
  }

  @Test
  fun `embedded dependency ownership stays with the first processed module`() {
    val result = computeEmbeddedModuleDependenciesInOrder(
      embeddedModulesInProcessingOrder = listOf(
        moduleItem(moduleName = "embedded.first", relativeOutputFile = "first.jar"),
        moduleItem(moduleName = "embedded.second", relativeOutputFile = "second.jar"),
      ),
      excludedModuleNames = emptySet(),
      alreadyIncluded = hashSetOf("embedded.first", "embedded.second"),
      dependencyResolver = dependencyResolver(
        mapOf(
          "embedded.first" to listOf("shared"),
          "embedded.second" to listOf("shared"),
        )
      ),
    )

    val sharedDependency = result.single()
    assertThat(sharedDependency.moduleName).isEqualTo("shared")
    assertThat(sharedDependency.relativeOutputFile).isEqualTo("first.jar")
  }

  @Test
  fun `excluded embedded dependencies are skipped`() {
    val result = computeEmbeddedModuleDependenciesInOrder(
      embeddedModulesInProcessingOrder = listOf(moduleItem(moduleName = "embedded", relativeOutputFile = "embedded.jar")),
      excludedModuleNames = setOf("excluded"),
      alreadyIncluded = hashSetOf("embedded"),
      dependencyResolver = dependencyResolver(
        mapOf(
          "embedded" to listOf("excluded", "dependency"),
        )
      ),
    )

    assertThat(result.map { it.moduleName }).containsExactly("dependency")
  }
}

private fun dependencyResolver(graph: Map<String, List<String>>): RuntimeDependencyResolver {
  return RuntimeDependencyResolver { moduleName ->
    graph.getOrDefault(moduleName, emptyList())
  }
}

private fun moduleItem(moduleName: String, relativeOutputFile: String): ModuleItem {
  return ModuleItem(
    moduleName = moduleName,
    relativeOutputFile = relativeOutputFile,
    reason = "test",
  )
}

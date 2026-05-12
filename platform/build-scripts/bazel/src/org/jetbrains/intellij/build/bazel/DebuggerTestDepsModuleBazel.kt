// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

private const val DEBUGGER_TEST_DEPS_MODULE_BAZEL = "debugger_test_deps.MODULE.bazel"
private const val DEBUGGER_AGENT_HOLDER_MODULE_NAME = "intellij.java.debugger.agent.holder"
private const val DEBUGGER_AGENT_LIBRARY_NAME = "debugger-agent"

internal fun generateDebuggerTestDepsModuleBazel(
  communityRoot: Path,
  allLibraries: Collection<Library>,
  urlCache: UrlCache,
  m2Repo: Path,
) {
  val matchingLibraries = allLibraries.filterIsInstance<MavenLibrary>().filter {
    it.target.container.isCommunity &&
    it.target.moduleLibraryModuleName == DEBUGGER_AGENT_HOLDER_MODULE_NAME &&
    it.target.jpsName == DEBUGGER_AGENT_LIBRARY_NAME
  }
  val library = when (matchingLibraries.size) {
    0 -> return
    1 -> matchingLibraries.single()
    else -> error("Expected a single $DEBUGGER_AGENT_LIBRARY_NAME library in $DEBUGGER_AGENT_HOLDER_MODULE_NAME, got: $matchingLibraries")
  }

  val binaryJars = library.jars.filter { it.mavenCoordinates.classifier == null }
  check(binaryJars.size == 1) {
    "Expected a single binary jar for $DEBUGGER_AGENT_LIBRARY_NAME in $DEBUGGER_AGENT_HOLDER_MODULE_NAME, got: $binaryJars"
  }
  val jar = binaryJars.single()
  val jarPath = jar.path.relativeTo(m2Repo).invariantSeparatorsPathString
  val entry = urlCache.getEntry(jarPath)
              ?: error("Cannot find cache entry for $jarPath while generating $DEBUGGER_TEST_DEPS_MODULE_BAZEL")

  val file = communityRoot.resolve(DEBUGGER_TEST_DEPS_MODULE_BAZEL)
  val newContent = renderDebuggerTestDepsModuleBazel(entry)
  if (file.isRegularFile() && file.readText() == newContent) {
    return
  }

  println("Writing debugger test deps to $file")
  file.createParentDirectories().writeText(newContent)
}

private fun renderDebuggerTestDepsModuleBazel(entry: CacheEntry): String = """
  http_file = use_repo_rule("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
  
  http_file(
      name = "debugger_test_deps_debugger_agent",
      downloaded_file_path = "debugger-agent.jar",
      sha256 = "${entry.sha256}",
      url = "${entry.url}",
  )
""".trimIndent() + "\n"

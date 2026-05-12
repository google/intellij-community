// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeowners.runtime.resolver

import com.intellij.codeowners.monorepo.resolver.TestOwnerResolver
import com.intellij.tests.TestLocationStorage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div

class TestClassCodeOwnerResolverImpl {
  private val resolver: TestOwnerResolver? by lazy { createResolver() }

  private val BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV = "BUILD_WORKSPACE_DIRECTORY"

  private val ultimateRoot: Path by lazy {
    val workspaceDir: Path? = System.getenv(BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV)?.let { Path.of(it).normalize() }
    val userDir: Path = Path.of(System.getProperty("user.dir"))
    searchRepositoryRoot(workspaceDir ?: userDir)
  }

  private fun searchRepositoryRoot(start: Path): Path {
    var current = start
    while (true) {
      if (Files.exists(current.resolve(".patronus")) && Files.exists(current.resolve(".ultimate.root.marker"))) {
        return current
      }

      current = checkNotNull(current.parent) { "Cannot find ultimate root starting from $start" }
    }
  }

  fun getOwnerGroupName(testClass: Class<*>): String? {
    val r = resolver ?: return null
    val locationInfo = TestLocationStorage.getTestLocationInfo(testClass) ?: return null
    return r.getOwner(locationInfo.moduleName(), locationInfo.packagePath(), locationInfo.fileName())?.owner?.stringName
  }

  private fun createResolver(): TestOwnerResolver? {
    val dir = ultimateRoot / "out" / "artifacts" / "codeowners"
    return TestOwnerResolver.create(
      ultimateRoot,
      dir / "file-locations.ndjson",
      dir / "module-paths.ndjson",
      dir / "ownership-mapping-generated.yaml",
    )
  }
}

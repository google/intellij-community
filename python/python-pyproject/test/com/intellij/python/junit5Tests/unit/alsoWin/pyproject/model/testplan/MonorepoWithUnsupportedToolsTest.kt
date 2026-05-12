// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.PYTHON
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.SEP
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_with_unsupported_tools")
internal class MonorepoWithUnsupportedToolsTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("flit1_name", contentRoot = "flit${SEP}flit1"),
      ExpectedModule("name2", contentRoot = "flit${SEP}flit2"),
      ExpectedModule("hatch1", contentRoot = "hatch${SEP}hatch1", sourceRoots = listOf("hatch${SEP}hatch1${SEP}src")),
      ExpectedModule("hatch2", contentRoot = "hatch${SEP}hatch2", sourceRoots = listOf("hatch${SEP}hatch2${SEP}src")),
      ExpectedModule("pdm1", contentRoot = "pdm${SEP}pdm1${SEP}pdm1", sourceRoots = listOf("pdm${SEP}pdm1${SEP}pdm1${SEP}src")),
      ExpectedModule("pdm2", contentRoot = "pdm${SEP}pdm2${SEP}pdm2"),
      ExpectedModule("poetry1", contentRoot = "poetry${SEP}poetry1", sourceRoots = listOf("poetry${SEP}poetry1${SEP}src")),
      ExpectedModule("poetry2", contentRoot = "poetry${SEP}poetry2", sourceRoots = listOf("poetry${SEP}poetry2${SEP}src")),
      ExpectedModule("uv", contentRoot = "uv"),
      ExpectedModule("subuv1", contentRoot = "uv${SEP}subuv1"),
      ExpectedModule("subuv2", contentRoot = "uv${SEP}subuv2"),
    )
  }
}

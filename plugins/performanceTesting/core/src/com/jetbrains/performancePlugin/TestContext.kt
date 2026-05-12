// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

@Service(Service.Level.APP)
class TestContext {
  companion object {
    @JvmStatic
    fun getInstance(): TestContext = service()
  }

  fun setActiveTestName(testName: String?) {
    logger<TestContext>().info("Setting active test name: $testName")
    if (testName == null) {
      activeTestNameFile.deleteIfExists()
    }
    else {
      Files.writeString(activeTestNameFile, testName)
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@TestApplication
class McpToolFilterConfigurableTest {
  @AfterEach
  fun tearDown() {
    McpToolDisallowListSettings.getInstance().toolStates = emptyMap()

    val filterSettings = McpToolFilterSettings.getInstance()
    filterSettings.showExperimental = false
    filterSettings.toolsFilter = McpToolFilterSettings.DEFAULT_FILTER
    filterSettings.invocationMode = McpSessionInvocationMode.DIRECT
  }

  @Test
  fun `untouched configurable is not modified when no overrides are stored`() {
    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()

      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun `hidden experimental overrides do not mark configurable modified`() {
    val experimentalToolName = McpServerService.getInstance()
      .getMcpToolsFiltered(useFiltersFromEP = false, excludeProviders = emptySet())
      .firstOrNull { it.descriptor.category.isExperimental }
      ?.descriptor
      ?.name

    assumeTrue(experimentalToolName != null)
    McpToolDisallowListSettings.getInstance().toolStates = mapOf(experimentalToolName!! to McpToolState.OFF)
    McpToolFilterSettings.getInstance().showExperimental = false

    val configurable = McpToolFilterConfigurable()

    try {
      configurable.createComponent()

      assertThat(configurable.isModified()).isFalse()
    }
    finally {
      configurable.disposeUIResources()
    }
  }
}

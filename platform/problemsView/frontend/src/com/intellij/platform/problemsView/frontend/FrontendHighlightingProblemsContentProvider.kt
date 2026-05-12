// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.splitApi.isSplitProblemsViewKeyEnabled
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content

internal class FrontendHighlightingProblemsContentProvider : FrontendProblemsViewContentProvider {
  override fun isAvailable(project: Project): Boolean {
    return isSplitProblemsViewKeyEnabled()
  }

  override fun initTabContent(project: Project, content: Content) {
    thisLogger().debug("initializing highlighting tab content in frontend")
    val panel = FrontendHighlightingPanel(project, ProblemsViewState.getInstance(project))
    content.component = panel
    panel.updateSelectedFile()
  }

  override fun matchesTabName(tabName: String) : Boolean {
    return tabName.contains("File")
  }
}
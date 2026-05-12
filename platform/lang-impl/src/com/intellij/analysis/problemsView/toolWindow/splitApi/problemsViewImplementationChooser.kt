package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun isSplitProblemsViewKeyEnabled(): Boolean {
  return Registry.`is`("problems.view.highlighting.frontend.enabled", defaultValue = false)
}

@ApiStatus.Internal
fun isSplitProblemsViewProjectErrorsKeyEnabled(): Boolean {
  return Registry.`is`("problems.view.project.errors.frontend.enabled", defaultValue = false)
}

@ApiStatus.Internal
fun areProblemsViewKeysEnabled(): Boolean {
  return isSplitProblemsViewKeyEnabled() && isSplitProblemsViewProjectErrorsKeyEnabled()
}

@ApiStatus.Internal
fun setProblemsViewImplementationForNextIdeRun(shouldEnableSplitImplementation: Boolean) {
  Registry.get("problems.view.highlighting.frontend.enabled").setValue(shouldEnableSplitImplementation)
  Registry.get("problems.view.project.errors.frontend.enabled").setValue(shouldEnableSplitImplementation)
}


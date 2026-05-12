// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationRouting
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionRequest
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionResult
import com.intellij.agent.workbench.codex.sessions.registerShutdownOnCancellation
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.APP)
class CodexPromptSuggestionAppServerService internal constructor(
  serviceScope: CoroutineScope,
  private val suggestWithClient: suspend (CodexPromptSuggestionRequest) -> CodexPromptSuggestionResult?,
  private val shutdownClient: () -> Unit = {},
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    client = CodexAppServerClient(
      coroutineScope = serviceScope,
      notificationRouting = CodexAppServerNotificationRouting.PARSED_ONLY,
    ),
  )

  @Suppress("unused")
  private constructor(serviceScope: CoroutineScope, client: CodexAppServerClient) : this(
    serviceScope = serviceScope,
    suggestWithClient = client::suggestPrompt,
    shutdownClient = client::shutdown,
  )

  private val suggestionMutex = Mutex()

  init {
    registerShutdownOnCancellation(serviceScope) { shutdownClient() }
  }

  internal suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): CodexPromptSuggestionResult? {
    return suggestionMutex.withLock {
      currentCoroutineContext().ensureActive()
      suggestWithClient(request)
    }
  }
}

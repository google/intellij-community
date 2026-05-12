// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AgentChatOpenTabPresentationInvalidator {
  /**
   * Repaints open chat editors whose presentation key was affected by the change set.
   *
   * The resolved presentation is written back into the file's bootstrap fields so that
   * (a) each repaint has a fast, store-free source for its title/activity and
   * (b) the persisted snapshot restores the last-seen title/activity after IDE restart.
   * For sub-agent tabs the resolved title is still the sub-agent's own label (per
   * [resolveAgentChatThreadPresentation]); only the activity is inherited from the
   * shared thread presentation. Tabs whose resolved state is unchanged are skipped.
   */
  suspend fun invalidate(changeSet: AgentThreadPresentationChangeSet): Int {
    if (changeSet.isEmpty) return 0

    val snapshotsToPersist = ArrayList<AgentChatTabSnapshot>()
    var updatedFiles = 0
    withContext(Dispatchers.EDT) {
      val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()
      for (chatFile in openTabsSnapshot.files()) {
        val key = chatFile.presentationKeyOrNull() ?: continue
        if (key !in changeSet.changedKeys && key !in changeSet.removedKeys) {
          continue
        }

        val resolvedPresentation = resolveAgentChatThreadPresentation(chatFile)
        val titleChanged = chatFile.updateBootstrapThreadTitle(resolvedPresentation.title)
        val activityChanged = chatFile.updateBootstrapThreadActivity(resolvedPresentation.activity)
        val needsRepaint = titleChanged || activityChanged || key in changeSet.removedKeys
        if (!needsRepaint) continue

        val managers = openTabsSnapshot.managersFor(chatFile)
        if (managers.isEmpty()) continue

        if (titleChanged || activityChanged) {
          snapshotsToPersist.add(chatFile.toSnapshot())
        }
        updatedFiles++
        for (manager in managers) {
          manager.updateFilePresentation(chatFile)
        }
      }
    }

    if (snapshotsToPersist.isNotEmpty()) {
      val tabsService = service<AgentChatTabsService>()
      withContext(Dispatchers.IO) {
        for (snapshot in snapshotsToPersist) {
          tabsService.upsert(snapshot)
        }
      }
    }

    return updatedFiles
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.TestOnly

internal data class AgentThreadPresentationKey(
  @JvmField val normalizedProjectPath: String,
  @JvmField val threadIdentity: String,
) {
  companion object {
    fun create(projectPath: String, threadIdentity: String): AgentThreadPresentationKey? {
      val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
      if (normalizedProjectPath.isBlank() || threadIdentity.isBlank()) {
        return null
      }
      return AgentThreadPresentationKey(
        normalizedProjectPath = normalizedProjectPath,
        threadIdentity = threadIdentity,
      )
    }
  }
}

internal data class AgentThreadPresentation(
  @JvmField val title: @NlsSafe String,
  @JvmField val activity: AgentThreadActivity,
)

internal data class AgentThreadPresentationChangeSet(
  @JvmField val changedKeys: Set<AgentThreadPresentationKey>,
  @JvmField val removedKeys: Set<AgentThreadPresentationKey>,
) {
  companion object {
    val EMPTY: AgentThreadPresentationChangeSet = AgentThreadPresentationChangeSet(
      changedKeys = emptySet(),
      removedKeys = emptySet(),
    )
  }

  val isEmpty: Boolean
    get() = changedKeys.isEmpty() && removedKeys.isEmpty()
}

@Service(Service.Level.APP)
internal class AgentThreadPresentationStore {
  private val lock = Any()

  @Volatile
  private var state: Map<AgentThreadPresentationKey, AgentThreadPresentation> = emptyMap()

  fun snapshot(): Map<AgentThreadPresentationKey, AgentThreadPresentation> = state

  fun resolve(key: AgentThreadPresentationKey): AgentThreadPresentation? = state[key]

  fun putExact(
    key: AgentThreadPresentationKey,
    presentation: AgentThreadPresentation,
  ): AgentThreadPresentationChangeSet {
    synchronized(lock) {
      val current = state
      if (current[key] == presentation) {
        return AgentThreadPresentationChangeSet.EMPTY
      }
      state = LinkedHashMap(current).apply { put(key, presentation) }
      return AgentThreadPresentationChangeSet(
        changedKeys = setOf(key),
        removedKeys = emptySet(),
      )
    }
  }

  fun remove(keys: Set<AgentThreadPresentationKey>): AgentThreadPresentationChangeSet {
    if (keys.isEmpty()) {
      return AgentThreadPresentationChangeSet.EMPTY
    }

    synchronized(lock) {
      val current = state
      val next = LinkedHashMap(current)
      val removedKeys = LinkedHashSet<AgentThreadPresentationKey>()
      for (key in keys) {
        if (next.remove(key) != null) {
          removedKeys.add(key)
        }
      }
      if (removedKeys.isEmpty()) {
        return AgentThreadPresentationChangeSet.EMPTY
      }
      state = next
      return AgentThreadPresentationChangeSet(
        changedKeys = emptySet(),
        removedKeys = removedKeys,
      )
    }
  }

  fun applyRefresh(
    provider: AgentSessionProvider,
    refreshedPaths: Set<String>,
    titleByPathAndThreadIdentity: Map<Pair<String, String>, String>,
    activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity>,
  ): AgentThreadPresentationChangeSet {
    if (refreshedPaths.isEmpty() && titleByPathAndThreadIdentity.isEmpty() && activityByPathAndThreadIdentity.isEmpty()) {
      return AgentThreadPresentationChangeSet.EMPTY
    }

    val normalizedPaths = refreshedPaths
      .asSequence()
      .map(::normalizeAgentWorkbenchPath)
      .filter(String::isNotBlank)
      .toCollection(LinkedHashSet())

    synchronized(lock) {
      val current = state
      val presentationsByKey = buildPresentationMerge(
        current = current,
        titleByPathAndThreadIdentity = titleByPathAndThreadIdentity,
        activityByPathAndThreadIdentity = activityByPathAndThreadIdentity,
      )
      val next = LinkedHashMap<AgentThreadPresentationKey, AgentThreadPresentation>(current.size + presentationsByKey.size)
      val changedKeys = LinkedHashSet<AgentThreadPresentationKey>()
      val removedKeys = LinkedHashSet<AgentThreadPresentationKey>()

      for ((key, presentation) in current) {
        // Removal is safe only for provider/path scopes that the caller marks as authoritative.
        if (key.matchesScope(provider, normalizedPaths) && key !in presentationsByKey) {
          removedKeys.add(key)
          continue
        }
        next[key] = presentation
      }

      for ((key, presentation) in presentationsByKey) {
        if (next[key] == presentation) {
          continue
        }
        next[key] = presentation
        changedKeys.add(key)
      }

      if (changedKeys.isEmpty() && removedKeys.isEmpty()) {
        return AgentThreadPresentationChangeSet.EMPTY
      }
      state = next
      return AgentThreadPresentationChangeSet(
        changedKeys = changedKeys,
        removedKeys = removedKeys,
      )
    }
  }

  @TestOnly
  fun replaceForTests(
    presentationsByKey: Map<AgentThreadPresentationKey, AgentThreadPresentation>,
  ) {
    synchronized(lock) {
      state = LinkedHashMap(presentationsByKey)
    }
  }

  @TestOnly
  fun clearForTests() {
    synchronized(lock) {
      state = emptyMap()
    }
  }
}

private fun buildPresentationMerge(
  current: Map<AgentThreadPresentationKey, AgentThreadPresentation>,
  titleByPathAndThreadIdentity: Map<Pair<String, String>, String>,
  activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity>,
): LinkedHashMap<AgentThreadPresentationKey, AgentThreadPresentation> {
  val orderedInputs = LinkedHashSet<Pair<String, String>>().apply {
    addAll(titleByPathAndThreadIdentity.keys)
    addAll(activityByPathAndThreadIdentity.keys)
  }
  val merged = LinkedHashMap<AgentThreadPresentationKey, AgentThreadPresentation>(orderedInputs.size)
  for (input in orderedInputs) {
    val key = AgentThreadPresentationKey.create(projectPath = input.first, threadIdentity = input.second) ?: continue
    val existing = current[key]
    merged[key] = AgentThreadPresentation(
      title = titleByPathAndThreadIdentity[input] ?: existing?.title.orEmpty(),
      activity = activityByPathAndThreadIdentity[input] ?: existing?.activity ?: AgentThreadActivity.READY,
    )
  }
  return merged
}

internal fun AgentChatVirtualFile.presentationKeyOrNull(): AgentThreadPresentationKey? {
  return AgentThreadPresentationKey.create(
    projectPath = projectPath,
    threadIdentity = threadIdentity,
  )
}

internal fun AgentChatVirtualFile.isEligibleForSharedPresentationSync(): Boolean {
  return !isPendingThread && subAgentId == null
}

internal fun AgentChatTabSnapshot.sharedThreadPresentationKeyOrNull(): AgentThreadPresentationKey? {
  if (identity.subAgentId != null || resolveAgentChatThreadCoordinates(identity.threadIdentity)?.isPending == true) {
    return null
  }
  return AgentThreadPresentationKey.create(
    projectPath = identity.projectPath,
    threadIdentity = identity.threadIdentity,
  )
}

internal fun resolveAgentChatThreadPresentation(file: AgentChatVirtualFile): AgentThreadPresentation {
  val bootstrapPresentation = AgentThreadPresentation(
    title = file.bootstrapThreadTitle,
    activity = file.bootstrapThreadActivity,
  )
  if (file.isPendingThread) {
    return bootstrapPresentation
  }

  val key = file.presentationKeyOrNull() ?: return bootstrapPresentation
  val application = ApplicationManager.getApplication()
  if (application == null || application.isDisposed) {
    return bootstrapPresentation
  }
  val sharedPresentation = application.service<AgentThreadPresentationStore>().resolve(key) ?: return bootstrapPresentation

  val resolvedTitle = if (file.subAgentId == null) {
    sharedPresentation.title.takeIf { it.isNotBlank() } ?: bootstrapPresentation.title
  }
  else {
    bootstrapPresentation.title
  }
  return AgentThreadPresentation(
    title = resolvedTitle,
    activity = sharedPresentation.activity,
  )
}

internal fun syncAgentChatSharedThreadPresentation(file: AgentChatVirtualFile): AgentThreadPresentationChangeSet {
  if (!file.isEligibleForSharedPresentationSync()) return AgentThreadPresentationChangeSet.EMPTY
  val key = file.presentationKeyOrNull() ?: return AgentThreadPresentationChangeSet.EMPTY
  return service<AgentThreadPresentationStore>().putExact(
    key = key,
    presentation = AgentThreadPresentation(
      title = file.bootstrapThreadTitle,
      activity = file.bootstrapThreadActivity,
    ),
  )
}

internal fun syncAgentChatSharedThreadPresentationAfterRebind(
  file: AgentChatVirtualFile,
  previousPresentationKey: AgentThreadPresentationKey?,
) {
  val store = service<AgentThreadPresentationStore>()
  if (
    previousPresentationKey != null &&
    previousPresentationKey != file.presentationKeyOrNull()
  ) {
    store.remove(setOf(previousPresentationKey))
  }
  syncAgentChatSharedThreadPresentation(file)
}

internal fun removeAgentChatSharedThreadPresentation(snapshot: AgentChatTabSnapshot) {
  removeAgentChatSharedThreadPresentation(listOf(snapshot))
}

internal fun removeAgentChatSharedThreadPresentation(snapshots: Iterable<AgentChatTabSnapshot>) {
  val keys = snapshots
    .asSequence()
    .mapNotNull(AgentChatTabSnapshot::sharedThreadPresentationKeyOrNull)
    .toCollection(LinkedHashSet())
  if (keys.isEmpty()) {
    return
  }
  service<AgentThreadPresentationStore>().remove(keys)
}

private fun AgentThreadPresentationKey.matchesScope(
  provider: AgentSessionProvider,
  normalizedPaths: Set<String>,
): Boolean {
  if (normalizedProjectPath !in normalizedPaths) {
    return false
  }
  val providerId = threadIdentity.substringBefore(':', missingDelimiterValue = "")
  return providerId.equals(provider.value, ignoreCase = true)
}

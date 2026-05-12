package com.intellij.python.pyproject.model.internal.autoImportBridge

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.python.pyproject.model.internal.workspaceBridge.isPythonEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CheckReturnValue


/**
 * Tracks workspace model of [project] for events (see [changesToTrack]).
 * Calls [onWsmChanged] if one happens. Be sure to cancel returned job when not needed.
 */
@CheckReturnValue
fun CoroutineScope.createWsmTracker(project: Project, onWsmChanged: () -> Unit): Job =
  launch {
    project.workspaceModel.eventLog.collect { event ->
      val hasChanges = changesToTrack.any { (entityCls, check) ->
        event.getChanges(entityCls).any { check(it) }
      }
      if (hasChanges) {
        onWsmChanged()
      }
    }
  }


private val changesToTrack: Map<Class<out WorkspaceEntity>, (EntityChange<*>) -> Boolean> =
  mapOf(
    ExcludeUrlEntity::class.java to {
      when (it) {
        is EntityChange.Added, is EntityChange.Removed -> true
        is EntityChange.Replaced -> false
      }
    },
    ModuleEntity::class.java to {
      when (it) {
        is EntityChange.Added -> !it.newEntity.entitySource.isPythonEntity // New module and not python
        is EntityChange.Replaced, is EntityChange.Removed -> false
      }
    }
  )

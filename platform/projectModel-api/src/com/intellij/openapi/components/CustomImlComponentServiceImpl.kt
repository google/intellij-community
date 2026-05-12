// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.CustomImlComponentEntity
import com.intellij.platform.workspace.jps.entities.CustomImlComponentEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.customImlComponent
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.xmlb.Constants
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceModelLegacyBridge
import org.jdom.Element

private const val COMPONENT_ELEMENT: String = "component"

internal class CustomImlComponentServiceImpl(
  private val project: Project,
) : CustomImlComponentService {

  private val legacyBridge = WorkspaceModelLegacyBridge.getInstance(project)

  override fun <T> getComponentValue(module: Module, componentName: String, componentClass: Class<T>): T? {
    val moduleEntity = legacyBridge.findModuleEntity(module) ?: return null
    val entity = moduleEntity.customImlComponent ?: return null
    val component = entity.components[componentName] ?: return null
    return JDOMUtil.load(component).deserialize(componentClass)
  }

  override suspend fun <T> setComponentValue(module: Module, componentName: String, component: T) {
    val moduleEntity = legacyBridge.findModuleEntity(module)
    if (moduleEntity != null) {
      project.workspaceModel.update("Update component: $componentName",
                                    prepareUpdater(moduleEntity, componentName, component))
    }
  }

  override fun <T> setComponentValueBlocking(module: Module, componentName: String, component: T) {
    val moduleEntity = legacyBridge.findModuleEntity(module)
    if (moduleEntity != null) {
      project.workspaceModel.updateProjectModel("Update component: $componentName",
                                                prepareUpdater(moduleEntity, componentName, component))
    }
  }

  private fun <T> prepareUpdater(
    moduleEntity: ModuleEntity,
    componentName: String,
    component: T,
  ): (MutableEntityStorage) -> Unit {
    val componentTag = Element(COMPONENT_ELEMENT)
    componentTag.setAttribute(Constants.NAME, componentName)
    serializeObjectInto(component as Any, componentTag, jdomSerializer.getDefaultSerializationFilter())
    val rawContent = JDOMUtil.write(componentTag)
    val entity = moduleEntity.customImlComponent
    val newComponent = mapOf(componentName to rawContent)

    return { storage ->
      if (entity != null) {
        storage.modifyEntity(CustomImlComponentEntityBuilder::class.java, entity) {
          components += newComponent
        }
      }
      else {
        val newEntity = CustomImlComponentEntity(newComponent, moduleEntity.entitySource)
        storage.modifyEntity(ModuleEntityBuilder::class.java, moduleEntity) {
          customImlComponent = newEntity
        }
      }
    }
  }
}

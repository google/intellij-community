// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.util.containers.Java11Shim
import java.util.Collections
import java.util.IdentityHashMap

internal class ModulesWithDependencies(
  @JvmField val modules: List<PluginModuleDescriptor>,
  @JvmField val directDependencies: Map<PluginModuleDescriptor, List<PluginModuleDescriptor>>,
) {
  internal fun sorted(topologicalComparator: Comparator<PluginModuleDescriptor>): ModulesWithDependencies {
    return ModulesWithDependencies(
      modules = modules.sortedWith(topologicalComparator),
      directDependencies = copySorted(directDependencies, topologicalComparator),
    )
  }
}

/**
 * Computes dependencies between modules in plugins and also computes additional edges in the module graph which shouldn't be treated as
 *  dependencies but should be used to determine the order in which modules are processed. 
 */
internal fun createModulesWithDependenciesAndAdditionalEdges(initContext: PluginInitializationContext, pluginSet: UnambiguousPluginSet): Pair<ModulesWithDependencies, IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>> {
  val modules = ArrayList<PluginModuleDescriptor>(pluginSet.plugins.size * 2)
  val additionalEdges = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>()
  for (module in pluginSet.plugins) {
    modules.add(module)
    for (subModule in module.contentModules) {
      modules.add(subModule)
    }
  }

  val dependenciesCollector: MutableSet<PluginModuleDescriptor> = Collections.newSetFromMap(IdentityHashMap())
  val additionalEdgesForCurrentModule: MutableSet<PluginModuleDescriptor> = Collections.newSetFromMap(IdentityHashMap())
  val directDependencies = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>(modules.size)
  for (module in modules) {
    for (implicitDependencyRef in initContext.provideCompatibilityDependencies(module, pluginSet)) {
      pluginSet.resolveReference(implicitDependencyRef)?.let(dependenciesCollector::add)
    }
    collectDirectDependenciesInOldFormat(module, pluginSet, dependenciesCollector, additionalEdgesForCurrentModule, initContext)
    collectDirectDependenciesInNewFormat(module, pluginSet, dependenciesCollector, additionalEdgesForCurrentModule)

    if (module.pluginId != PluginManagerCore.CORE_ID && module is ContentModuleDescriptor) {
      // add main as an implicit dependency for optional content modules 
      val main = pluginSet.resolvePluginId(module.pluginId)!!
      assert(main !== module)
      if (!module.isRequiredContentModule) {
        dependenciesCollector.add(main)
      }

      /* if the plugin containing the module is incompatible with some other plugins, make sure that the module is processed after these plugins (and all their required modules)
         to ensure that the proper module is disabled in case of package conflict */
      for (incompatibility in main.incompatiblePlugins) {
        val incompatibleDescriptor = pluginSet.resolvePluginId(incompatibility)
        if (incompatibleDescriptor != null) {
          additionalEdgesForCurrentModule.add(incompatibleDescriptor)
        }
      }
    }

    if (!additionalEdgesForCurrentModule.isEmpty()) {
      additionalEdgesForCurrentModule.removeAll(dependenciesCollector)
      if (!additionalEdgesForCurrentModule.isEmpty()) {
        additionalEdges.put(module, Java11Shim.INSTANCE.copyOfList(additionalEdgesForCurrentModule))
        additionalEdgesForCurrentModule.clear()
      }
    }
    if (!dependenciesCollector.isEmpty()) {
      directDependencies.put(module, Java11Shim.INSTANCE.copyOfList(dependenciesCollector))
      dependenciesCollector.clear()
    }
  }

  return ModulesWithDependencies(
    modules = modules,
    directDependencies = directDependencies,
  ) to additionalEdges
}

internal fun toCoreAwareComparator(comparator: Comparator<PluginModuleDescriptor>): Comparator<PluginModuleDescriptor> {
  // there is circular reference between core and implementation-detail plugin, as not all such plugins extracted from core,
  // so, ensure that core plugin is always first (otherwise not possible to register actions - a parent group not defined)
  // don't use sortWith here - avoid loading kotlin stdlib
  return Comparator { o1, o2 ->
    val o1isCore = o1 !is ContentModuleDescriptor && o1.pluginId == PluginManagerCore.CORE_ID
    val o2isCore = o2 !is ContentModuleDescriptor && o2.pluginId == PluginManagerCore.CORE_ID
    when {
      o1isCore == o2isCore -> comparator.compare(o1, o2)
      o1isCore -> -1
      else -> 1
    }
  }
}

private fun collectDirectDependenciesInOldFormat(
  rootDescriptor: IdeaPluginDescriptorImpl,
  pluginSet: UnambiguousPluginSet,
  dependenciesCollector: MutableSet<PluginModuleDescriptor>,
  additionalEdges: MutableSet<PluginModuleDescriptor>,
  initContext: PluginInitializationContext,
) {
  for (dependency in rootDescriptor.dependencies) {
    // check for missing optional dependency
    val dependencyPluginId = dependency.pluginId
    val dep = pluginSet.resolvePluginId(dependencyPluginId)
    if (dep == null) {
      dependency.subDescriptor?.isMarkedForLoading = false // target is unresolved
      continue
    }
    if (dep.pluginId != PluginManagerCore.CORE_ID || dep is ContentModuleDescriptor) {
      // ultimate plugin it is combined plugin, where some included XML can define dependency on ultimate explicitly and for now not clear,
      // can be such requirements removed or not
      if (rootDescriptor === dep) {
        if (rootDescriptor.pluginId != PluginManagerCore.CORE_ID) {
          PluginManagerCore.logger.error("Plugin $rootDescriptor depends on self (${dependency})")
        }
      }
      else {
        // e.g. `.env` plugin in an old format and doesn't explicitly specify dependency on new extracted modules
        if (dep is PluginMainDescriptor) {
          dependenciesCollector.addAll(dep.contentModules)
        }

        dependenciesCollector.add(dep)
      }
    }
    if (dep is ContentModuleDescriptor && dep.moduleLoadingRule.required) {
      val dependencyPluginDescriptor = pluginSet.resolvePluginId(dep.pluginId)
      if (dependencyPluginDescriptor != null && dependencyPluginDescriptor !== rootDescriptor) {
        // Add an edge to the main module of the plugin. This is needed to ensure that this plugin is processed after it's decided whether to enable the referenced plugin or not.
        additionalEdges.add(dependencyPluginDescriptor)
      }
    }

    dependency.subDescriptor?.let { subDescriptor ->
      for (implicitDep in initContext.provideCompatibilityDependencies(subDescriptor, pluginSet)) {
        pluginSet.resolveReference(implicitDep)?.let(dependenciesCollector::add)
      }
      collectDirectDependenciesInOldFormat(subDescriptor, pluginSet, dependenciesCollector, additionalEdges, initContext)
    }
  }

  for (pluginId in rootDescriptor.incompatiblePlugins) {
    pluginSet.resolvePluginId(pluginId)?.let {
      dependenciesCollector.add(it)
    }
  }
}

private fun collectDirectDependenciesInNewFormat(
  module: PluginModuleDescriptor,
  pluginSet: UnambiguousPluginSet,
  dependenciesCollector: MutableCollection<PluginModuleDescriptor>,
  additionalEdges: MutableSet<PluginModuleDescriptor>
) {
  for (item in module.moduleDependencies.modules) {
    val dependency = pluginSet.resolveContentModuleId(item)
    if (dependency != null) {
      dependenciesCollector.add(dependency)
      if (dependency.isRequiredContentModule) {
        // Add an edge to the main module of the plugin. This is needed to ensure that this module is processed after it's decided whether to enable the referenced plugin or not.
        val dependencyPluginDescriptor = pluginSet.resolvePluginId(dependency.pluginId)
        val currentPluginDescriptor = pluginSet.resolvePluginId(module.pluginId)
        if (dependencyPluginDescriptor != null && dependencyPluginDescriptor !== currentPluginDescriptor) {
          additionalEdges.add(dependencyPluginDescriptor)
        }
      }
    }
  }
  for (item in module.moduleDependencies.plugins) {
    val targetModule = pluginSet.resolvePluginId(item)
    // fake v1 module maybe located in a core plugin
    if (targetModule != null && (targetModule is ContentModuleDescriptor || targetModule.pluginId != PluginManagerCore.CORE_ID)) {
      dependenciesCollector.add(targetModule)
    }
    // Add an edge to the main module of the plugin. Handling aliases.
    if (targetModule != null && targetModule is ContentModuleDescriptor && targetModule.isRequiredContentModule) {
      if (pluginSet.resolvePluginId(module.pluginId) != targetModule.parent) {
        additionalEdges.add(targetModule.parent)
      }
    }
  }

  if (module.pluginId != PluginManagerCore.CORE_ID && module is PluginMainDescriptor) {
    /* Add edges to all required content modules. 
       This is needed to ensure that the main plugin module is processed after them, and at that point we can determine whether the plugin 
       can be loaded or not. */
    for (item in module.contentModules) {
      if (item.moduleLoadingRule.required) {
        val descriptor = pluginSet.resolveContentModuleId(item.moduleId)
        if (descriptor != null) {
          additionalEdges.add(descriptor)
        }
      }
    }
  }
}

private fun copySorted(
  map: Map<PluginModuleDescriptor, Collection<PluginModuleDescriptor>>,
  comparator: Comparator<PluginModuleDescriptor>,
): Map<PluginModuleDescriptor, List<PluginModuleDescriptor>> {
  val result = IdentityHashMap<PluginModuleDescriptor, List<PluginModuleDescriptor>>(map.size)
  for (element in map.entries) {
    result.put(element.key, element.value.sortedWith(comparator))
  }
  return result
}

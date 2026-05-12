// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems.jar
import com.intellij.openapi.vfs.StandardFileSystems.local
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope
import com.intellij.util.io.URLUtil.JAR_SEPARATOR
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.core.script.shared.HeavyScriptInfo
import org.jetbrains.kotlin.idea.core.script.shared.LightScriptInfo
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.io.File
import java.lang.ref.SoftReference
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString

@K1Deprecation
class ScriptClassRootsCache(
  val scripts: Map<String, LightScriptInfo>,
  private val classes: Set<String>,
  private val sources: Set<String>,
  val customDefinitionsUsed: Boolean,
  val sdks: ScriptSdks,
  private val classpathVfsHint: MutableMap<String, VirtualFile?>?,
) {
    companion object {
        val EMPTY: ScriptClassRootsCache = ScriptClassRootsCache(
          mapOf(), setOf(), setOf(), true,
          ScriptSdks(mapOf(), setOf(), setOf()),
          null
        )
    }

    fun withUpdatedSdks(newSdks: ScriptSdks): ScriptClassRootsCache =
        ScriptClassRootsCache(scripts, classes, sources, customDefinitionsUsed, newSdks, classpathVfsHint)

    fun builder(project: Project): ScriptClassRootsBuilder {
        return ScriptClassRootsBuilder(
            project,
            classes.toMutableSet(),
            sources.toMutableSet(),
            scripts.toMutableMap()
        ).also { builder ->
            if (customDefinitionsUsed) {
                builder.useCustomScriptDefinition()
            }
            builder.sdks.addAll(sdks)
            builder.withClasspathVfsHint(classpathVfsHint ?: mutableMapOf())
        }
    }

    fun scriptsPaths(): Set<String> = scripts.keys

    fun getLightScriptInfo(file: String): LightScriptInfo? = scripts[file]

    private fun getHeavyScriptInfo(file: String): HeavyScriptInfo? {
        val lightScriptInfo = getLightScriptInfo(file) ?: return null
        val heavy0 = lightScriptInfo.heavyCache?.get()
        if (heavy0 != null) return heavy0

        return runReadAction {
          // The lock ^ is needed to define the order of acquisition: (read, lightScriptInfo), thus preventing possible deadlock.
          synchronized(lightScriptInfo) {
            val heavy1 = lightScriptInfo.heavyCache?.get()
            if (heavy1 != null) return@runReadAction heavy1
            val heavy2 = computeHeavy(lightScriptInfo) // might require read-lock inside
            lightScriptInfo.heavyCache = SoftReference(heavy2)
            return@runReadAction heavy2
          }
        }
    }

    private fun computeHeavy(lightScriptInfo: LightScriptInfo): HeavyScriptInfo? {
        val configuration = lightScriptInfo.buildConfiguration() ?: return null

        val vfsRoots = configuration.dependenciesClassPath.mapNotNull { refreshAndLocateFile(it) }
        val sdk = sdks[SdkId.Companion(configuration.javaHome?.toPath())]

        fun heavyInfoForRoots(roots: List<VirtualFile>): HeavyScriptInfo {
            return HeavyScriptInfo(configuration, roots, NonClasspathDirectoriesScope.compose(roots), sdk)
        }

        return if (sdk == null) {
            heavyInfoForRoots(vfsRoots)
        } else {
            val sdkClasses = sdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()
            heavyInfoForRoots(sdkClasses + vfsRoots)
        }
    }

    val allDependenciesClassFiles: Set<VirtualFile>

    val allDependenciesSources: Set<VirtualFile>

    init {
        fun String.toVFile(): VirtualFile? {
            return if (classpathVfsHint?.containsKey(this) == true) {
                classpathVfsHint[this]
            } else {
                refreshIfPossibleAndFindFileByPath(this).also { vFile ->
                    classpathVfsHint?.put(this, vFile)
                }
            }
        }

        allDependenciesClassFiles = mutableSetOf<VirtualFile>().also { result ->
            classes.mapNotNullTo(result) { it.toVFile() }
        }

        allDependenciesSources = mutableSetOf<VirtualFile>().also { result ->
            sources.mapNotNullTo(result) { it.toVFile() }
        }
    }

    val allDependenciesClassFilesScope: GlobalSearchScope = NonClasspathDirectoriesScope.compose(
      allDependenciesClassFiles.toList() + sdks.nonIndexedClassRoots)

    val allDependenciesSourcesScope: GlobalSearchScope = NonClasspathDirectoriesScope.compose(
      allDependenciesSources.toList() + sdks.nonIndexedSourceRoots)

    fun getScriptConfiguration(file: VirtualFile): ScriptCompilationConfigurationWrapper? =
        getHeavyScriptInfo(file.path)?.scriptConfiguration

    fun getScriptSdk(file: VirtualFile): Sdk? =
        getHeavyScriptInfo(file.path)?.sdk

    fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope =
      getHeavyScriptInfo(file.path)?.classFilesScope ?: GlobalSearchScope.EMPTY_SCOPE

    fun getScriptDependenciesClassFiles(file: VirtualFile): List<VirtualFile> =
        getHeavyScriptInfo(file.path)?.classFiles ?: emptyList()

    fun getScriptDependenciesSourceFiles(file: VirtualFile): List<VirtualFile> {
        return getHeavyScriptInfo(file.path)?.scriptConfiguration?.dependenciesSources?.mapNotNull { file ->
            refreshAndLocateFile(file)
        } ?: emptyList()
    }

    private fun refreshAndLocateFile(file: File): VirtualFile? = refreshIfPossibleAndFindFileByPath(file.path)

    fun diff(old: ScriptClassRootsCache): Updates =
        when (old) {
            EMPTY -> FullUpdate(this)
            this -> NotChanged(this)
            else -> IncrementalUpdates(
                cache = this,
                hasNewRoots = this.hasNewRoots(old),
                hasOldRoots = old.hasNewRoots(this),
                updatedScripts = getChangedScripts(old)
            )
        }

    private fun hasNewRoots(old: ScriptClassRootsCache): Boolean {
        val oldClassRoots = old.allDependenciesClassFiles.toSet()
        val oldSourceRoots = old.allDependenciesSources.toSet()

        return allDependenciesClassFiles.any { it !in oldClassRoots } ||
                allDependenciesSources.any { it !in oldSourceRoots } ||
                old.sdks != sdks
    }

    private fun getChangedScripts(old: ScriptClassRootsCache): Set<String> {
        val changed = mutableSetOf<String>()

        scripts.forEach {
            if (old.scripts[it.key] != it.value) {
                changed.add(it.key)
            }
        }

        old.scripts.forEach {
            if (it.key !in scripts) {
                changed.add(it.key)
            }
        }

        return changed
    }

    interface Updates {
        val cache: ScriptClassRootsCache
        val changed: Boolean
        val hasNewRoots: Boolean
        val hasUpdatedScripts: Boolean
        fun isScriptChanged(scriptPath: String): Boolean
    }

    class IncrementalUpdates(
        override val cache: ScriptClassRootsCache,
        override val hasNewRoots: Boolean,
        private val hasOldRoots: Boolean,
        val updatedScripts: Set<String>
    ) : Updates {
        override val hasUpdatedScripts: Boolean get() = updatedScripts.isNotEmpty()
        override fun isScriptChanged(scriptPath: String): Boolean = scriptPath in updatedScripts

        override val changed: Boolean
            get() = hasNewRoots || updatedScripts.isNotEmpty() || hasOldRoots
    }

    class FullUpdate(override val cache: ScriptClassRootsCache) : Updates {
        override val changed: Boolean get() = true
        override val hasUpdatedScripts: Boolean get() = true
        override fun isScriptChanged(scriptPath: String): Boolean = true

        override val hasNewRoots: Boolean
            get() =
                cache.allDependenciesClassFiles.isNotEmpty() ||
                        cache.allDependenciesSources.isNotEmpty() ||
                        cache.sdks.nonIndexedClassRoots.isNotEmpty() ||
                        cache.sdks.nonIndexedSourceRoots.isNotEmpty()
    }

    class NotChanged(override val cache: ScriptClassRootsCache) : Updates {
        override val changed: Boolean get() = false
        override val hasNewRoots: Boolean get() = false
        override val hasUpdatedScripts: Boolean get() = false
        override fun isScriptChanged(scriptPath: String): Boolean = false
    }
}

private fun refreshIfPossibleAndFindFileByPath(pathString: String): VirtualFile? {
    val nioPath = pathString.toNioPathOrNull() ?: return null
    return when {
        nioPath.notExists() -> {
            scriptingWarnLog("Invalid classpath entry '${nioPath.pathString}', notExists=true")
            null
        }

        nioPath.isDirectory() -> {
            refreshIfAllowedAndFindFile(local(), nioPath.pathString)
        }

        nioPath.isRegularFile() -> {
            refreshIfAllowedAndFindFile(local(), nioPath.pathString)
            refreshIfAllowedAndFindFile(jar(), nioPath.pathString + JAR_SEPARATOR)
        }

        else -> {
            scriptingWarnLog("Invalid classpath entry '${nioPath.pathString}'")
            null
        }
    }
}

private fun refreshIfAllowedAndFindFile(fileSystem: VirtualFileSystem, path: String): VirtualFile? {
    val application = ApplicationManager.getApplication()

    fileSystem.findFileByPath(path)?.let { return it }

    //we cannot use `refreshAndFindFileByPath` under read lock
    if (application.isDispatchThread || !application.isReadAccessAllowed) {
        return fileSystem.refreshAndFindFileByPath(path)
    }

    return null
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.search.refIndex.IncrementalKotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path

internal class BtaKotlinCompilerReferenceIndexStorageImpl(
    private val project: Project,
    private val projectPath: String,
    @Volatile private var lookupStoragesByRoot: Map<Path, BtaLookupInMemoryStorage>,
    @Volatile private var subtypeStoragesByRoot: Map<Path, BtaSubtypeInMemoryStorage>,
) : KotlinCompilerReferenceIndexStorage, IncrementalKotlinCompilerReferenceIndexStorage {

    private val lfs = LocalFileSystem.getInstance()

    override fun getUsages(fqName: FqName): List<VirtualFile> = lookupStoragesByRoot.values
        .asSequence()
        .flatMap { it[fqName] }
        .distinct()
        .mapNotNull { lfs.findFileByNioFile(it) }
        .toList()

    override fun getSubtypesOf(fqName: FqName, deep: Boolean) = subtypeStoragesByRoot.values
        .asSequence()
        .flatMap { it[fqName, deep] }
        .distinct()

    override fun refreshModules(modules: Collection<Module>): Boolean {
        // Rebuild from the current project roots so stale CRI directories
        // (which disappeared after a recompilation or a source set change) do not stay loaded indefinitely
        val updatedCriRoots = modules.flatMapTo(mutableSetOf(), Module::getCriPaths)
        val currentCriRoots = project.getCriPaths()

        // Recreate storages for changed or newly discovered CRI roots, but keep existing instances for unchanged roots
        val refreshedLookupStorages = buildMap {
            for (criRoot in currentCriRoots) {
                val storage = if (criRoot in updatedCriRoots || criRoot !in lookupStoragesByRoot) {
                    BtaLookupInMemoryStorage.create(criRoot, projectPath)
                } else {
                    lookupStoragesByRoot[criRoot]
                }
                if (storage != null) {
                    put(criRoot, storage)
                }
            }
        }

        val refreshedSubtypeStorages = buildMap {
            for (criRoot in currentCriRoots) {
                val storage = if (criRoot in updatedCriRoots || criRoot !in subtypeStoragesByRoot) {
                    BtaSubtypeInMemoryStorage.create(criRoot)
                } else {
                    subtypeStoragesByRoot[criRoot]
                }
                if (storage != null) {
                    put(criRoot, storage)
                }
            }
        }

        lookupStoragesByRoot = refreshedLookupStorages
        subtypeStoragesByRoot = refreshedSubtypeStorages
        return refreshedLookupStorages.isNotEmpty()
    }

    override fun close() = Unit
}

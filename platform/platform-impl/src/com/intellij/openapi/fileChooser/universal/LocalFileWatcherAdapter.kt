// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentHashMap

internal class LocalFileWatcherAdapter : FileWatcherAdapter {
  private val watchKeys = ConcurrentHashMap<Path, WatchKey>()

  override suspend fun subscribe(path: Path): Flow<FileChangeType>? {
    if (!Files.isDirectory(path)) return null
    return flow {
      val watchService = path.fileSystem.newWatchService()
      val key = try {
        path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      }
      catch (e: Exception) {
        LOG.debug("Cannot register watch service for $path", e)
        watchService.close()
        return@flow
      }
      watchKeys[path] = key
      try {
        while (currentCoroutineContext().isActive) {
          val watchKey = watchService.take()
          for (event in watchKey.pollEvents()) {
            val change = when (event.kind()) {
              ENTRY_CREATE -> FileChangeType.CREATED
              ENTRY_DELETE -> FileChangeType.DELETED
              ENTRY_MODIFY -> FileChangeType.CHANGED
              else -> null
            }
            if (change != null) {
              emit(change)
            }
          }
          if (!watchKey.reset()) {
            break
          }
        }
      }
      catch (_: ClosedWatchServiceException) {
        // expected on close
      }
      catch (e: InterruptedException) {
        LOG.debug("Watch service interrupted for $path", e)
      }
      finally {
        key.cancel()
        watchKeys.remove(path)
        try {
          watchService.close()
        }
        catch (e: Exception) {
          LOG.debug("Cannot close watch service for $path", e)
        }
      }
    }.flowOn(Dispatchers.IO)
  }

  override suspend fun unsubscribe(path: Path) {
    val key = watchKeys.remove(path) ?: return
    key.cancel()
  }

  companion object {
    private val LOG = Logger.getInstance(LocalFileWatcherAdapter::class.java)
  }
}
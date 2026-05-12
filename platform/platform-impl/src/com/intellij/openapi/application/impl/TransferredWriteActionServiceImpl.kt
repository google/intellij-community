// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.util.concurrency.TransferredWriteActionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TransferredWriteActionServiceImpl : TransferredWriteActionService {
  override fun runOnEdtWithTransferredWriteActionAndWait(action: Runnable) {
    InternalThreading.invokeAndWaitWithTransferredWriteAction {
      action.run()
    }
  }

  override fun <T> runOnBackgroundThreadWithTransferredWriteActionAndWait(action: Runnable) {
    InternalThreading.executeOnPooledThreadWithTransferredWriteAction {
      action.run()
    }
  }
}
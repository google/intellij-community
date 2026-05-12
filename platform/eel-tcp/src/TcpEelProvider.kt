// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.execution.eel.MultiRoutingFileSystemUtils
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.EelEnvironmentInitializer
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine
import java.nio.file.Path

class TcpEelEnvironmentInitializer : EelEnvironmentInitializer {
  override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine? {
    if (!MultiRoutingFileSystemUtils.isMultiRoutingFsEnabled) {
      return null
    }
    val descriptor = Path.of(path).getEelDescriptor() as? TcpEelDescriptor ?: return null
    val tcpMachine = descriptor.resolveEelMachine() as? TcpEelMachine ?: return null
    tcpMachine.toEelApi(descriptor) // deploy ijent
    return tcpMachine
  }
}

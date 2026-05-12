// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelOsFamily
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * NIO Path compatibility extension for [EelMachine.ownsDescriptor].
 * Resolves the [EelDescriptor] from the NIO path and delegates.
 */
@ApiStatus.Experimental
fun EelMachine.ownsPath(path: Path): Boolean = ownsDescriptor(path.getEelDescriptor())

@ApiStatus.Experimental
fun Path.getEelDescriptor(): EelDescriptor {
  return EelNioFsBackend.instance?.resolveDescriptor(this) ?: LocalEelDescriptor
}

@get:ApiStatus.Experimental
val Path.osFamily: EelOsFamily get() = getEelDescriptor().osFamily

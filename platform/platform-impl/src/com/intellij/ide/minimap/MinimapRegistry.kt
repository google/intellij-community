// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils

internal object MinimapRegistry {
  private enum class MinimapMode { DISABLED, LEGACY, NEW }
  internal const val MODE_KEY = "editor.minimap.mode"

  private fun mode(): MinimapMode = try {
    val value = Registry.get(MODE_KEY).selectedOption
    MinimapMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MinimapMode.DISABLED
  }
  catch (_: Exception) {
    MinimapMode.DISABLED
  }

  // Hard-coded IDE allow-list intentionally. Other products that want to opt in can extend this check directly.
  fun isEnabled(): Boolean = mode() != MinimapMode.DISABLED && PlatformUtils.isPyCharm()
  fun isLegacy(): Boolean = mode() == MinimapMode.LEGACY
}

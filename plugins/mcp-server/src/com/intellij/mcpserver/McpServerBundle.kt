package com.intellij.mcpserver

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

const val BUNDLE: @NonNls String = "messages.McpServerBundle"

internal object McpServerBundle {
  private val INSTANCE = DynamicBundle(McpServerBundle::class.java, BUNDLE)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = INSTANCE.getMessage(key, *params)
}

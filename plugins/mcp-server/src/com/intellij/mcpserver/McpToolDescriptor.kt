package com.intellij.mcpserver

import com.intellij.openapi.util.NlsSafe
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations

class McpToolDescriptor(
  /**
   * Tool name
   */
  val name: @NlsSafe String,

  /**
   * Tool title
   */
  val title: @NlsSafe String? = null,

  /**
   * Tool description
   */
  val description: @NlsSafe String,

  /**
   * Tool category, only for UI and filtering purposes
   */
  val category: McpToolCategory,

  val fullyQualifiedName: @NlsSafe String,

  /**
   * Input schema for the tool
   */
  val inputSchema: McpToolSchema,
  val outputSchema: McpToolSchema? = null,
  val annotations: ToolAnnotations? = null,
)
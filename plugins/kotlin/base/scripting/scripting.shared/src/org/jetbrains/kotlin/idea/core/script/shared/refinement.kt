// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.getScriptCollectedData
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.scripting.resolve.toKtFileSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode

/**
 * Refines the script compilation configuration for a provided script and related context.
 *
 * This method performs script compilation configuration refinement by analyzing the script, its definition,
 * and project context. If an initial `ScriptCompilationConfiguration` is not provided, a default one associated
 * with the `ScriptDefinition` is used. The method uses collected data during the analysis process to produce the
 * resulting `ScriptCompilationConfiguration`.
 *
 * @param script The source code of the script to refine the compilation configuration for.
 * @param definition The script definition that specifies how the script should be handled, including defaults
 *                   for compilation configuration.
 * @param project The IntelliJ IDEA project in which the script resides.
 * @param providedConfiguration The initial script compilation configuration, if already available. If `null`,
 *                               the default configuration from the script definition will be used.
 * @return The resulting refined script compilation configuration wrapped in a `ScriptCompilationConfigurationResult`.
 */
suspend fun smartRefineScriptCompilationConfiguration(
    script: SourceCode,
    definition: ScriptDefinition,
    project: Project,
    providedConfiguration: ScriptCompilationConfiguration?,
): ScriptCompilationConfigurationResult {
    val ktFileSource = script.toKtFileSource(definition, project)
    val compilationConfiguration = providedConfiguration ?: definition.compilationConfiguration
    val collectedData = smartReadAction(project) {
        getScriptCollectedData(ktFileSource.ktFile, compilationConfiguration, definition.contextClassLoader)
    }
    return refineScriptCompilationConfiguration(
        compilationConfiguration,
        script,
        collectedData,
        null,
        ktFileSource,
        definition
    )
}
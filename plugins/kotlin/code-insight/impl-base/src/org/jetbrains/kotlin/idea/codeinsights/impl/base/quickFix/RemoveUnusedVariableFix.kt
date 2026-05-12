// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.renameToUnderscore
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtProperty

class RemoveUnusedVariableFix(
    element: KtNamedDeclaration,
    isSimpleCase: Boolean,
    val couldBeAnExplicitlyIgnoredValue: Boolean
) : KotlinModCommandQuickFix<KtNamedDeclaration>() {
    private val name: @IntentionName String = when (element) {
        is KtDestructuringDeclarationEntry -> {
            KotlinBundle.message("rename.to.underscore")
        }

        is KtProperty if couldBeAnExplicitlyIgnoredValue -> {
            KotlinBundle.message("rename.0.to.explicitly.ignore.return.value", element.name.toString())
        }

        else -> {
            if (isSimpleCase) {
                KotlinBundle.message(if (element is KtParameter) "remove.parameter.0" else "remove.variable.0", element.name.toString())
            } else {
                KotlinBundle.message("remove.variable.change.semantics", element.name.toString())
            }
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.variable")

    override fun getName(): String = name
    override fun applyFix(
        project: Project,
        element: KtNamedDeclaration,
        updater: ModPsiUpdater
    ) {
        when (element) {
            is KtDestructuringDeclarationEntry -> renameToUnderscore(element)
            is KtProperty -> {
                if (couldBeAnExplicitlyIgnoredValue) {
                    renameToUnderscore(element)
                } else {
                    // Always remove the entire statement for both simple and complex cases
                    element.delete()
                }
            }
            is KtParameter -> {
                val parameterList = element.parent as? KtParameterList ?: return
                val ownerFunction = parameterList.ownerFunction ?: return
                val arrow = ownerFunction.node.findChildByType(KtTokens.ARROW) ?: return

                parameterList.delete()
                ownerFunction.node.removeChild(arrow)
            }
        }
    }
}

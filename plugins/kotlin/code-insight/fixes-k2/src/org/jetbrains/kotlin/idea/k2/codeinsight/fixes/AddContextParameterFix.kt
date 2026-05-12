// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class AddContextParameterFix(
    element: KtCallElement,
    private val contextTypes: List<String>
) : PsiUpdateModCommandAction<KtCallElement>(element) {
    override fun invoke(context: ActionContext, element: KtCallElement, updater: ModPsiUpdater) {
        val containingFunction = element.getStrictParentOfType<KtNamedFunction>() ?: return

        val psiFactory = KtPsiFactory(context.project)
        val existingParameters = containingFunction.contextParameters
        val allParams = existingParameters.map { it.text } + contextTypes.map { "_: $it" }
        val contextClause = allParams.joinToString(", ", "context(", ")")

        val functionBody = if (existingParameters.isNotEmpty()) {
            val oldContextEnd = existingParameters.last().parent.textRange.endOffset - containingFunction.textRange.startOffset
            containingFunction.text.substring(oldContextEnd).trimStart()
        } else {
            containingFunction.text
        }

        val newFunction = psiFactory.createFunction("$contextClause $functionBody")
        val replacedFunction = containingFunction.replace(newFunction) as KtNamedFunction
        updater.select(replacedFunction.contextParameters.getOrNull(existingParameters.size)?.nameIdentifier ?: return)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.context.parameter.family")

}
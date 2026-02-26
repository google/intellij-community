// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureInsightsProvider

class GradleProjectStructureInsightsProvider : ProjectStructureInsightsProvider {
    override fun isInSpecialSrcDirectory(psiElement: PsiElement): Boolean = psiElement.isUnderSpecialSrcDirectory()
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.jetbrains.python.requirements.psi.SimpleName
import com.jetbrains.python.requirements.psi.Visitor

/**
 * Hand-maintained replacement for the generated `SimpleNameImpl.java` so the package-name token
 * can host its own [PsiReference] (registered against `SimpleName` in
 * `RequirementsReferenceContributor`). `ASTWrapperPsiElement.getReferences()` returns empty
 * unless the element opts in via [ContributedReferenceHost].
 */
class SimpleNameImpl(node: ASTNode) : ASTWrapperPsiElement(node), SimpleName, ContributedReferenceHost {
  fun accept(visitor: Visitor) {
    visitor.visitSimpleName(this)
  }

  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is Visitor) accept(visitor)
    else super.accept(visitor)
  }

  override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}

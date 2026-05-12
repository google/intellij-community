// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider

internal interface ValidityEvaluator {
  fun isRecreatedViewProviderIsIdentical(
    virtualFile: VirtualFile,
    provider: AbstractFileViewProvider,
    context: CodeInsightContext,
  ): Boolean

  fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean

  fun reanimateProviderIfNecessary(vFile: VirtualFile, viewProvider: FileViewProvider?): FileViewProvider?
}
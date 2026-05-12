// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy interface for expanding a postfix template as a {@link ModCommand},
 * suitable for use in ModCompletion and preview.
 *
 * <h3>ModCommand-based postfix template lifecycle</h3>
 * <ol>
 *   <li>{@link PostfixTemplate#isApplicableForModCommand()} — queried to check whether the template
 *       opts in to ModCommand-based expansion.</li>
 *   <li>{@link PostfixTemplate#createModExpander()} — called to obtain this strategy object.
 *       Returns {@code null} if the template does not support ModCommand expansion.</li>
 *   <li>{@link #expand} — invoked with the original {@link ActionContext} and the template key range.
 *       The implementation is responsible for:
 *       <ul>
 *         <li>creating a non-physical copy of the file and deleting the template key from it;</li>
 *         <li>calling {@link PostfixTemplateProvider#prepareCopyForModCommand} if the provider needs
 *             to pre-process the copy (this is <b>not</b> called automatically);</li>
 *         <li>resolving the target expression(s) and performing the actual expansion.</li>
 *       </ul>
 *   </li>
 * </ol>
 * Two standard implementations are provided:
 * <ul>
 *   <li>{@link ExpressionSelectorModExpander} — for templates that use a
 *       {@link PostfixTemplateExpressionSelector} to choose the target expression and a
 *       {@link ExpressionSelectorModExpander.ModExpandAction} to perform the per-element expansion;</li>
 *   <li>{@link com.intellij.codeInsight.template.postfix.templates.editable.EditableTemplateModExpander} —
 *       for {@link com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplate},
 *       which resolves expressions and expands via live template substitution.</li>
 * </ul>
 *
 * @see PostfixTemplate#createModExpander()
 * @see ExpressionSelectorModExpander
 * @see com.intellij.codeInsight.template.postfix.templates.editable.EditableTemplateModExpander
 * @see ExpressionSelectorModExpander.ModExpandAction
 */
@ApiStatus.Experimental
public interface PostfixModExpander {
  /**
   * Expands the template as a {@link ModCommand}, suitable for use in ModCompletion and preview/batch modes.
   */
  @NotNull ModCommand expand(@NotNull ActionContext ctx,
                              @NotNull PostfixTemplateProvider provider,
                              @NotNull TextRange keyRange);
}
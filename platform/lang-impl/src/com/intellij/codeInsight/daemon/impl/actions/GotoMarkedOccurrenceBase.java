/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public abstract class GotoMarkedOccurrenceBase extends BaseCodeInsightAction implements CodeInsightActionHandler {

  private final Comparator<Integer> myComparator;

  /**
   * @param comparator defines ordering of occurrences.
   */
  public GotoMarkedOccurrenceBase(Comparator<Integer> comparator) {
    myComparator = comparator;
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    final int caretOffset = editor.getCaretModel().getOffset();
    final int startOffset = file.getTextRange().getStartOffset();
    final int endOffset = file.getTextRange().getEndOffset();
    final Ref<Integer> first = new Ref<>();
    final Ref<Integer> next = new Ref<>();
    DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, null, startOffset, endOffset, info -> {
      if (info.type == HighlightInfoType.ELEMENT_UNDER_CARET_READ || info.type == HighlightInfoType.ELEMENT_UNDER_CARET_WRITE) {
        if (myComparator.compare(info.startOffset, caretOffset) > 0) {
          if (next.isNull() || myComparator.compare(next.get(), info.startOffset) > 0) {
            next.set(info.startOffset);
          }
        }
        if (first.isNull() || myComparator.compare(first.get(), info.startOffset) > 0) {
          first.set(info.startOffset);
        }
      }
      return true;
    });
    if (!next.isNull()) {
      moveCaret(editor, editor.getCaretModel().getCurrentCaret(), next.get());
    } else if (!first.isNull()) {
      moveCaret(editor, editor.getCaretModel().getCurrentCaret(), first.get());
    } else {
      // It's ok, do nothing.
    }
  }

  private static void moveCaret(Editor editor, Caret caret, int offset) {
    caret.removeSelection();
    caret.moveToOffset(offset);
    EditorModificationUtil.scrollToCaret(editor);
  }
}

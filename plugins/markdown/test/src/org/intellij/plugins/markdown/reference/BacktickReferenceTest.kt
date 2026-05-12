// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.references.backtick.BacktickReference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BacktickReferenceTest : BasePlatformTestCase() {
  @Test
  fun `test unresolved reference does not yield any errors`() {
    val reference = configureAndGetReferenceAtCaret("There is an `Java<caret>Class` backtick")
    assertTrue(reference is BacktickReference)
    myFixture.checkHighlighting()
  }

  @Test
  fun `test blank code span does not have reference`() {
    val reference = configureAndGetReferenceAtCaret("There is an ` <caret>  ` backtick")
    assertNull(reference)
  }

  @Test
  fun `test reference resolves to original element`() {
    val javaClass = createJavaClass()
    val reference = configureAndGetReferenceAtCaret("There is an `Java<caret>Class` backtick")
    assertTrue(myFixture.psiManager.areElementsEquivalent(javaClass, reference!!.resolve()))
  }

  @Test
  fun `test renaming original element updates markdown reference`() {
    val javaClass = createJavaClass()
    myFixture.configureByText("some.md", "There is an `JavaClass` backtick")
    myFixture.renameElement(javaClass, "NewJavaClass")
    myFixture.checkResult("There is an `NewJavaClass` backtick")
  }

  private fun createJavaClass(): PsiClass {
    val file = myFixture.addFileToProject("JavaClass.java", "class JavaClass {}")
    return file.children.single { it is PsiClass } as PsiClass
  }

  private fun configureAndGetReferenceAtCaret(text: String): BacktickReference? {
    val file = myFixture.configureByText("some.md", text)
    return file.findReferenceAt(myFixture.editor.caretModel.offset) as BacktickReference?
  }
}

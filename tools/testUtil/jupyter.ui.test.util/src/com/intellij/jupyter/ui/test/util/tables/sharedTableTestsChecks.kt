package com.intellij.jupyter.ui.test.util.tables

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.notebooks.LastCell
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.getClipboardText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

// Shared tables test utilities used across Kotlin and Python notebooks to avoid duplication
fun NotebookEditorUiComponent.checkTableSize() {
  firstTable.should { hasText("3 rows × 5 cols") }
}

fun NotebookEditorUiComponent.checkTablePaging() {
  firstTable.run {
    changePageSizeTo(2)

    tableView.should { rowCount() == 2 }

    goNextPage()
    tableView.rowCount() shouldBe 1

    goPreviousPage()
    tableView.rowCount() shouldBe 2
  }
}

fun NotebookEditorUiComponent.checkTableCellContextMenuActions(column: Int) {
  lastTable.run {
    tableView.rightClickCell(0, column)

    driver.ideFrame {
      popup().run {
        waitOneText("Copy").strictClick()
      }
    }

    waitFor { driver.getClipboardText() == "Daniel" }
  }
}


fun NotebookEditorUiComponent.checkTableScenario(codeForTable: String, type: String,
                                                        runAllCellsAndAwait: NotebookEditorUiComponent.(kotlin.time.Duration) -> Unit = { runAllCellsAndWaitExecuted() }) {
  val column = when (type) {
    "Kotlin" -> 0
    "Jupyter" -> 1
    else -> error("Invalid test type: '$type'. Expected 'Kotlin' or 'Jupyter'.")
  }

  step("Create a table") {
    pasteToCell(LastCell, codeForTable)
    runAllCellsAndAwait(1.minutes)
    waitFor("Expect 1 table rendered") {
      notebookTables.size == 1
    }
  }

  step("Check the table") {
    notebookTables.first().run {
      // Check the first row
      tableView.should { getValueAt(0, column).equals("a") }
    }
  }
}

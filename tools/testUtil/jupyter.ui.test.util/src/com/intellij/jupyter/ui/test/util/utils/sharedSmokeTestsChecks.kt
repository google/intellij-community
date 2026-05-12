package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.IdeTheme
import com.intellij.driver.sdk.changeTheme
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.notebooks.FirstCell
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.NotebookType
import com.intellij.driver.sdk.ui.components.notebooks.createNewNotebook
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import com.intellij.jupyter.ui.test.util.codeinsight.checkLookAndFeel
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.softRunAllCellsAndWaitExecuted
import java.awt.Color
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Shared smoke test utilities used across Kotlin and Python notebooks to avoid duplication
fun Driver.checkColorThemeChange() = ideFrame {
  step("Change color theme to light") {
    changeTheme(IdeTheme.LIGHT)
    notebookEditor { checkLookAndFeel(IdeTheme.LIGHT) }
  }

  step("Change color theme to dark") {
    changeTheme(IdeTheme.DARK)
    notebookEditor { checkLookAndFeel(IdeTheme.DARK) }
  }
}

fun Driver.checkColorThemeChangeForAllNotebookElements() {
  fun Color.toHex(): String = "#%02X%02X%02X".format(red, green, blue)
  fun Color.luma(): Int = (0.2126 * red + 0.7152 * green + 0.0722 * blue).roundToInt()
  fun assertLight(color: Color, what: String) = waitFor(message = "$what should be light, actual=${color.toHex()} luma=${color.luma()}", timeout = 15.seconds) { color.luma() > 180 }
  fun assertDark(color: Color, what: String) = waitFor(message = "$what should be dark, actual=${color.toHex()} luma=${color.luma()}", timeout = 15.seconds) { color.luma() < 100 }

  ideFrame {
    notebookEditor {
      // Prepare cells: code, markdown, table, static matplotlib plot
      step("Prepare notebook content: code + markdown + table + plot") {
        pasteToCell(FirstCell, "print(1)")
        addMarkdownCell("# header")
        addCodeCell(
          """
            import pandas as pd
            df = pd.DataFrame([[1,2],[3,4]], columns=["A","B"])
            df
          """.trimIndent()
        )
        addCodeCell(
          """
            %matplotlib inline
            import matplotlib.pyplot as plt
            import math
            x = [i * (6.28 / 99) for i in range(100)]
            y = [math.sin(val) for val in x]

            plt.figure(figsize=(3,2))
            plt.plot(x, y)
            plt.show()
          """.trimIndent()
        )
        softRunAllCellsAndWaitExecuted(5.minutes)
        waitFor("Markdown rendered", 20.seconds) { jcefOffScreens.isNotEmpty() }
        waitFor("Table rendered", 20.seconds) { notebookTables.isNotEmpty() }
        waitFor("Plot rendered", 30.seconds) { imagePanel.isNotEmpty() || jcefOffScreens.size >= 2 }
      }

      step("Validate jcef height") {
        val regex = Regex("""(\d+)x(\d+)""")
        val matchResult = regex.find(jcefOffScreens.last().component.toString())
        val height = matchResult?.groupValues[2]?.toInt()

        should("Jcef panel should have non-zero height") {
          height!! > 0
        }
      }

      step("Validate static plot height") {
        val regex = Regex("""(\d+)x(\d+)""")
        val matchResult = regex.find(imagePanel.last().component.toString())
        val height = matchResult?.groupValues[2]?.toInt()

        should("Image panel should have non-zero height") {
          height!! > 0
        }
      }

      step("Validate colors in LIGHT theme") {
        changeTheme(IdeTheme.LIGHT)
        checkLookAndFeel(IdeTheme.LIGHT)

        val md = jcefOffScreens.firstOrNull() ?: imagePanel.first() // markdown JCEF or fallback image
        val mdColor = md.getColor(null)
        assertLight(mdColor, "Markdown background")

        val table = notebookTables.first()
        val tableColor = table.getColor(null)
        assertLight(tableColor, "Table background")
      }

      step("Validate colors in DARK theme") {
        changeTheme(IdeTheme.DARK)
        checkLookAndFeel(IdeTheme.DARK)

        val md = jcefOffScreens.firstOrNull() ?: imagePanel.first()
        val mdColor = md.getColor(null)
        assertDark(mdColor, "Markdown background")

        val table = notebookTables.first()
        val tableColor = table.getColor(null)
        assertDark(tableColor, "Table background")
      }
    }
  }
}

fun NotebookEditorUiComponent.checkCreateNewNotebook(type: NotebookType, name: String = "test") {
  step("Create a new notebook") {
    driver.createNewNotebook(name, type)
  }
  step("Check the notebook editor is opened") {
    waitForOne(
      "Waiting for the notebook editor to appear",
      timeout = 10.seconds,
      getter = { notebookCellEditors }
    )
  }
}

//issue = "PY-75616"
fun NotebookEditorUiComponent.checkMarkdownCellRendering() {
  step("Create a markdown cell") {
    addMarkdownCell("# header")
    runCell()
  }
  step("Check the cell is rendered") {
    waitFor("The MD cell should be rendered", 15.seconds) {
      jcefOffScreens.isNotEmpty()
    }
  }
  step("Check markdown cell is not rendered after double click") {
    jcefOffScreens.first().doubleClick()
    waitFor("The MD cell should be rendered", 15.seconds) {
      jcefOffScreens.isEmpty()
    }
  }
  step("Check markdown cell is rendered after switching focus") {
    clickOnCell(FirstCell)
    waitFor("The MD cell should be rendered", 15.seconds) {
      jcefOffScreens.isNotEmpty()
    }
  }
  val expectedMdCellHtmlContent = "<h1 data-jupyter-id=\"header\">header"
  step("Check the cell content") {
    jcefOffScreens.first().should {
      htmlSource.contains(expectedMdCellHtmlContent)
    }
  }
}

fun NotebookEditorUiComponent.checkRunCellsAndCleanUpOutputs(
  runAllCellsAndAwait: NotebookEditorUiComponent.(kotlin.time.Duration) -> Unit = { timeout -> runAllCellsAndWaitExecuted(timeout) },
) {
  step("Add one more code cell") {
    pasteToCell(FirstCell, "print(1)")
    addCodeCell("print(2)")
  }
  step("Run cells and check the outputs") {
    runAllCellsAndAwait(1.minutes)
    should("The cells should be executed", 30.seconds) {
      notebookCellOutputs.size == 2
    }
  }
  step("Clear outputs check") {
    clearAllOutputs()
    waitFor("The cells should be cleaned up", 15.seconds) {
      notebookCellOutputs.isEmpty()
      &&
      notebookCellExecutionInfos.isEmpty()
    }
  }
}

/**
 * Repeatedly runs all cells added in [createInitialCells] to reveal possible
 * race conditions and other issues in cell execution logic
 */
fun Driver.runAllCellsRepeatedly(
  times: Int = 50,
  createInitialCells: NotebookEditorUiComponent.() -> Unit = {},
) {
  withNotebookEditor {
    step("Add initial cells and run them") {
      createInitialCells()
    }
    step("Run cells over and over") {
      repeat(times) {
        runAllCellsAndWaitExecuted(10.seconds)
      }
    }
  }
}
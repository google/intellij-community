// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.intList
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.ListCellRenderer

internal class LcrExamplesPanel : UISandboxPanel {

  override val title: String = "Examples"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox

      row {
        enabled = checkBox("Enabled")
          .selected(true)
          .component
      }

      indent {
        row {
          panel {
            val simpleTextLcrExample = createSimpleTextLcrExample()
            row {
              comment(simpleTextLcrExample.comment)
            }
            row {
              comboBox(simpleTextLcrExample.items, simpleTextLcrExample.renderer).applyToComponent {
                selectedIndex = 0
              }.align(AlignX.FILL)
            }
            row {
              scrollCell(JBList(simpleTextLcrExample.items)).applyToComponent {
                cellRenderer = simpleTextLcrExample.renderer
              }.align(Align.FILL)
            }.resizableRow()
          }.align(Align.FILL)

          panel {
            val itemsLcrExample = createItemsLcrExample()
            row {
              comment(itemsLcrExample.comment)
            }
            row {
              comboBox(itemsLcrExample.items, itemsLcrExample.renderer).applyToComponent {
                selectedIndex = 0
              }.align(AlignX.FILL)
            }
            row {
              cell(JBList(itemsLcrExample.items)).applyToComponent {
                cellRenderer = itemsLcrExample.renderer
                border = JBUI.Borders.customLine(JBColor.border())
              }.align(Align.FILL)
            }.resizableRow()
          }.align(Align.FILL)
            .resizableColumn()

          panel {
            val featuresLcrExample = createFeaturesLcrExample()
            row {
              comment(featuresLcrExample.comment)
            }
            row {
              comboBox(featuresLcrExample.items, featuresLcrExample.renderer).applyToComponent {
                selectedIndex = 0
                ComboboxSpeedSearch.installSpeedSearch(this, ::featuresItemToSearchableConverter)
              }.align(AlignX.FILL)
                .label("Swing popup:", LabelPosition.TOP)
            }
            row {
              comboBox(featuresLcrExample.items, featuresLcrExample.renderer).applyToComponent {
                selectedIndex = 0
                isSwingPopup = false
              }.align(AlignX.FILL)
                .label("Non-Swing popup:", LabelPosition.TOP)
            }
            row {
              cell(JBList(featuresLcrExample.items)).applyToComponent {
                cellRenderer = featuresLcrExample.renderer
                border = JBUI.Borders.customLine(JBColor.border())
                TreeUIHelper.getInstance().installListSpeedSearch(this, ::featuresItemToSearchableConverter)
              }.align(Align.FILL)
            }.resizableRow()
          }.align(Align.FILL)
            .resizableColumn()
        }
      }.enabledIf(enabled.selected)
    }
  }

  private fun createSimpleTextLcrExample(): LcrExample<Int> {
    @Suppress("DialogTitleCapitalization")
    return LcrExample(
      comment = "<b>textListCellRenderer</b> maps items<br>to their simple string representation",
      items = intList(),
      renderer = textListCellRenderer { "Item $it" }
    )
  }

  private fun createItemsLcrExample(): LcrExample<Int> {
    @Suppress("DialogTitleCapitalization")
    return LcrExample(
      comment = "<b>listCellRenderer</b> for complex renderers.<br>All possible items",
      items = intList(11),
      renderer = listCellRenderer {
        when (value) {
          1 -> {
            separator {
              text = "Text"
            }
            text("Normal text")
          }
          2 -> {
            text("Red text") {
              foreground = JBColor.RED
            }
          }
          3 -> text("Italic") {
            attributes = SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES
          }
          4 -> text("Bold") {
            attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
          }
          5 -> {
            text("With comment") {
              align = LcrInitParams.Align.LEFT
            }
            text("Comment") {
              foreground = greyForeground
            }
          }
          6 -> {
            text("With small comment") {
              align = LcrInitParams.Align.LEFT
            }
            text("Comment") {
              attributes = SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES
            }
          }

          7 -> {
            separator {
              text = "Icon"
            }
            icon(AllIcons.Empty)
            text("With reserved space for icon")
          }
          8 -> {
            icon(AllIcons.General.Information)
            text("With icon")
          }
          9 -> {
            text("Badge usage")
            icon(Badge.beta)
          }

          10 -> {
            separator {
              text = "Switch"
            }
            text("Switch is off")
            switch(false) {
              align = LcrInitParams.Align.RIGHT
            }
          }
          11 -> {
            text("Switch is on")
            switch(true) {
              align = LcrInitParams.Align.RIGHT
            }
          }
        }
      }
    )
  }

  private val SEARCHABLE_COMMENT = "Searchable comment"

  private fun featuresItemToSearchableConverter(s: String): String {
    return when (s) {
      "Normal text 2" -> "$s $SEARCHABLE_COMMENT"
      else -> s
    }
  }

  private fun createFeaturesLcrExample(): LcrExample<String> {
    val items = listOf(
      "Normal text",
      "Normal text 2",
      "Normal text 3",
      "Default bg",
      "Yellow bg",
      "Pink bg",
      "Tooltip",
      "UI Inspector"
    )

    @Suppress("DialogTitleCapitalization")
    return LcrExample(
      comment = "<b>listCellRenderer</b> features<br>Speed search in ComboBox-es/List",
      items = items,
      renderer = listCellRenderer("") {
        when (value) {
          items[0] -> {
            separator {
              text = "Text"
            }
            text(value) {
              speedSearch { }
            }
          }
          items[1] -> {
            text(value) {
              align = LcrInitParams.Align.LEFT
              speedSearch { }
            }
            text(SEARCHABLE_COMMENT) {
              speedSearch { }
              foreground = greyForeground
            }
            toolTipText = "The row is searchable by both the text and the comment"
            icon(AllIcons.General.ContextHelp)
          }
          items[2] -> {
            text(value) {
              align = LcrInitParams.Align.LEFT
              speedSearch { }
            }
            text("Non-searchable comment") {
              foreground = greyForeground
            }
            toolTipText = "The row is searchable by the main text only"
            icon(AllIcons.General.ContextHelp)
          }

          items[3] -> {
            separator {
              text = "Background"
            }

            text(value) {
              speedSearch { }
            }
          }
          items[4] -> {
            background = JBColor.YELLOW
            text(value) {
              speedSearch { }
            }
          }
          items[5] -> {
            background = JBColor.PINK
            text(value) {
              speedSearch { }
            }
          }

          items[6] -> {
            separator {
              text = "Other"
            }

            toolTipText = "Tooltip for the 'Tooltip' row"
            text(value)
            icon(AllIcons.General.ContextHelp)
          }
          items[7] -> {
            toolTipText = "Use the UI Inspector to see additional item properties"
            text(value)
            icon(AllIcons.General.ContextHelp)
            uiInspectorContext = listOf(PropertyBean("Item id", value), PropertyBean("Item source", "UI Sandbox"))
          }
        }
      }
    )
  }
}

private data class LcrExample<T>(
  val comment: @NlsContexts.DetailedDescription String,
  val items: List<T>,
  val renderer: ListCellRenderer<T?>,
)

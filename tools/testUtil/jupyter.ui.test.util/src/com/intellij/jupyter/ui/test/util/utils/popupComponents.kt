package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame

fun Driver.getFilterViewPanel(): UiComponent {
  return ideFrame().x("//div[@class='HeavyWeightWindow']//div[@class='MyContentPanel']")
}

fun Driver.getPopups(): UIComponentsList<UiComponent> {
  return ideFrame().xx("//div[@class='HeavyWeightWindow']")
}

fun Driver.getDialogPanel(): UiComponent {
  return ideFrame().x("//div[@class='MyDialog']")
}

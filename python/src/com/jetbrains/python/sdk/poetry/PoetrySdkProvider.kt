package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.jetbrains.python.sdk.PySdkProvider
import org.jdom.Element
import javax.swing.Icon

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

internal class PoetrySdkProvider : PySdkProvider {

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return PyPoetrySdkAdditionalData.load(element)
  }
}

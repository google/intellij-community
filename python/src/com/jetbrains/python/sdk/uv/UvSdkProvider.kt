// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.python.community.impl.uv.common.icons.PythonCommunityImplUVCommonIcons
import com.jetbrains.python.sdk.PySdkProvider
import org.jdom.Element
import javax.swing.Icon

internal class UvSdkProvider : PySdkProvider {

  override fun loadAdditionalDataForSdk(element: Element): SdkAdditionalData? {
    return UvSdkAdditionalData.load(element)
  }
}
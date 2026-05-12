// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

class PyWelcomeScreenFreeModeTracker : AppLifecycleListener {
  override fun appStarted() {
    if (AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)) {
      return
    }
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(FREE_MODE_OVERRIDE_DONE, false)) {
      return
    }
    properties.setValue(FREE_MODE_OVERRIDE_DONE, true)
    if (PythonSdkUtil.isFreeTier() || InitialConfigImportState.isNewUser()) {
      AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, true)
    }
  }

  companion object {
    private const val FREE_MODE_OVERRIDE_DONE = "pycharm.welcome.free.mode.override"
  }
}

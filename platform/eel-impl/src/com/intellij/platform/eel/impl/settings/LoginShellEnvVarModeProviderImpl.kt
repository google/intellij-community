// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.settings

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.eel.impl.EelImplBundle
import com.intellij.platform.eel.provider.LoginShellEnvVarMode
import com.intellij.platform.eel.provider.LoginShellEnvVarModeProvider

internal class LoginShellEnvVarModeProviderImpl : LoginShellEnvVarModeProvider {
  /**
   * The advanced-setting-side enum, kept nested so its localized [toString] (which depends on
   * [EelImplBundle], living in `eel-impl`) doesn't leak into the public `eel` module. The XML
   * `<advancedSetting enumClass="…">` references this nested type by its binary name.
   */
  @Suppress("unused") // Referenced by FQN from intellij.platform.eel.impl.xml
  enum class EnvVarShellMode {
    LOGIN_INTERACTIVE {
      override fun toString(): String =
        EelImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.interactive")
    },
    LOGIN_NON_INTERACTIVE {
      override fun toString(): String =
        EelImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.non.interactive")
    },
  }

  override fun get(): LoginShellEnvVarMode =
    when (AdvancedSettings.getEnum("container.environments.env.var.shell.mode", EnvVarShellMode::class.java)) {
      EnvVarShellMode.LOGIN_INTERACTIVE -> LoginShellEnvVarMode.LOGIN_INTERACTIVE
      EnvVarShellMode.LOGIN_NON_INTERACTIVE -> LoginShellEnvVarMode.LOGIN_NON_INTERACTIVE
    }
}

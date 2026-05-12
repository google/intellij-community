// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * IJent functionality operates with many coroutine scopes. It's easy to confuse them.
 *
 * These tag types help to distinguish different lifetimes and prevent some bugs in compile-time.
 */
@file:JvmName("IjentScopes")

package com.intellij.platform.ijent

import com.intellij.platform.eel.channels.EelDelicateApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job


/**
 * A scope that owns one or many [IjentScope].
 *
 * While writing tests, you may create an instance for any scope.
 * While writing production code, you may do it also,
 * but likely you need to call [com.intellij.platform.eel.EelMachine.toEelApi] instead.
 *
 * Notice: [IjentScope] may be an indirect child scope of [ParentOfIjentScopes]. There may be scopes in between.
 *
 * The class intentionally doesn't implement [CoroutineScope] itself for avoiding unintentional upcasting.
 */
class ParentOfIjentScopes(val s: CoroutineScope) {
  init {
    require(s.coroutineContext[Job] != null) {
      "Scope $s has no Job"
    }
  }
}

/**
 * This scope is created right before launching the IJent executable,
 * and cancellation of this scope triggers termination of the IJent process.
 *
 * The scope is **NOT a supervisor scope**. Any failed coroutine destroys the whole scope and terminates IJent.
 *
 * Only SPI implementations that actually define the internal logic of launching an IJent process
 * are supposed to create instances of this class.
 * If you need to launch IJent, look at [com.intellij.platform.eel.EelMachine.toEelApi]
 * or for methods that accept [ParentOfIjentScopes] as a parameter.
 *
 * The class intentionally doesn't implement [CoroutineScope] itself for avoiding unintentional upcasting.
 */
class IjentScope @EelDelicateApi constructor(
  val parent: ParentOfIjentScopes,
  val s: CoroutineScope,
)

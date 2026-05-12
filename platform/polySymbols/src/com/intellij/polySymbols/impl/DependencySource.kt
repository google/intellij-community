// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer

/**
 * Carrier of a materialized symbol's dependency roots. Flips from
 * [FromSpecs] (initial instance, still in its creation read action) to
 * [FromPointers] the first time a pointer is needed, and subsequent
 * cross-read-action instances carry [FromPointers] onward.
 */
internal sealed interface DependencySource {
  val isEmpty: Boolean

  /** Snapshot the current values of all deps. Null on failure. */
  fun snapshot(): List<Any>?

  /** Pointer list for long-term survival — materialized lazily by [FromSpecs]. */
  fun pointers(): List<Pointer<out Any>>

  class FromSpecs(val specs: List<DepSpec<*>>) : DependencySource {
    override val isEmpty: Boolean get() = specs.isEmpty()
    override fun snapshot(): List<Any> {
      val values = ArrayList<Any>(specs.size)
      for (spec in specs) values += spec.currentValue()
      return values
    }

    private val lazyPointers: List<Pointer<out Any>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
      specs.map { it.toPointer() }
    }

    override fun pointers(): List<Pointer<out Any>> = lazyPointers
  }

  class FromPointers(private val pointers: List<Pointer<out Any>>) : DependencySource {
    override val isEmpty: Boolean get() = pointers.isEmpty()
    override fun snapshot(): List<Any>? {
      val values = ArrayList<Any>(pointers.size)
      for (pointer in pointers) values += pointer.dereference() ?: return null
      return values
    }

    override fun pointers(): List<Pointer<out Any>> = pointers
  }
}

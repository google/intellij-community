// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType

data class RefMethods(
  val getter: QualifiedName,
  val getterBuilder: String,
  val setter: QualifiedName,
  val many: Boolean = false
)

fun ObjProperty<*, *>.refNames(): RefMethods {
  if (!valueType.isRefType()) error("Call this on ref field")
  return when (valueType) {
    is ValueType.ObjRef -> constructCode(valueType)
    is ValueType.Optional -> constructCode((this.valueType as ValueType.Optional<*>).type)
    is ValueType.List<*> -> RefMethods(Instrumentation.getManyChildrenBuilders, "getManyChildrenBuilders", Instrumentation.replaceChildren, true)
    else -> error("Call this on ref field")
  }
}

private fun ObjProperty<*, *>.constructCode(type: ValueType<*>): RefMethods {
  type as ValueType.ObjRef<*>

  return if (type.child) {
    RefMethods(Instrumentation.getOneChild, "getOneChildBuilder", Instrumentation.replaceChildren, true)
  }
  else {
    val valueType = referencedField.valueType.let { if (it is ValueType.Optional<*>) it.type else it }
    if (valueType !is ValueType.List<*> && valueType !is ValueType.ObjRef<*>) {
      error("Unsupported reference type")
    }
    RefMethods(Instrumentation.getParent, "getParentBuilder", Instrumentation.addChild)
  }
}

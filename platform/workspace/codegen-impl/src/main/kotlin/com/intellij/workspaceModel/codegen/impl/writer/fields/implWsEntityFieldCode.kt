package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.impl.engine.ProblemReporter
import com.intellij.workspaceModel.codegen.impl.writer.Instrumentation
import com.intellij.workspaceModel.codegen.impl.writer.checkReference
import com.intellij.workspaceModel.codegen.impl.writer.extensions.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.hasSetter
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isOverride
import com.intellij.workspaceModel.codegen.impl.writer.extensions.isRefType
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.kotlinClassName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.refsFields
import com.intellij.workspaceModel.codegen.impl.writer.toQualifiedName

val ObjProperty<*, *>.implWsEntityFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) {
        append(implWsBlockingCodeOverride)
      }
      else append(implWsBlockingCode)
    } else {
      append("override var $javaName: ${valueType.javaType} = dataSource.$javaName\n")
    }
  }

private val ObjProperty<*, *>.implWsBlockingCode: String
  get() = implWsBlockCode(valueType, name, "")

internal fun ObjProperty<*, *>.implWsBlockCode(fieldType: ValueType<*>, name: String, optionalSuffix: String = ""): String {
  return when (fieldType) {
    ValueType.Int, ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
    ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> """
      override val $name: ${fieldType.javaType}
      get() {
      readField("$name")
      return dataSource.$name
      }
      """.trimIndent()
    ValueType.String -> """            
      override val $name: ${fieldType.javaType}${optionalSuffix}
      get() {
      readField("$name")
      return dataSource.$name
      }
      """.trimIndent()
    is ValueType.ObjRef -> {
      val notNullAssertion = if (optionalSuffix.isBlank()) " ?: error(\"Parent ${this.name} not found for ${this.receiver.name}\")" else ""
      """
        override val $name: ${fieldType.javaType}$optionalSuffix
        get() = snapshot.${refsConnectionMethodCode()} as? ${fieldType.javaType}$notNullAssertion           
        """.trimIndent()
    }
    is ValueType.List<*> -> {
      if (fieldType.isRefType()) {
        val connectionName = name.uppercase() + "_CONNECTION_ID"
        val notNullAssertion = if (optionalSuffix.isBlank()) " ?: error(\"Children ${this.name} not found for ${this.receiver.name}\")" else ""
          """
            override val $name: ${fieldType.javaType}$optionalSuffix
            get() = (snapshot.${Instrumentation.getManyChildren}($connectionName, this) as? Sequence<${fieldType.elementType.javaType}>)?.toList()$notNullAssertion
            """.trimIndent()
      }
      else { 
        """
          override val $name: ${fieldType.javaType}$optionalSuffix
          get() {
          readField("$name")
          return dataSource.$name
          }
          """.trimIndent()
      }
    }
    is ValueType.Set<*> -> {
      if (fieldType.isRefType()) {
        error("Set of references is not supported")
      }
      else { 
        """
          override val $javaName: ${fieldType.javaType}$optionalSuffix
          get() {
          readField("$name")
          return dataSource.$name
          }
          """.trimIndent()
      }
    }
    is ValueType.Map<*, *> -> """
      override val $name: ${fieldType.javaType}$optionalSuffix
      get() {
      readField("$name")
      return dataSource.$name
      }
      """
    is ValueType.Optional<*> -> when (fieldType.type) {
      ValueType.Int, ValueType.Boolean, ValueType.Char, ValueType.Long, ValueType.Float, ValueType.Double,
      ValueType.Short, ValueType.Byte, ValueType.UByte, ValueType.UShort, ValueType.UInt, ValueType.ULong -> """
        override val $name: ${fieldType.javaType}
        get() {
        readField("$name")
        return dataSource.$name
        }
        """.trimIndent()
      else -> implWsBlockCode(fieldType.type, name, "?")
    }
    is ValueType.JvmClass -> """            
      override val $name: ${fieldType.kotlinClassName.toQualifiedName()}$optionalSuffix
      get() {
      readField("$name")
      return dataSource.$name
      }
      """.trimIndent()
    else -> error("Unsupported field type: $this")
  }
}

internal val ObjProperty<*, *>.implWsBlockingCodeOverride: String
  get() {
    val originalField = receiver.refsFields.first { it.valueType.javaType == valueType.javaType }
    val connectionName = originalField.name.uppercase() + "_CONNECTION_ID"
    var valueType = referencedField.valueType
    if (valueType is ValueType.Optional<*>) {
      valueType = valueType.type
    }
    val getterName = when (valueType) {
      is ValueType.List<*> -> if (receiver.openness.extendable)
        Instrumentation.getParent
      else
        Instrumentation.getParent
      is ValueType.ObjRef<*> -> if (receiver.openness.extendable)
        Instrumentation.getParent
      else
        Instrumentation.getParent
      else -> error("Unsupported reference type")
    }
    return """
      override val $name: ${this.valueType.javaType}
      get() = snapshot.$getterName($connectionName, this) as? ${this.valueType.javaType} ?: error("Parent ${this.name} not found for ${this.receiver.name}")
      """.trimIndent()
  }

internal val ObjProperty<*, *>.referencedField: ObjProperty<*, *>
  get() {
    checkReference(this, object : ProblemReporter {
      override val problems: List<GenerationProblem>
        get() = emptyList()

      override fun reportProblem(problem: GenerationProblem) {
        error("ERROR MISSED BY PROBLEM CHECKER: ${problem.message}")
      }
    })
    val ref = valueType.getRefType()
    val declaredReferenceFromChild =
      ref.target.refsFields.filter { it.valueType.getRefType().target == receiver && it != this } +
      setOf(ref.target.module,
            receiver.module).flatMap { it.extensions }.filter { it.valueType.getRefType().target == receiver && it.receiver == ref.target && it != this }
    val referencedField = declaredReferenceFromChild[0]
    return referencedField
  }
